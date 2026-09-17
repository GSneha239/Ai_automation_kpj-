package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Quotation — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Quotation</b>.
 *
 * <p>One screen, two ways to add an item to the same quotation, both driven here: <b>New Item</b> (type
 * the item's details by hand) and <b>Get Items</b> (search and pick an existing item). Each does its own
 * <b>Add Terms and Condition</b> sequence for whichever item it just added; both share one Save at the
 * end, since it is one quotation record either way.</p>
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Quotation</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select the <b>Supplier</b>.</li>
 *   <li>Enter the <b>Item Name</b>.</li>
 *   <li><b>New Item tab</b>: click <b>New Item</b> → in <b>Quotation Details</b> enter the <b>Item
 *       Code</b>, <b>Item Name</b>, <b>Quantity</b>, <b>Cost Price</b>, <b>Excise</b> and <b>Tax %</b> →
 *       click <b>Add Terms and Condition</b> (select <b>Payment Terms</b>, select <b>Terms and
 *       Condition</b>, click <b>Add</b>, click <b>OK</b>).</li>
 *   <li><b>Get Items tab</b>: click <b>Get Items</b> → click <b>Search</b> → tick the checkbox beside
 *       the item code → click <b>OK</b> → in <b>Quotation Details</b> enter the same six fields → click
 *       <b>Add Terms and Condition</b> (same dialog sequence as above).</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see {@link com.kpj.pages.Inventory_page.Purchase_page.Quotation}. It is a sibling
 * of {@link com.kpj.pages.Inventory_page.Purchase_page.ItemEnquiry} in the same Purchase module, so
 * proven fixes from that screen (atomic-JS checkbox ticks, ordinal-positioned Terms and Condition
 * selects, the same shared "Item Search" picker dialog and its blank-search-returns-nothing fallback)
 * are applied from the start. {@code describeControls()} is dumped into the report at each stage so
 * anything still fuzzy can be pinned exactly once this has run.</p>
 *
 * <p>Pin values with {@code -DitemName=}, {@code -DitemCode=}, {@code -Dqty=}, {@code -DcostPrice=},
 * {@code -Dexcise=}, {@code -DtaxPercent=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL quotation record in the target environment.</p>
 */
public class Quotation extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Quotation() { super("Inventory_Purchase_Quotation"); }

    public static void main(String[] args) {
        Quotation t = new Quotation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Quotation", "Inventory > Purchase > Quotation",
                "&#9888; Creates a REAL quotation: New, select supplier, enter item name, then adds an "
                        + "item via the New Item tab AND via the Get Items tab (each with its own "
                        + "Quotation Details and Add Terms and Condition), then one shared Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String itemName = System.getProperty("itemName", "");
        String itemCode = System.getProperty("itemCode", "");
        String qty = System.getProperty("qty", "10");
        String costPrice = System.getProperty("costPrice", "5.00");
        String excise = System.getProperty("excise", "0");
        String taxPercent = System.getProperty("taxPercent", "6");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.Quotation qt =
                new com.kpj.pages.Inventory_page.Purchase_page.Quotation(page);

        // 1) Navigate
        boolean rendered = qt.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", qt.lastMenu);
        step(page, "Open Quotation screen",
                "Click Inventory -> Purchase -> Quotation",
                "The Quotation screen is shown",
                rendered ? "Opened " + page.url()
                           + (qt.lastRoute.isEmpty() ? "" : " (menu route " + qt.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = qt.clickNew();
        boolean newOk = qt.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Form controls", qt.describeControls());

        // 3) Supplier
        String supplier = qt.selectSupplier();
        boolean supplierOk = qt.supplierSelected();
        step(page, "Select the supplier", "Select the Supplier",
                "A supplier is selected",
                supplierOk ? "PASSES: " + supplier : "FAILS: " + supplier,
                supplierOk ? "PASS" : "FAIL");

        // 4) Item Name
        boolean touchField = !itemName.isEmpty();
        String itemNameResult = touchField ? qt.enterItemName(itemName)
                : "(not touched — left as the screen opened it, no item name was pinned)";
        boolean itemNameOk = !touchField || qt.itemNameEntered();
        step(page, "Enter Item Name",
                "Enter the Item Name" + (itemName.isEmpty() ? " (blank — not pinned)" : " " + itemName),
                "The Item Name is entered",
                itemNameOk ? "PASSES: " + itemNameResult : "FAILS: " + itemNameResult,
                itemNameOk ? "PASS" : "FAIL");

        // ================= Tab: New Item (manually typed) =================

        // 5) "Add a new tab - New Item" — confirmed live: there is no separate tab, only one "New Item"
        // button (OpenNewItem()) that both adds the new item line and opens Quotation Details for it.
        // Reported here rather than dropped silently, since it was one of the requested steps.
        String tab = qt.clickNewItemTab();
        addSummary("\"Add a new tab - New Item\"", tab
                + " — this screen has no separate tab; \"New Item\" is a single button, covered next.");

        // 6) New Item button
        String newItemBtn = qt.clickNewItemButton();
        boolean newItemBtnOk = qt.newItemButtonHandled();
        step(page, "[New Item] Click New Item", "Click New Item",
                "Quotation Details opens",
                newItemBtnOk ? "PASSES: " + newItemBtn : "FAILS: " + newItemBtn,
                newItemBtnOk ? "PASS" : "FAIL");

        boolean detailsOpen = qt.quotationDetailsOpen();
        step(page, "[New Item] Verify Quotation Details is showing",
                "Read the form for the Quotation Details fields",
                "Quotation Details is open",
                detailsOpen ? "PASSES — the Quotation Details fields are visible"
                            : "FAILS — none of the expected Quotation Details fields were found",
                detailsOpen ? "PASS" : "FAIL");

        if (detailsOpen) {
            addSummary("[New Item] Quotation Details controls", qt.describeControls());

            // 7) Quotation Details fields
            String details = qt.enterQuotationDetails(itemCode, itemName, qty, costPrice, excise, taxPercent);
            boolean detailsOk = qt.quotationDetailsEntered();
            step(page, "[New Item] Enter Item Code, Item Name, Quantity, Cost Price, Excise and Tax %",
                    "In Quotation Details, enter the Item Code" + (itemCode.isEmpty() ? " (blank)" : " " + itemCode)
                            + ", Item Name" + (itemName.isEmpty() ? " (blank)" : " " + itemName)
                            + ", Quantity " + qty + ", Cost Price " + costPrice + ", Excise " + excise
                            + " and Tax % " + taxPercent,
                    "All six are entered",
                    detailsOk ? "PASSES: " + details : "FAILS — a field was not found: " + details,
                    detailsOk ? "PASS" : "FAIL");

            // 8) Add Terms and Condition
            runTermsAndConditionDialog(qt, "[New Item]");
        } else {
            addSummary("Result (New Item)", "FAILED — Quotation Details never opened");
        }

        // ================= Tab: Get Items (search and pick an existing item) =================
        // Not one of the New Item tab's steps — a second, independent way to add an item to this same
        // quotation, using the same shared "Item Search" picker dialog confirmed live on Item Enquiry
        // (same Purchase module).

        String getItems = qt.clickGetItems();
        boolean pickerOpen = qt.itemPickerOpen();
        step(page, "[Get Items] Click Get Items", "Click Get Items",
                "The item-picker dialog opens",
                pickerOpen ? "PASSES: " + getItems : "FAILS: " + getItems,
                pickerOpen ? "PASS" : "FAIL");

        if (pickerOpen) {
            addSummary("[Get Items] Item picker controls", qt.describeControls());

            String pickerSearch = qt.clickSearchInPicker();
            int pickerRows = qt.pickerRowCount();
            // Mirrors Item Enquiry's identical picker: a blank search returns nothing on this shared
            // dialog, so a single letter is tried as a broad "contains" search.
            if (pickerRows == 0) {
                page.evaluate("() => { const e=document.querySelector(\"[ng-model='ItemSearch.ItemName']\");"
                        + " if(e){ e.focus(); e.value='a';"
                        + "   try{ const c=angular.element(e).controller('ngModel');"
                        + "        if(c){ c.$setViewValue('a'); c.$render(); } }catch(err){}"
                        + "   e.dispatchEvent(new Event('input',{bubbles:true}));"
                        + "   e.dispatchEvent(new Event('change',{bubbles:true})); } }");
                pickerSearch = qt.clickSearchInPicker();
                pickerRows = qt.pickerRowCount();
            }
            step(page, "[Get Items] Click Search", "Click Search",
                    "The item picker lists results",
                    (pickerRows > 0 ? "PASSES because the search returned rows: " + pickerSearch
                              : "FAILS because the search returned nothing: " + pickerSearch),
                    pickerRows > 0 ? "PASS" : "FAIL");

            if (pickerRows > 0) {
                String pickerTick = qt.tickFirstItemInPicker();
                boolean pickerTicked = qt.pickerItemTicked();
                step(page, "[Get Items] Tick the checkbox to choose an item",
                        "Tick the checkbox beside the item code, for the first item",
                        "The item is selected",
                        pickerTicked ? "PASSES: " + pickerTick : "FAILS: " + pickerTick,
                        pickerTicked ? "PASS" : "FAIL");

                if (pickerTicked) {
                    String pickerOk = qt.clickOkInPicker();
                    boolean pickerOkClicked = qt.pickerOkClicked();
                    step(page, "[Get Items] Click OK", "Click OK",
                            "The item is added to Quotation Details",
                            pickerOkClicked ? "PASSES: " + pickerOk : "FAILS: " + pickerOk,
                            pickerOkClicked ? "PASS" : "FAIL");

                    if (pickerOkClicked) {
                        String details2 = qt.enterQuotationDetails(itemCode, itemName, qty, costPrice, excise, taxPercent);
                        boolean details2Ok = qt.quotationDetailsEntered();
                        step(page, "[Get Items] Enter Item Code, Item Name, Quantity, Cost Price, Excise and Tax %",
                                "In Quotation Details, enter the Item Code"
                                        + (itemCode.isEmpty() ? " (blank)" : " " + itemCode)
                                        + ", Item Name" + (itemName.isEmpty() ? " (blank)" : " " + itemName)
                                        + ", Quantity " + qty + ", Cost Price " + costPrice + ", Excise "
                                        + excise + " and Tax % " + taxPercent,
                                "All six are entered",
                                details2Ok ? "PASSES: " + details2 : "FAILS — a field was not found: " + details2,
                                details2Ok ? "PASS" : "FAIL");

                        runTermsAndConditionDialog(qt, "[Get Items]");
                    } else {
                        addSummary("Result (Get Items)", "FAILED — OK did not confirm the selection");
                    }
                } else {
                    addSummary("Result (Get Items)", "FAILED — no item was selected");
                }
            } else {
                addSummary("Result (Get Items)", "FAILED — no item to select");
            }
        } else {
            addSummary("Result (Get Items)", "FAILED — the item picker never opened");
        }

        // 9) Save -> toast
        addSummary("Form controls right before Save (diagnostic)", qt.describeControls());
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "Quotation-beforeSave-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        String toast = qt.saveAndGetToast();
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "Quotation-afterSave-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        boolean success = com.kpj.pages.Inventory_page.Purchase_page.Quotation.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + qt.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Click Save & verify success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Supplier", qt.lastSupplier);
        addSummary("Item Name", qt.lastItemName);
        addSummary("Quotation Details", qt.lastQuotationDetails);
        addSummary("Payment Terms", qt.lastPaymentTerms);
        addSummary("Terms and Condition", qt.lastTermsCondition);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }

    /**
     * Click <b>Add Terms and Condition</b>, select <b>Payment Terms</b> and <b>Terms and Condition</b>,
     * click <b>Add</b>, click <b>OK</b> — shared by both the New Item tab and the Get Items tab, since
     * each does this same sequence for whichever item it just added.
     */
    private void runTermsAndConditionDialog(com.kpj.pages.Inventory_page.Purchase_page.Quotation qt, String tag) {
        String addTerms = qt.clickAddTermsAndCondition();
        boolean termsDialogOpen = qt.termsDialogOpen();
        step(page, tag + " Click Add Terms and Condition", "Click Add Terms and Condition",
                "The Terms and Condition dialog opens",
                termsDialogOpen ? "PASSES: " + addTerms : "FAILS: " + addTerms,
                termsDialogOpen ? "PASS" : "FAIL");
        if (!termsDialogOpen) {
            addSummary("Terms and Condition " + tag, "SKIPPED — the dialog never opened");
            return;
        }

        addSummary(tag + " Terms and Condition dialog controls", qt.describeControls());

        String payTerms = qt.selectPaymentTerms();
        boolean payTermsOk = qt.paymentTermsSelected();
        step(page, tag + " Select Payment Terms", "Select the Payment Terms",
                "A payment term is selected",
                payTermsOk ? "PASSES: " + payTerms : "FAILS: " + payTerms,
                payTermsOk ? "PASS" : "FAIL");

        String termsCond = qt.selectTermsCondition();
        boolean termsCondOk = qt.termsConditionSelected();
        step(page, tag + " Select Terms and Condition", "Select the Terms and Condition",
                "A terms and condition entry is selected",
                termsCondOk ? "PASSES: " + termsCond : "FAILS: " + termsCond,
                termsCondOk ? "PASS" : "FAIL");

        String termsAdd = qt.clickAddInTermsDialog();
        boolean termsAdded = qt.termsAdded();
        step(page, tag + " Click Add (in the dialog)", "Click Add",
                "The terms are added to the dialog's list",
                termsAdded ? "PASSES: " + termsAdd : "FAILS: " + termsAdd,
                termsAdded ? "PASS" : "FAIL");

        String termsOk = qt.clickOkTermsDialog();
        boolean termsOkClicked = qt.termsOkClicked();
        step(page, tag + " Click OK (Terms and Condition dialog)", "Click OK",
                "The Terms and Condition dialog closes",
                termsOkClicked ? "PASSES: " + termsOk : "FAILS: " + termsOk,
                termsOkClicked ? "PASS" : "FAIL");
    }
}
