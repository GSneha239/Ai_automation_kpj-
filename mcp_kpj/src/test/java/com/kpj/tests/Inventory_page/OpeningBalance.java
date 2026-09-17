package com.kpj.tests.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OpeningBalance — referenced by its fully-qualified name.

/**
 * Inventory &gt; <b>Opening Balance</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Opening Balance</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Click <b>Get Items</b>.</li>
 *   <li>Enter the <b>Item Code</b> and <b>Item Name</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Tick the checkbox to select an item.</li>
 *   <li>Click <b>OK</b>.</li>
 *   <li>Enter the <b>Barcode</b>, <b>Batch Code</b>, <b>Expiry Date</b>, <b>Loose Qty</b>, <b>Pack
 *       Cost</b> and <b>Pack MRP</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>This screen has not been inspected live, so every control is found by FUZZY matching rather than a
 * pinned exact model — see {@link com.kpj.pages.Inventory_page.OpeningBalance}.
 * {@code describeControls()} / {@code describeDialogControls()} are dumped into the report so the real
 * ng-models can replace the fuzzy matches once this has run against the live screen.</p>
 *
 * <p>Pin values with {@code -DitemCode=}, {@code -DitemName=}, {@code -Dbarcode=}, {@code -DbatchCode=},
 * {@code -DexpiryDate=}, {@code -DlooseQty=}, {@code -DpackCost=}, {@code -DpackMrp=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL opening balance record in the target environment.</p>
 */
