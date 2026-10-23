package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LoanGRN — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Loan GRN</b>.
 *
 * <ol>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Loan GRN</b>; click <b>New</b> (the form opens on <b>Receive Loan In</b>).</li>
 *   <li>Select the <b>Supplier</b> and the <b>Store</b> (a pharmacy one — Pharmacy Main Store).</li>
 *   <li>Item search: type the <b>Item Name</b> (a smart search — Panadol) and pick the item; enter the <b>Expiry Date</b> and the
 *       <b>Quantity</b>, press Enter — the line joins the List of Items.</li>
 *   <li><b>Save</b>; verify the report opens in another tab and the success toast.</li>
 * </ol>
 *
 * <p><b>Expiry Date</b> is a month/year datepicker that discards typed characters, so the date is picked in its calendar (a year four years out, and
 * the month on show); the box then reads {@code MM/yy}. Batch and Invoice No. are not in the steps and neither the screen nor the server requires
 * them (repeated saves with both blank are accepted), so they are left empty.</p>
 *
 * <p>Pin the store with {@code -Dloangrn.store=} and the item search term with {@code -Dloangrn.item=}. The supplier is the first one listed. This
 * test SAVES a real Loan GRN (Receive Loan In), which posts stock into the store.</p>
 */
public class LoanGRN extends DevHisBase {

    static final String STORE = System.getProperty("loangrn.store", "Pharmacy Main Store");
    static final String ITEM = System.getProperty("loangrn.item", "Panadol");
    static final String QUANTITY = "10";
    static final int EXPIRY_YEARS_AHEAD = 4;

    public LoanGRN() { super("Inventory_Purchase_LoanGRN"); }

    public static void main(String[] args) {
        LoanGRN t = new LoanGRN();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Loan GRN", "Inventory > Purchase > Loan GRN",
                "New (Receive Loan In): supplier, store (Pharmacy Main Store); smart-search an item (Panadol), expiry date, quantity + Enter, verify the "
                        + "List of Items; Save; verify the GRN report in another tab and the success toast. SAVES a real Loan GRN.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.LoanGRN g = new com.kpj.pages.Inventory_page.Purchase_page.LoanGRN(page);

        // 1) Inventory > Purchase > Loan GRN, New
        boolean list = g.navigateViaMenu();
        step(page, "Open Loan GRN", "Click Inventory -> Purchase -> Loan GRN", "The Loan GRN list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — Loan GRN not reached"); return; }

        boolean form = g.clickNew();
        step(page, "Click New", "Click New (AddGRN)", "The new Loan GRN form is shown",
                form ? "Form opened (" + page.url() + "); type: " + g.loanType() : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the Loan GRN form did not open"); return; }

        // 2) supplier, store
        String supplier = g.selectSupplier();
        step(page, "Select Supplier", "Select a Supplier", "A supplier is selected",
                supplier.isEmpty() ? "Not selected — " + g.problems : "Selected \"" + supplier + "\"", supplier.isEmpty() || !g.ok() ? "FAIL" : "PASS");
        if (supplier.isEmpty() || !g.ok()) { addSummary("Result", "FAILED — no supplier: " + g.problems); return; }

        String store = g.selectStore(STORE);
        step(page, "Select store", "Select the Store (" + STORE + " — a pharmacy store)", "The store is selected",
                store.isEmpty() ? "Not selected — " + g.problems : "Selected \"" + store + "\"", store.isEmpty() || !g.ok() ? "FAIL" : "PASS");
        if (store.isEmpty() || !g.ok()) { addSummary("Result", "FAILED — no pharmacy store: " + g.problems); return; }

        // 3) item search: name (smart search), select an item
        String sugg = g.searchAndPickItem(ITEM);
        step(page, "Item search — enter Item Name and select an item", "Type \"" + ITEM + "\" in Item Name (smart search) and select an item",
                "The suggestions appear and the chosen item fills in the item code and price",
                sugg.isEmpty() ? "No item chosen — " + g.problems
                        : "Chose \"" + sugg + "\" → item code " + g.lastItemCode + ", name \"" + g.lastItemName + "\"" + (g.ok() ? "" : " | " + g.problems),
                sugg.isEmpty() || !g.ok() ? "FAIL" : "PASS");
        if (sugg.isEmpty() || !g.ok()) { addSummary("Result", "FAILED — item search: " + g.problems); return; }

        // 4) expiry date (a datepicker), quantity + Enter
        int year = java.time.LocalDate.now().getYear() + EXPIRY_YEARS_AHEAD;
        boolean enabled = g.expiryEnabled();
        String expiry = enabled ? g.pickExpiryDate(year) : "";
        step(page, "Enter expiry date", "Enter the Expiry Date (picked in the calendar: " + year + ")", "The expiry date is entered (MM/yy)",
                !enabled ? "The Expiry Date box is disabled for this item (it is not batch-tracked)"
                         : expiry.isEmpty() || !g.ok() ? "Not entered — " + g.problems : "Expiry Date reads " + expiry + " (month/year picked in the calendar)",
                enabled && !expiry.isEmpty() && g.ok() ? "PASS" : "FAIL");
        if (!enabled || expiry.isEmpty() || !g.ok()) { addSummary("Result", "FAILED — expiry date: " + (enabled ? g.problems : "box disabled")); return; }

        int rows = g.enterQuantityAndEnter(QUANTITY);
        step(page, "Enter quantity and click Enter", "Enter the Quantity (" + QUANTITY + ") and press Enter", "The line is added to the List of Items",
                g.ok() ? "Quantity " + g.lastQty + " entered; Enter added the line — the List of Items now has " + rows + " line(s)" : "Problem — " + g.problems,
                g.ok() ? "PASS" : "FAIL");
        if (!g.ok()) { addSummary("Result", "FAILED — quantity / add line: " + g.problems); return; }

        boolean held = g.rowHolds(expiry, QUANTITY);
        step(page, "Verify the item is added — populated in List of Items", "Look at the List of Items", "The item, its expiry date and quantity are listed",
                (held ? "PASSES because the list shows: " : "FAILS because the list shows: ") + g.lastRow + "; the form shows " + g.totals(),
                held ? "PASS" : "FAIL");
        if (!held) { addSummary("Result", "FAILED — the line is not populated: " + g.lastRow); return; }

        // 5) Save -> report tab + toast (a refused blank invoice number is retried once with a fresh one)
        String toast = g.clickSaveAndCapture();
        boolean ok = g.saveSucceeded() && g.savedOnServer();
        step(page, "Click Save", "Click Save (IUDSave)", "The Loan GRN is saved",
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

        // 6) the new Loan GRN on the list
        String row = "";
        if (ok && !g.lastGrnNo.isEmpty()) {
            row = g.findInList(g.lastGrnNo);
            step(page, "Verify the new Loan GRN is listed", "After Save the app returns to the list: Search today and look for the new GRN",
                    "The new Loan GRN is listed", row.isEmpty() ? "FAILS because " + g.lastGrnNo + " was not found on the list for today"
                                                                : "PASSES because the list shows: " + row,
                    row.isEmpty() ? "FAIL" : "PASS");
        }

        addSummary("Type / supplier / store", g.lastLoanType + " / " + g.lastSupplier + " / " + g.lastStore);
        addSummary("Item", g.lastRow);
        addSummary("Loan GRN no.", g.lastGrnNo.isEmpty() ? "(none — not saved)" : g.lastGrnNo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + g.lastSaveAnswer));
    }
}
