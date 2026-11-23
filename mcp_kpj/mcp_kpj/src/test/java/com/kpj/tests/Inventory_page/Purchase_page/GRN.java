package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named GRN — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>GRN</b>.
 *
 * <ol>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>GRN</b>; click <b>New</b>.</li>
 *   <li>Select the <b>Supplier</b>, the <b>Store</b> (Pharmacy Main Store), enter the <b>Invoice No.</b>, select the <b>PR Type</b>.</li>
 *   <li>Item search: type the <b>Item Name</b> (a smart search — Panadol) and pick the item; enter the <b>Quantity</b> and press Enter;
 *       enter the <b>FOC</b> and press Enter in the same field (the line joins the List of Items).</li>
 *   <li>In the list, tick the item's checkbox; <b>Save</b>; verify the report opens in another tab and the success toast.</li>
 * </ol>
 *
 * <p><b>Two things the steps do not say, both needed on devhis.</b></p>
 * <ul>
 *   <li><b>Batch and expiry.</b> Panadol needs a batch, so the line's <b>Batch Number</b> and <b>Exp Date</b> are mandatory
 *       ("This field is required"; "Please Enter Expiry Date For Item …"). They are filled before the tick — a fresh batch code each
 *       run, and an expiry four years out. The only checkbox on the line is <b>Returnable</b>.</li>
 *   <li><b>PR Type.</b> The default is <b>Non-Inventory</b>, not Inventory. On this environment Pharmacy Main Store's direct-GRN number
 *       series (Inventory, prefix PGR) is stuck: the server keeps generating PGR260000104, which already exists (the series runs to
 *       PGR260000113), so every Inventory save is rejected with "Violation of UNIQUE KEY constraint 'grnno'". Non-Inventory (PNGR)
 *       saves normally. Pin another type with {@code -Dgrn.prtype=}; the failed save reports the server's own reason.</li>
 * </ul>
 *
 * <p>Pin the store with {@code -Dgrn.store=} and the item search term with {@code -Dgrn.item=}. The supplier is the first one listed.
 * This test SAVES a real GRN, which posts stock into the store.</p>
 */
public class GRN extends DevHisBase {

    static final String STORE = System.getProperty("grn.store", "Pharmacy Main Store");
    static final String PR_TYPE = System.getProperty("grn.prtype", "Non-Inventory");
    static final String ITEM = System.getProperty("grn.item", "Panadol");
    static final String QUANTITY = "10";
    static final String FOC = "1";

    public GRN() { super("Inventory_Purchase_GRN"); }

    public static void main(String[] args) {
        GRN t = new GRN();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - GRN", "Inventory > Purchase > GRN",
                "New: supplier, store (Pharmacy Main Store), invoice no, PR type; smart-search an item (Panadol), quantity + Enter, FOC + Enter; "
                        + "batch/expiry, tick the line; Save; verify the GRN report in another tab and the success toast. SAVES a real GRN.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.GRN g = new com.kpj.pages.Inventory_page.Purchase_page.GRN(page);

        // 1) Inventory > Purchase > GRN, New
        boolean list = g.navigateViaMenu();
        step(page, "Open GRN", "Click Inventory -> Purchase -> GRN", "The GRN list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — GRN not reached"); return; }

