package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

// The page object is also named StoreIndent — referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Store Indent</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Inventory</b> → <b>Transfer</b> → <b>Store Indent</b>.</li>
 *   <li>"Add a new tab - Manual Indent" (reported plainly, not assumed to be a real tab); click
 *       <b>Manual Indent</b>; select <b>Issuing Store</b>; click <b>Get Items</b> (opens the "Item
 *       Search" dialog); click <b>Search</b>; tick a checkbox to select an item; click <b>OK</b>; enter
 *       the <b>Unit Quantity</b>; enter <b>Remarks</b>; click <b>Save</b>; verify report generation;
 *       verify the success toast; in the <b>Indent Number</b> confirmation popup, click <b>OK</b>.</li>
 * </ol>
 *
 * <p>Sibling of {@link com.kpj.pages.Inventory_page.Transfer_page.Transfer} in the
 * {@code Inventory_page.Transfer_page} submodule, confirmed at route {@code #/StoreIndentList}. Every
 * control was walked by hand in a live browser before writing any selector, which is how a genuine
 * reversed-field trap was caught before it became a bug: {@code StoreIndent.SearchStoreID} is actually
 * labelled "Requesting Store" and {@code StoreIndent.ToStore} is actually labelled "Issuing Store" — the
 * opposite of what their ng-model names suggest — and this screen's Save is gated by a jQuery
 * {@code validationEngine} check that a first pass using this module's usual raw-JS field-setting left
 * silently failing (no alert, no network call) even though every Angular-bound value looked correct on
 * inspection; genuine Playwright {@code fill()}/{@code click()} interactions are used for this screen's
 * fields for that reason. See the page object's class doc for the full detail. PDF generation is judged
 * with {@link PdfReport}, the same helper already proven across this module.
 * {@code describeControls()} is dumped into the report at each stage so anything still fuzzy can be
 * pinned exactly once this has run against the live screen.</p>
 *
 * <p>&#9888; A successful run creates ONE REAL Store Indent in the target environment.</p>
 */
public class StoreIndent extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public StoreIndent() { super("Inventory_Transfer_StoreIndent"); }

    public static void main(String[] args) {
        StoreIndent t = new StoreIndent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Store Indent", "Inventory > Transfer > Store Indent",
                "&#9888; Creates ONE REAL Store Indent: click Manual Indent, select Issuing Store, "
                        + "Get Items, search and tick an item, OK, enter Unit Quantity and Remarks, "
                        + "click Save, verify report generation and the success toast, then click OK on "
                        + "the Indent Number confirmation popup.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String qty = System.getProperty("qty", "2");
        String remarks = System.getProperty("remarks", "Automated test remarks");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.StoreIndent si =
                new com.kpj.pages.Inventory_page.Transfer_page.StoreIndent(page);

        // 1) Navigate
        boolean rendered = si.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory menu offered", si.lastMenu);
        step(page, "Open Store Indent screen",
                "Click Inventory -> Transfer -> Store Indent",
                "The Store Indent screen is shown",
                rendered ? "Opened " + page.url()
                           + (si.lastRoute.isEmpty() ? "" : " (menu route " + si.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean contentLoaded = si.waitForRealContent(15000);
        addSummary("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addSummary("List screen controls", si.describeControls());

        // 2) "Add a new tab - Manual Indent" — reported plainly; not assumed to be a real tab.
        String manualIndentTab = si.clickManualIndentTab();
        addSummary("\"Add a new tab - Manual Indent\"", manualIndentTab);

        // 3) Click Manual Indent
        String manualIndentResult = si.clickManualIndent();
        boolean manualIndentOk = si.manualIndentClicked();
        step(page, "Click Manual Indent", "Click Manual Indent",
                "The entry form opens",
                manualIndentOk ? "PASSES: " + manualIndentResult : "FAILS: " + manualIndentResult,
                manualIndentOk ? "PASS" : "FAIL");
        if (!manualIndentOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addSummary("Entry form controls", si.describeControls());

        // 4) Select Issuing Store
        String issuingStoreResult = si.selectIssuingStore();
        boolean issuingStoreOk = si.issuingStoreSelected();
        step(page, "Select issuing store", "Select the Issuing Store",
                "An issuing store is selected",
                issuingStoreOk ? "PASSES: " + issuingStoreResult : "FAILS: " + issuingStoreResult,
                issuingStoreOk ? "PASS" : "FAIL");
        if (!issuingStoreOk) { addSummary("Result", "FAILED — no issuing store could be selected"); return; }

        // 5) Click Get Items
        String getItemsResult = si.clickGetItems();
        boolean dialogOpenOk = si.dialogOpen();
        step(page, "Click get items", "Click Get Items",
                "The Item Search dialog opens",
                dialogOpenOk ? "PASSES: " + getItemsResult : "FAILS: " + getItemsResult,
                dialogOpenOk ? "PASS" : "FAIL");
        if (!dialogOpenOk) { addSummary("Result", "FAILED — the Item Search dialog never opened"); return; }

        addSummary("Item Search dialog controls", si.describeControls());

        // 6) In dialog popup - Item Search, click the search button
        String dialogSearchResult = si.clickSearchInDialog();
        boolean dialogRowsOk = si.dialogItemRowsFound();
        step(page, "In dialog popup -Item Search, click the search button", "Click Search",
                "Item rows appear in the dialog",
                dialogRowsOk ? "PASSES: " + dialogSearchResult : "FAILS: " + dialogSearchResult,
                dialogRowsOk ? "PASS" : "FAIL");
        if (!dialogRowsOk) { addSummary("Result", "FAILED — no item to select"); return; }

        // 7) Click tick on checkbox to select item
        String itemTickResult = si.tickFirstItemInDialog();
        boolean itemTickOk = si.itemTicked();
        step(page, "Click tick on checkbox to select item", "Tick the checkbox for one item",
                "The item is selected",
                itemTickOk ? "PASSES: " + itemTickResult : "FAILS: " + itemTickResult,
                itemTickOk ? "PASS" : "FAIL");
        if (!itemTickOk) { addSummary("Result", "FAILED — no item was selected"); return; }

        // 8) Click OK
        String dialogOkResult = si.clickOkInDialog();
        boolean dialogOkOk = si.dialogOkClicked() && si.dialogClosed();
        step(page, "Click ok", "Click OK",
                "The dialog closes and the item appears on the main form",
                dialogOkOk ? "PASSES: " + dialogOkResult : "FAILS: " + dialogOkResult,
                dialogOkOk ? "PASS" : "FAIL");
        if (!dialogOkOk) { addSummary("Result", "FAILED — the Item Search dialog never closed"); return; }

        addSummary("Main form controls after selecting the item", si.describeControls());

        // 9) Enter unit quantity
        String unitQtyResult = si.enterUnitQuantity(qty);
        boolean unitQtyOk = si.unitQuantityEntered(qty);
        step(page, "Enter unit quantity", "Enter Unit Quantity " + qty,
                "The Unit Quantity is entered",
                unitQtyOk ? "PASSES: " + unitQtyResult : "FAILS: " + unitQtyResult,
                unitQtyOk ? "PASS" : "FAIL");

        // 10) Enter remarks
        String remarksResult = si.enterRemarks(remarks);
        boolean remarksOk = si.remarksEntered(remarks);
        step(page, "Enter remarks", "Enter Remarks " + remarks,
                "Remarks is entered",
                remarksOk ? "PASSES: " + remarksResult : "FAILS: " + remarksResult,
                remarksOk ? "PASS" : "FAIL");

        // 11) Click Save
        int tabsBeforeSave = si.clickSave();
        boolean saveClickedOk = si.saveClicked();
        step(page, "Click save", "Click Save",
                "The Save action fires",
                saveClickedOk ? "PASSES: " + si.lastSaveDiagnostics : "FAILS: " + si.lastSaveDiagnostics,
                saveClickedOk ? "PASS" : "FAIL");
        if (!saveClickedOk) { addSummary("Result", "FAILED — the Save button was not found"); return; }

        // 12) Verify success toast message — checked BEFORE report generation, since PdfReport.capture()
        // can itself take up to 15s watching for the report tab, and the toast (typically visible only
        // a few seconds) was confirmed live to have already faded by the time a toast check ran after it.
        String toast = si.waitForSaveToast();
        boolean success = com.kpj.pages.Inventory_page.Transfer_page.StoreIndent.isSuccess(toast);
        String actual = (toast == null || toast.isEmpty())
                ? "No message appeared — " + si.lastSaveDiagnostics
                : (success ? toast : "Not confirmed — the screen answered: \"" + toast + "\"");
        step(page, "Verify success toast message", "Wait for the success toast",
                "'... successfully' toast", actual, success ? "PASS" : "FAIL");

        // 13) Verify report generation
        PdfReport.Result pdf = PdfReport.capture(page, tabsBeforeSave, 15000);
        step(page, "Verify report generation", "Wait for the report to open",
                "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");

        // 14) In the indent number pop up, click ok
        String indentNumberResult = si.clickIndentNumberOk();
        boolean indentNumberOk = si.indentNumberOkClicked();
        step(page, "In the indent number pop up, click ok", "Click OK on the Indent Number popup",
                "The popup is dismissed",
                indentNumberOk ? "PASSES: " + indentNumberResult : "FAILS: " + indentNumberResult,
                indentNumberOk ? "PASS" : "FAIL");

        addSummary("Issuing store", si.lastIssuingStore);
        addSummary("Item selected", si.lastItemTick);
        addSummary("Unit quantity / Remarks", si.lastUnitQty + " / " + si.lastRemarks);
        addSummary("Report result", pdf.diagnostics);
        addSummary("Save toast result", success ? toast : "Not confirmed (\"" + toast + "\")");
        addSummary("Indent Number popup", indentNumberResult);

        // ==================== [Auto Indent] tab (same list view, a second tab) ====================
        // Confirmed live this is a genuinely separate route (#/AutoIndent), not a dialog over the
        // Manual Indent form — return to the list view first rather than assume the current screen
        // state carries over.

        String autoRemarks = System.getProperty("autoRemarks", "Automated auto indent remarks");

        boolean rendered2 = si.navigateViaMenu(BASE);
        step(page, "[Auto Indent] Return to the Store Indent list",
                "Navigate back to Inventory -> Transfer -> Store Indent",
                "The Store Indent list view is shown",
                rendered2 ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                rendered2 ? "PASS" : "FAIL");
        if (!rendered2) { addSummary("Auto Indent result", "FAILED — screen not reached"); return; }

        // 15) "Add new tab - Auto Indent" — reported plainly; not assumed to be a real tab.
        String autoIndentTab = si.clickAutoIndentTab();
        addSummary("\"Add new tab - Auto Indent\"", autoIndentTab);

        // 16) Click Auto Indent
        String autoIndentResult = si.clickAutoIndent();
        boolean autoIndentOk = si.autoIndentClicked();
        step(page, "[Auto Indent] Click auto indent", "Click Auto Indent",
                "The Auto Indent screen opens",
                autoIndentOk ? "PASSES: " + autoIndentResult : "FAILS: " + autoIndentResult,
                autoIndentOk ? "PASS" : "FAIL");
        if (!autoIndentOk) { addSummary("Auto Indent result", "FAILED — the Auto Indent screen never opened"); return; }

        addSummary("[Auto Indent] Screen controls", si.describeControls());

        // 17) Select requesting store and issuing store
        String storesResult = si.selectRequestingAndIssuingStore();
        boolean storesOk = si.requestingAndIssuingStoreSelected();
        step(page, "[Auto Indent] Select requesting store and issuing store",
                "Select the Requesting Store and Issuing Store",
                "Both stores are selected",
                storesOk ? "PASSES: " + storesResult : "FAILS: " + storesResult,
                storesOk ? "PASS" : "FAIL");
        if (!storesOk) { addSummary("Auto Indent result", "FAILED — the stores could not be selected"); return; }

        // 18) Click Search
        String autoSearchResult = si.clickAutoIndentSearch();
        boolean autoRowsOk = si.autoIndentRowsFound();
        step(page, "[Auto Indent] Click search", "Click Search",
                "Results appear in Auto Indent Details",
                autoRowsOk ? "PASSES: " + autoSearchResult : "FAILS: " + autoSearchResult,
                autoRowsOk ? "PASS" : "FAIL");
        if (!autoRowsOk) { addSummary("Auto Indent result", "FAILED — no item to select"); return; }

        // 19) In auto indent details, click tick on checkbox to select item
        String autoTickResult = si.tickFirstAutoIndentItem();
        boolean autoTickOk = si.autoIndentItemTicked();
        step(page, "In auto indent details, click tick on checkbox to select item",
                "Tick the checkbox for one item",
                "The item is selected",
                autoTickOk ? "PASSES: " + autoTickResult : "FAILS: " + autoTickResult,
                autoTickOk ? "PASS" : "FAIL");
        if (!autoTickOk) { addSummary("Auto Indent result", "FAILED — no item was selected"); return; }

        // 20) Enter remarks
        String autoRemarksResult = si.enterAutoIndentRemarks(autoRemarks);
        boolean autoRemarksOk = si.autoIndentRemarksEntered(autoRemarks);
        step(page, "[Auto Indent] Enter remarks", "Enter Remarks " + autoRemarks,
                "Remarks is entered",
                autoRemarksOk ? "PASSES: " + autoRemarksResult : "FAILS: " + autoRemarksResult,
                autoRemarksOk ? "PASS" : "FAIL");

        // 21) Click Save Indent
        int tabsBeforeAutoSave = si.clickSaveIndent();
        boolean saveIndentOk = si.saveIndentClicked();
        step(page, "Click save indent", "Click Save Indent",
                "The Save Indent action fires",
                saveIndentOk ? "PASSES: " + si.lastSaveIndentDiagnostics : "FAILS: " + si.lastSaveIndentDiagnostics,
                saveIndentOk ? "PASS" : "FAIL");
        if (!saveIndentOk) { addSummary("Auto Indent result", "FAILED — the Save Indent button was not found"); return; }

        // 22) Verify success toast message — checked before report generation, same reasoning as Manual
        // Indent's own Save above.
        String autoToast = si.waitForSaveToast();
        boolean autoSuccess = com.kpj.pages.Inventory_page.Transfer_page.StoreIndent.isSuccess(autoToast);
        String autoActual = (autoToast == null || autoToast.isEmpty())
                ? "No message appeared — " + si.lastSaveIndentDiagnostics
                : (autoSuccess ? autoToast : "Not confirmed — the screen answered: \"" + autoToast + "\"");
        step(page, "Verify success toast message", "Wait for the success toast",
                "'... successfully' toast", autoActual, autoSuccess ? "PASS" : "FAIL");

        // 23) Verify report generation
        PdfReport.Result autoPdf = PdfReport.capture(page, tabsBeforeAutoSave, 15000);
        step(page, "Verify report generation", "Wait for the report to open",
                "A non-blank PDF is generated", autoPdf.diagnostics, autoPdf.blank ? "FAIL" : "PASS");

        addSummary("[Auto Indent] Requesting/Issuing store", storesResult);
        addSummary("[Auto Indent] Item selected", si.lastAutoIndentItemTick);
        addSummary("[Auto Indent] Remarks", si.lastAutoIndentRemarks);
        addSummary("[Auto Indent] Report result", autoPdf.diagnostics);
        addSummary("[Auto Indent] Save toast result", autoSuccess ? autoToast : "Not confirmed (\"" + autoToast + "\")");
    }
}
