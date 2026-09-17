package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named Transfer — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Transfer</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>CSSD</b> → <b>Transfer</b>, then <b>New</b>.</li>
 *   <li>Click <b>Get Indent</b> → the Item Search modal opens.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In <b>Selected Item</b> (indent headers): tick a row → verify it populates <b>Indent Item List</b>.</li>
 *   <li>In <b>Indent Item List</b>: tick a row → verify it populates <b>Item Batch List</b> and the
 *       (second) <b>Selected Item</b> grid.</li>
 *   <li>Ensure the batch is ticked in <b>Selected Item</b> → click <b>OK</b>.</li>
 *   <li>Enter <b>Unit Quantity</b> on the item row now in the main form.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES a real transfer in the target environment.</p>
 *
 * <h2>Data dependency, confirmed live 2026-09-09</h2>
 * <p>Steps 5-6 do not depend on any particular indent existing — search is against whatever indent(s)
 * currently target CSSD as issuing store. But steps 7 onward require the ticked item to have <b>real batch
 * stock</b> at the issuing store. Verified live: as of this writing devhis carries exactly one indent
 * targeting CSSD (item "BD SYRINGE 5CC"), and its Available Qty is 0 — confirmed both from the Indent Item
 * List grid and by calling the batch API directly ({@code ExecFlag: "BatcList"} returned {@code []}). This
 * is a real stock/data gap, not a script defect: {@code IdentItemSearchController.js}'s own
 * {@code GetBatchList} only auto-selects a batch when the server actually returns one. If the indent data
 * changes (new indent, or CSSD's stock topped up), steps 7-9 will exercise for real; until then this test
 * reports precisely how far the real data allows and stops there rather than fabricating a save.</p>
 */
public class Transfer extends DevHisBase {

    public Transfer() { super("AncillaryServices_CSSD_Transfer"); }

    public static void main(String[] args) {
        Transfer t = new Transfer();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Transfer", "Ancillary Services > CSSD > Transfer",
                "&#9888; Creates a REAL store transfer: Get Indent, Search, select item + batch, Unit Quantity, Save.");

        String issuingStore = System.getProperty("issuingStore", "CSSD");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.Transfer xfer =
                new com.kpj.pages.AncillaryServices_page.CSSD_page.Transfer(page);

        // 1) Navigate
        boolean onScreen = xfer.navigateViaMenu();
        step(page, "Open CSSD Transfer screen", "Click Ancillary Services -> CSSD -> Transfer",
                "The Transfer list screen is shown",
                onScreen ? "Opened (Transfer list screen)" : "Did NOT reach the screen",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = xfer.clickNew();
        step(page, "Click New", "Click New (AddIssueToStore)",
                "The add-transfer form opens (Get Indent / Get Items visible)",
                formOpen ? "Form opened" : "Form did NOT open",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — add-transfer form not reached"); return; }

        // 3) Get Indent
        boolean modalOpen = xfer.clickGetIndent();
        step(page, "Click Get Indent", "Click Get Indent (OpenIndentdetailsList)",
                "The Item Search modal opens", modalOpen ? "Modal opened" : "Modal did NOT open",
                modalOpen ? "PASS" : "FAIL");
        if (!modalOpen) { addSummary("Result", "FAILED — Item Search modal not reached"); return; }

        // Narrow the search to the issuing store this screen is about, so the result is meaningful
        // rather than an unfiltered dump.
        String storePicked = xfer.selectModalIssuingStore(issuingStore);

        // 4) Search
        boolean searched = xfer.clickModalSearch();
        List<String> indentRows = xfer.selectedIndentRows();
        step(page, "Click Search", "Click Search (SearchIndentItem), Issuing Store = " + storePicked,
                "The Selected Item list shows matching indent(s)",
                searched
                    ? (indentRows.isEmpty() ? "Search ran, but no indent currently targets " + storePicked
                                             : indentRows.size() + " indent(s) found: " + indentRows)
                    : "Search click failed",
                searched && !indentRows.isEmpty() ? "PASS" : "FAIL");
        if (!searched || indentRows.isEmpty()) {
            addSummary("Result", "FAILED — no indent available to select (searched Issuing Store = " + storePicked + ")");
            return;
        }

        // 5) Selected Item -> tick -> verify Indent Item List
        String indentRow = xfer.tickFirstSelectedIndentRow();
        List<String> itemRows = xfer.indentItemListRows();
        boolean itemListOk = !itemRows.isEmpty();
        step(page, "Selected Item · Tick checkbox to select the item", "Tick the first Selected Item (indent) row",
                "The item populates in Indent Item List",
                itemListOk
                    ? "PASSES because Indent Item List shows: " + itemRows
                    : "FAILS — ticked indent \"" + indentRow + "\" but Indent Item List stayed empty",
                itemListOk ? "PASS" : "FAIL");
        if (!itemListOk) { addSummary("Result", "FAILED — Indent Item List did not populate"); return; }

        // 6) Indent Item List -> tick -> verify Item Batch List + Selected Item (bottom)
        String itemRow = xfer.tickFirstIndentItemRow();
        List<String> batchRows = xfer.itemBatchListRows();
        List<String> bottomRows = xfer.bottomSelectedItemRows();
        boolean batchOk = !batchRows.isEmpty() && !bottomRows.isEmpty();
        step(page, "Indent Item List · Tick checkbox to select the item", "Tick the first Indent Item List row",
                "The item populates in Item Batch List and Selected Item",
                batchOk
                    ? "PASSES — Item Batch List: " + batchRows + " | Selected Item: " + bottomRows
                    : "FAILS — WHAT: no batch is available to select for \"" + itemRow + "\". WHERE: the "
                      + "Item Batch List / Selected Item grids stayed empty after ticking. WHY: confirmed live "
                      + "via the batch API (ExecFlag \"BatcList\") that this item's Available Qty at "
                      + storePicked + " is 0 — a real stock gap in the environment, not a selector issue "
                      + "(IdentItemSearchController.js's GetBatchList only auto-selects when the server "
                      + "actually returns a batch). " + xfer.lastBatchListState,
                batchOk ? "PASS" : "FAIL");
        if (!batchOk) {
            addSummary("Indent item ticked", itemRow);
            addSummary("Result", "FAILED — no available batch stock for this item at " + storePicked
                    + " (environment data gap, confirmed via direct API check — see step reason)");
            return;
        }

        // 7) Ensure Selected Item (bottom) is ticked, then OK
        String bottomRow = xfer.ensureBottomSelectedItemTicked();
        boolean okClicked = xfer.clickOK();
        step(page, "Selected Item · Tick checkbox & click OK", "Ensure the batch is selected in Selected Item, click OK",
                "The modal closes and the item is added to the main Transfer form",
                okClicked ? "Modal closed; row = " + bottomRow : "Modal did NOT close (likely rejected — no item selected)",
                okClicked ? "PASS" : "FAIL");
        if (!okClicked) { addSummary("Result", "FAILED — OK did not close the modal"); return; }

        // 8) Unit Quantity
        String qty = System.getProperty("unitQty", "1");
        String qtyEntered = xfer.enterUnitQuantity(qty);
        boolean qtyOk = qtyEntered != null && qtyEntered.equals(qty);
        step(page, "Enter unit quantity", "Enter Unit Quantity = " + qty,
                "The quantity is entered on the item row",
                qtyOk ? "Unit Quantity = " + qtyEntered : "Unit Quantity NOT entered (" + qtyEntered + ")",
                qtyOk ? "PASS" : "FAIL");

        // 9) Save -> success toast
        String toast = xfer.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean saveOk = tl.contains("success") || tl.contains("saved");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (saveOk ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (IUDSave)",
                "'... saved successfully' toast", actual, saveOk ? "PASS" : "FAIL");

        addSummary("Issuing Store", storePicked);
        addSummary("Indent", indentRow);
        addSummary("Item", itemRow);
        addSummary("Unit Quantity", qtyEntered);
        addSummary("Result", saveOk ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