        boolean form = g.clickNew();
        step(page, "Click New", "Click New (AddGRN)", "The new GRN form is shown",
                form ? "Form opened (" + page.url() + ")" : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the GRN form did not open"); return; }

        // 2) header
        String supplier = g.selectSupplier();
        step(page, "Select Supplier", "Select a Supplier", "A supplier is selected",
                supplier.isEmpty() ? "Not selected — " + g.problems : "Selected \"" + supplier + "\"", supplier.isEmpty() ? "FAIL" : "PASS");
        if (supplier.isEmpty()) { addSummary("Result", "FAILED — no supplier"); return; }

        String store = g.selectStore(STORE);
        step(page, "Select store", "Select the Store (" + STORE + ")", "The store is selected",
                store.isEmpty() ? "Not selected — " + g.problems : "Selected \"" + store + "\"", store.isEmpty() ? "FAIL" : "PASS");
        if (store.isEmpty()) { addSummary("Result", "FAILED — store \"" + STORE + "\" not available"); return; }

        String invoice = "AUTOINV" + String.valueOf(System.currentTimeMillis()).substring(6);
        String inv = g.enterInvoiceNo(invoice);
        step(page, "Enter invoice no", "Enter the Invoice No.", "The invoice number is entered",
                g.ok() ? "Entered \"" + inv + "\" (a fresh one each run)" : "Not entered — " + g.problems, g.ok() ? "PASS" : "FAIL");
        if (!g.ok()) { addSummary("Result", "FAILED — invoice no: " + g.problems); return; }

        String pr = g.selectPrType(PR_TYPE);
        step(page, "Select PR Type", "Select the PR Type (" + PR_TYPE + ")", "The PR type is selected",
                pr.isEmpty() ? "Not selected — " + g.problems
                        : "Selected \"" + pr + "\"" + ("Inventory".equalsIgnoreCase(pr) ? " — NOTE: on this environment the Inventory number series is stuck, so Save will fail"
                                                                                       : " (Inventory is avoided: its GRN number series is stuck on this environment)"),
                pr.isEmpty() ? "FAIL" : "PASS");
        if (pr.isEmpty()) { addSummary("Result", "FAILED — PR type \"" + PR_TYPE + "\" not available"); return; }

        // 3) item search (smart search), quantity + Enter, FOC + Enter
        String sugg = g.searchAndPickItem(ITEM);
        step(page, "Item search — enter Item Name and select it", "Type \"" + ITEM + "\" in Item Name (smart search) and select the item",
                "The suggestions appear and the chosen item fills in the item code and price",
                sugg.isEmpty() ? "No item chosen — " + g.problems
                        : "Chose \"" + sugg + "\" → item code " + g.lastItemCode + ", name \"" + g.lastItemName + "\"" + (g.ok() ? "" : " | " + g.problems),
                sugg.isEmpty() || !g.ok() ? "FAIL" : "PASS");
        if (sugg.isEmpty() || !g.ok()) { addSummary("Result", "FAILED — item search: " + g.problems); return; }

        String qty = g.enterQuantityAndEnter(QUANTITY);
        step(page, "Enter quantity and press Enter", "Enter the Quantity and press Enter", "The quantity is entered and focus moves to FOC",
                g.ok() ? "Quantity " + qty + " entered; Enter moved focus to FOC" : "Problem — " + g.problems, g.ok() ? "PASS" : "FAIL");
        if (!g.ok()) { addSummary("Result", "FAILED — quantity: " + g.problems); return; }

        int rows = g.enterFocAndEnter(FOC);
        step(page, "Enter FOC and press Enter", "Enter the FOC and press Enter in the same field", "The line is added to the List of Items",
                g.ok() ? "FOC " + g.lastFoc + " entered; Enter added the line — the List of Items now has " + rows + " line(s): " + g.lastRow
                       : "Problem — " + g.problems, g.ok() ? "PASS" : "FAIL");
        if (!g.ok()) { addSummary("Result", "FAILED — FOC / add line: " + g.problems); return; }

        // 4) the line: its batch and expiry (mandatory here), then its checkbox
        String batch = "AUTOB" + String.valueOf(System.currentTimeMillis()).substring(6);
        String expiry = java.time.LocalDate.now().plusYears(4).withMonth(12).withDayOfMonth(31)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String be = g.fillBatchAndExpiry(batch, expiry);
        step(page, "Enter the line's batch number and expiry date", "In the List of Items fill the line's Batch Number and Exp Date (mandatory for this item — not in the written steps)",
                "The batch number and expiry date are entered",
                be.isEmpty() ? (g.ok() ? "Not needed: the line does not require a batch" : "Problem — " + g.problems)
                             : be + (g.ok() ? "" : " | " + g.problems),
                g.ok() ? "PASS" : "FAIL");
        if (!g.ok()) { addSummary("Result", "FAILED — batch / expiry: " + g.problems); return; }

        String boxName = g.checkboxColumn();
        boolean ticked = g.tickItemCheckbox();
        step(page, "In the list of items, tick the checkbox", "Tick the checkbox of the item in the List of Items", "The item's checkbox is ticked",
                ticked ? "Ticked the line's checkbox (the column is \"" + boxName + "\") and it reads back as ticked" : "Not ticked — " + g.problems,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED — the checkbox could not be ticked"); return; }

        // 5) Save -> report tab + toast
        String toast = g.clickSaveAndCapture();
        boolean ok = g.saveSucceeded();
        step(page, "Click Save", "Click Save (IUDSave)", "The GRN is saved",
                g.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"") + "; the server answered " + g.lastSaveAnswer,
                toast.isEmpty() ? "FAIL" : "PASS");

        boolean reportOk = !g.reportBlank;
        if (g.reportPng != null && g.reportPng.length > 0) {
            step(g.reportPng, "Verify the report is generated in another tab", "Look for the GRN report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + g.lastReport, reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify the report is generated in another tab", "Look for the GRN report in the new tab", "A report opens in another tab and carries content",
                    (reportOk ? "PASSES because " : "FAILS because ") + g.lastReport, reportOk ? "PASS" : "FAIL");
        }

        step(page, "Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\" — server: " + g.lastSaveAnswer),
                ok ? "PASS" : "FAIL");

        addSummary("Supplier / store / PR type", g.lastSupplier + " / " + g.lastStore + " / " + g.lastPrType);
        addSummary("Invoice No.", g.lastInvoice);
        addSummary("Item", g.lastRow);
        addSummary("Batch / expiry", g.lastBatch + " / " + g.lastExpiry);
        addSummary("GRN no.", g.lastGrnNo.isEmpty() ? "(none — not saved)" : g.lastGrnNo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + g.lastSaveAnswer));
    }
}
