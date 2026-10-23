package com.kpj.tests.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is named CurrentItemStock — referenced by its fully-qualified name.

/**
 * Inventory &gt; <b>Current Item Stock</b> &gt; <b>Print Item Label</b>.
 *
 * <ol>
 *   <li>Open <b>Inventory</b> → <b>Current Item Stock</b>.</li>
 *   <li>Select a row of the stock list by <b>clicking</b> it.</li>
 *   <li>Click <b>Print Item Label</b> and verify the item label is generated.</li>
 * </ol>
 *
 * <p>The label is a new tab (an HTML page, {@code frmcurrentstockbarcodeReport.aspx}, titled "Item Label"), not a PDF. It is
 * verified on what it shows: it must open, carry a loaded QR image, and show the SELECTED row's item code, batch code and item
 * name — so a label for some other item would fail. The test takes the first stock line that has an item code and a batch code;
 * no item is hardcoded.</p>
 *
 * <p>Printing a label also records the item's barcode image on the server (a second call, answered "already …" on a repeat),
 * so a repeat run is harmless; nothing else is saved.</p>
 */
public class CurrentItemStockPrintItemLabel extends DevHisBase {

    public CurrentItemStockPrintItemLabel() { super("Inventory_CurrentItemStock_PrintItemLabel"); }

    public static void main(String[] args) {
        CurrentItemStockPrintItemLabel t = new CurrentItemStockPrintItemLabel();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Current Item Stock - Print Item Label", "Inventory > Current Item Stock > Print Item Label",
                "Select a stock row by clicking it, click Print Item Label and verify the item label (a new tab with the QR and the selected "
                        + "item's code, batch and name) is generated.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.CurrentItemStock cis = new com.kpj.pages.Inventory_page.CurrentItemStock(page);

        // 1) Inventory > Current Item Stock
        boolean on = cis.navigateViaMenu();
        step(page, "Open Current Item Stock", "Click Inventory -> Current Item Stock", "The Current Item Stock list is shown with stock lines",
                on ? "Opened " + page.url() + " — " + cis.stockRows() + " stock line(s) on the first page"
                   : "Did NOT reach the screen with stock (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Current Item Stock not reached"); return; }

        // 2) select a row by clicking it
        String selected = cis.selectRowByClick();
        step(page, "Select a row by clicking it", "Click a row in the stock list", "The row is selected",
                selected.isEmpty() ? "No row could be selected" : selected, selected.isEmpty() ? "FAIL" : "PASS");
        if (selected.isEmpty()) { addSummary("Result", "FAILED — no row could be selected"); return; }

        // 3) Print Item Label -> the label opens in a new tab
        String label = cis.clickPrintItemLabelAndVerify();
        boolean ok = cis.labelOk;
        // the label's own picture when there is one, else the screen as it stands
        if (cis.labelPng != null && cis.labelPng.length > 0) {
            step(cis.labelPng, "Click Print Item Label — verify the item label is generated", "Click Print Item Label for the selected row",
                    "An Item Label tab opens with the QR and the selected item's code, batch and name",
                    (ok ? "PASSES because " : "FAILS because ") + label, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Print Item Label — verify the item label is generated", "Click Print Item Label for the selected row",
                    "An Item Label tab opens with the QR and the selected item's code, batch and name",
                    (ok ? "PASSES because " : "FAILS because ") + label, ok ? "PASS" : "FAIL");
        }

        addSummary("Selected row", cis.lastRow);
        addSummary("Item", cis.lastItemCode + " — " + cis.lastItemName + " (batch " + cis.lastBatch + ", " + cis.lastStore + ")");
        addSummary("Item label", cis.lastLabelUrl.isEmpty() ? "(none)" : cis.lastLabelUrl);
        addSummary("Result", ok ? "Item label generated" : "Item label NOT verified — " + label);
    }
}
