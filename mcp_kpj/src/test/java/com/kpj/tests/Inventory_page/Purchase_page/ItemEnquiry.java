package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ItemEnquiry — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Item Enquiry</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Item Enquiry</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Click <b>Get Items</b>.</li>
 *   <li>Enter the <b>Item Name</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Tick the checkbox beside the item code to choose an item.</li>
 *   <li>Click <b>OK</b>.</li>
 *   <li>Select the <b>Supplier</b> and tick its checkbox.</li>
 *   <li>Click <b>Add Terms and Condition</b>.</li>
 *   <li>In the Terms and Condition dialog: select <b>Payment Terms</b>, select <b>Terms and
 *       Condition</b>, click <b>Add</b>, click <b>OK</b>.</li>
 *   <li>In the listed item, enter the <b>Quantity</b> and <b>Remarks</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see
 * {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry}. Its item-picker dialog is the same
 * shared component as Opening Balance (same Inventory module), so lessons from that screen are applied
 * from the start: checkbox ticks use Playwright's own {@code Locator.check()} with a 3-attempt retry,
 * since raw JS reported success there while the screen's own validation still refused it.
 * {@code describeControls()} is dumped into the report at each stage so the real ng-models can replace
 * the fuzzy matches once this has run.</p>
 *
 * <p>Pin values with {@code -DitemName=}, {@code -Dqty=}, {@code -Dremarks=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL item enquiry record in the target environment.</p>
 */
public class ItemEnquiry extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ItemEnquiry() { super("Inventory_Purchase_ItemEnquiry"); }

    public static void main(String[] args) {
        ItemEnquiry t = new ItemEnquiry();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Item Enquiry", "Inventory > Purchase > Item Enquiry",
                "&#9888; Creates a REAL item enquiry: New, Get Items, search and select an item, "
                        + "select supplier, add terms and condition, enter quantity/remarks, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String itemName = System.getProperty("itemName", "");
        String qty = System.getProperty("qty", "10");
        String remarks = System.getProperty("remarks", "Automated item enquiry " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry ie =
                new com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry(page);

        // 1) Navigate
        boolean rendered = ie.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", ie.lastMenu);
        step(page, "Open Item Enquiry screen",
                "Click Inventory -> Purchase -> Item Enquiry",
                "The Item Enquiry screen is shown",
                rendered ? "Opened " + page.url()
                           + (ie.lastRoute.isEmpty() ? "" : " (menu route " + ie.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = ie.clickNew();
        boolean newOk = ie.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        // 3) Get Items
        String getItems = ie.clickGetItems();
        boolean pickerOpen = ie.itemPickerOpen();
        step(page, "Click Get Items", "Click Get Items",
                "The item-picker dialog opens",
                pickerOpen ? "PASSES: " + getItems : "FAILS: " + getItems,
                pickerOpen ? "PASS" : "FAIL");
        if (!pickerOpen) { addSummary("Result", "FAILED — the item picker never opened"); return; }

        addSummary("Item picker controls", ie.describeControls());

        // 4) Item Name
        boolean touchField = !itemName.isEmpty();
        String itemSearch = touchField ? ie.enterItemName(itemName)
                : "(not touched — trying Search with the field left as the screen opened it)";
        boolean itemFieldOk = !touchField || ie.itemNameEntered();
        step(page, "Enter Item Name",
                "Enter the Item Name" + (itemName.isEmpty() ? " (blank — broad search)" : " " + itemName),
                "The Item Name is entered",
                itemFieldOk ? "PASSES: " + itemSearch : "FAILS: " + itemSearch,
                itemFieldOk ? "PASS" : "FAIL");

        // 5) Search
        String search = ie.clickSearch();
        int rows = ie.rowCount();
        String fallbackNote = "";
        // Mirrors Opening Balance: this shared item-picker component does not search on a blank Item
        // Name, so a single letter is tried as a broad "contains" search when nothing was pinned.
        if (rows == 0 && itemName.isEmpty()) {
            String broadName = "a";
            ie.enterItemName(broadName);
            search = ie.clickSearch();
            rows = ie.rowCount();
            fallbackNote = "  ||  a blank/untouched search returned nothing, so the flow retried with "
                    + "Item Name \"" + broadName + "\" as a broad search. That is not one of the "
                    + "requested inputs, but this item picker does not search on a blank Item Name.";
        }
        step(page, "Click Search", "Click Search",
                "The item picker lists results",
                (rows > 0 ? "PASSES because the search returned rows: " + search + fallbackNote
                          : "FAILS because the search returned nothing: " + search + fallbackNote),
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED — no item to select"); return; }

        addSummary("Items found", ie.describeRows());

        // 6) Tick the checkbox beside the item code
        String tick = ie.tickFirstItem();
        boolean ticked = ie.itemTicked();
        step(page, "Tick the checkbox to choose an item",
                "Tick the checkbox beside the item code, for the first item",
                "The item is selected",
                ticked ? "PASSES: " + tick : "FAILS: " + tick,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — no item was selected"); return; }

        // 7) OK
        String ok = ie.clickOk();
        boolean okOk = ie.okClicked();
        step(page, "Click OK", "Click OK",
                "The item is added to the form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        addSummary("Main form controls after OK", ie.describeControls());

        // 8) Supplier
        String supplier = ie.selectSupplier();
        boolean supplierOk = ie.supplierSelected();
        step(page, "Select the supplier", "Select the Supplier",
                "A supplier is selected",
                supplierOk ? "PASSES: " + supplier : "FAILS: " + supplier,
                supplierOk ? "PASS" : "FAIL");

        String supplierTick = ie.tickSupplierCheckbox();
        boolean supplierTicked = ie.supplierTicked();
        step(page, "Tick the checkbox for the supplier name",
                "Tick the checkbox beside the supplier name",
                "The supplier is ticked",
                supplierTicked ? "PASSES: " + supplierTick : "FAILS: " + supplierTick,
                supplierTicked ? "PASS" : "FAIL");

        // 9) Add Terms and Condition
        String addTerms = ie.clickAddTermsAndCondition();
        boolean termsDialogOpen = ie.termsDialogOpen();
        step(page, "Click Add Terms and Condition", "Click Add Terms and Condition",
                "The Terms and Condition dialog opens",
                termsDialogOpen ? "PASSES: " + addTerms : "FAILS: " + addTerms,
                termsDialogOpen ? "PASS" : "FAIL");

        if (termsDialogOpen) {
            addSummary("Terms and Condition dialog controls", ie.describeControls());

            String payTerms = ie.selectPaymentTerms();
            boolean payTermsOk = ie.paymentTermsSelected();
            step(page, "Select Payment Terms", "Select the Payment Terms",
                    "A payment term is selected",
                    payTermsOk ? "PASSES: " + payTerms : "FAILS: " + payTerms,
                    payTermsOk ? "PASS" : "FAIL");

            String termsCond = ie.selectTermsCondition();
            boolean termsCondOk = ie.termsConditionSelected();
            step(page, "Select Terms and Condition", "Select the Terms and Condition",
                    "A terms and condition entry is selected",
                    termsCondOk ? "PASSES: " + termsCond : "FAILS: " + termsCond,
                    termsCondOk ? "PASS" : "FAIL");

            String termsAdd = ie.clickAddInTermsDialog();
            boolean termsAdded = ie.termsAdded();
            step(page, "Click Add (in the dialog)", "Click Add",
                    "The terms are added to the dialog's list",
                    termsAdded ? "PASSES: " + termsAdd : "FAILS: " + termsAdd,
                    termsAdded ? "PASS" : "FAIL");

            String termsOk = ie.clickOkTermsDialog();
            boolean termsOkClicked = ie.termsOkClicked();
            step(page, "Click OK (Terms and Condition dialog)", "Click OK",
                    "The Terms and Condition dialog closes",
                    termsOkClicked ? "PASSES: " + termsOk : "FAILS: " + termsOk,
                    termsOkClicked ? "PASS" : "FAIL");
        } else {
            addSummary("Terms and Condition", "SKIPPED — the dialog never opened");
        }

        // 10) Quantity + Remarks
        String qtyRemarks = ie.enterQuantityAndRemarks(qty, remarks);
        boolean qtyRemarksOk = ie.quantityAndRemarksEntered();
        step(page, "Enter quantity and remarks",
                "In the listed item, enter the Quantity " + qty + " and Remarks " + remarks,
                "Both are entered",
                qtyRemarksOk ? "PASSES: " + qtyRemarks : "FAILS — a field was not found: " + qtyRemarks,
                qtyRemarksOk ? "PASS" : "FAIL");

        // 11) Save -> toast
        String toast = ie.saveAndGetToast();
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ie.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Click Save & verify success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Item Name", itemName);
        addSummary("Selected item", ie.lastTick);
        addSummary("Supplier", ie.lastSupplier);
        addSummary("Payment Terms", ie.lastPaymentTerms);
        addSummary("Terms and Condition", ie.lastTermsCondition);
        addSummary("Quantity / Remarks", ie.lastQtyRemarks);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