public class OpeningBalance extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public OpeningBalance() { super("Inventory_OpeningBalance"); }

    public static void main(String[] args) {
        OpeningBalance t = new OpeningBalance();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Opening Balance", "Inventory > Opening Balance",
                "&#9888; Creates a REAL opening balance: New, Get Items, search and select an item, "
                        + "enter its barcode/batch/expiry/qty/cost/MRP, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String itemCode = System.getProperty("itemCode", "");
        String itemName = System.getProperty("itemName", "");
        String barcode = System.getProperty("barcode", "BC" + stamp);
        String batchCode = System.getProperty("batchCode", "BATCH" + stamp);
        String expiryDate = System.getProperty("expiryDate",
                java.time.LocalDate.now().plusYears(2).format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String looseQty = System.getProperty("looseQty", "10");
        String packCost = System.getProperty("packCost", "5.00");
        String packMrp = System.getProperty("packMrp", "8.00");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.OpeningBalance ob =
                new com.kpj.pages.Inventory_page.OpeningBalance(page);

        // 1) Navigate
        boolean rendered = ob.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", ob.lastMenu);
        step(page, "Open Opening Balance screen",
                "Click Inventory -> Opening Balance",
                "The Opening Balance screen is shown",
                rendered ? "Opened " + page.url()
                           + (ob.lastRoute.isEmpty() ? "" : " (menu route " + ob.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        String newResult = ob.clickNew();
        boolean newOk = ob.newClicked();
        step(page, "Click New", "Click New",
                "The entry form opens",
                newOk ? "PASSES: " + newResult : "FAILS: " + newResult,
                newOk ? "PASS" : "FAIL");
        if (!newOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        // 3) Get Items
        String getItems = ob.clickGetItems();
        boolean pickerOpen = ob.itemPickerOpen();
        step(page, "Click Get Items", "Click Get Items",
                "The item-picker dialog opens",
                pickerOpen ? "PASSES: " + getItems : "FAILS: " + getItems,
                pickerOpen ? "PASS" : "FAIL");
        if (!pickerOpen) { addSummary("Result", "FAILED — the item picker never opened"); return; }

        addSummary("Item picker controls", ob.describeDialogControls());

        // 4) Item Code + Item Name — SKIPPED when neither is pinned. Setting a field to "" still fires
        // Angular's change handlers (setEl dispatches input/change/blur), which may reset whatever
        // default state the field held; leaving both fields completely untouched is a genuinely
        // different action from "typed blank into them", so it is tried on its own before falling back
        // further.
        boolean touchFields = !itemCode.isEmpty() || !itemName.isEmpty();
        String itemSearch = touchFields
                ? ob.enterItemCodeAndName(itemCode, itemName)
                : "(not touched — trying Search with both fields left as the screen opened them)";
        boolean itemFieldsOk = !touchFields || ob.itemCodeAndNameEntered();
        step(page, "Enter Item Code and Item Name",
                "Enter the Item Code" + (itemCode.isEmpty() ? " (blank — search all)" : " " + itemCode)
                        + " and the Item Name" + (itemName.isEmpty() ? " (blank — search all)" : " " + itemName),
                "Both are entered",
                itemFieldsOk ? "PASSES: " + itemSearch : "FAILS: " + itemSearch,
                itemFieldsOk ? "PASS" : "FAIL");

        // 5) Search
        String search = ob.clickSearch();
        int rows = ob.rowCount();
        String fallbackNote = "";
        // Neither leaving the fields untouched nor typing "" into them returns anything on this screen —
        // unlike the "blank searches all" convention elsewhere in this module. When neither field was
        // pinned, retry with a single-letter Item Name as a broad "contains" search rather than
        // reporting a working screen as broken.
        if (rows == 0 && itemCode.isEmpty() && itemName.isEmpty()) {
            String broadName = "a";
            ob.enterItemCodeAndName("", broadName);
            search = ob.clickSearch();
            rows = ob.rowCount();
            fallbackNote = "  ||  neither an untouched search nor a blank one returned anything, so the "
                    + "flow retried with Item Name \"" + broadName + "\" as a broad search. That is not "
                    + "one of the requested inputs, but this screen does not search on a blank Item "
                    + "Code/Item Name.";
        }
        step(page, "Click Search", "Click Search",
                "The item picker lists results",
                (rows > 0 ? "PASSES because the search returned rows: " + search + fallbackNote
                          : "FAILS because the search returned nothing: " + search + fallbackNote),
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "FAILED — no item to select"); return; }

        addSummary("Items found", ob.describeRows());

        // 6) Tick the checkbox
        String tick = ob.tickFirstItem();
        boolean ticked = ob.itemTicked();
        step(page, "Tick the checkbox to select an item",
                "Tick the checkbox on the first item",
                "The item is selected",
                ticked ? "PASSES: " + tick : "FAILS: " + tick,
                ticked ? "PASS" : "FAIL");
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "OpeningBalance-afterTick-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        if (!ticked) { addSummary("Result", "FAILED — no item was selected"); return; }

        // 7) OK
        String ok = ob.clickOk();
        boolean okOk = ob.okClicked();
        step(page, "Click OK", "Click OK",
                "The item is added to the form",
                okOk ? "PASSES: " + ok : "FAILS: " + ok,
                okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK did not confirm the selection"); return; }

        // 8) Item details
        addSummary("Main form controls AFTER OK (diagnostic)", ob.describeControls());
        try {
            page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions()
                    .setPath(java.nio.file.Paths.get("test-output", "OpeningBalance-afterOK-diagnostic.png"))
                    .setFullPage(true));
        } catch (Exception e) { System.out.println("diagnostic screenshot failed: " + e.getMessage()); }
        String details = ob.enterItemDetails(barcode, batchCode, expiryDate, looseQty, packCost, packMrp);
        boolean detailsOk = ob.detailsEntered();
        step(page, "Enter barcode, batch code, expiry date, loose qty, pack cost and pack MRP",
                "Enter the Barcode " + barcode + ", Batch Code " + batchCode + ", Expiry Date "
                        + expiryDate + ", Loose Qty " + looseQty + ", Pack Cost " + packCost
                        + " and Pack MRP " + packMrp,
                "All six are entered",
                detailsOk ? "PASSES: " + details : "FAILS — a field was not found: " + details,
                detailsOk ? "PASS" : "FAIL");

        // 9) Save -> toast
        String toast = ob.saveAndGetToast();
        boolean success = com.kpj.pages.Inventory_page.OpeningBalance.isSuccess(toast);
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ob.lastSaveDiagnostics
                : (success ? toast : "Save not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Click Save & verify success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, success ? "PASS" : "FAIL");

        addSummary("Item Code / Name", itemCode + " / " + itemName);
        addSummary("Selected item", ob.lastTick);
        addSummary("Item details", ob.lastDetails);
        addSummary("Result", success ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
