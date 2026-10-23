package com.kpj.tests.Inventory_page.Purchase_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.math.BigDecimal;
import java.util.Set;

// The page object is also named WorkOrder — referenced by its fully-qualified name.

/**
 * Inventory &gt; Purchase &gt; <b>Work Order</b>.
 *
 * <ol>
 *   <li>Open <b>Inventory</b> → <b>Purchase</b> → <b>Work Order</b>; click <b>New</b>.</li>
 *   <li>Select the <b>Store</b> (a pharmacy one — Pharmacy Main Store) and the <b>Supplier</b>.</li>
 *   <li>Enter the item <b>Code</b> (PH10000000) and <b>Name</b> (Panadol) and the <b>Quantity</b>; press Enter — the line appears in Work Details.</li>
 *   <li>Enter the <b>Price</b> in the line and press Enter; click <b>Save</b>; verify the success toast.</li>
 * </ol>
 *
 * <p>Code, Name and Quantity are plain text boxes (nothing is searched or looked up — what is typed becomes the line), so the Code and Name given
 * are used as typed. The Price is entered in the Work Details row, where the amounts are computed (the test checks Amount = Quantity × Price).</p>
 *
 * <p>Pin values with {@code -Dworkorder.store=}, {@code -Dworkorder.code=}, {@code -Dworkorder.item=}, {@code -Dworkorder.qty=},
 * {@code -Dworkorder.price=}. The supplier is the first one listed. This test SAVES a real work order (the number is read back from the list).</p>
 *
 * <p><b>Enter caveat.</b> The screen's Save button is the form's default submit button, so an Enter in Quantity or in the row's Price also runs
 * Save's validation and can raise a stray warning ("Please Enter Net Amount!"). The test records any such toast but treats the Enter step as
 * passed when the line was added / the amounts calculated, presses Enter in Price only once (a second one would really save), and clears the
 * warning before the real Save.</p>
 */
public class WorkOrder extends DevHisBase {

    static final String STORE = System.getProperty("workorder.store", "Pharmacy Main Store");
    static final String CODE = System.getProperty("workorder.code", "PH10000000");
    static final String ITEM = System.getProperty("workorder.item", "Panadol");
    static final String QUANTITY = System.getProperty("workorder.qty", "5");
    static final String PRICE = System.getProperty("workorder.price", "25");

    public WorkOrder() { super("Inventory_Purchase_WorkOrder"); }

    public static void main(String[] args) {
        WorkOrder t = new WorkOrder();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - Purchase - Work Order", "Inventory > Purchase > Work Order",
                "New: store (Pharmacy Main Store), supplier, item code (PH10000000), item name (Panadol), quantity + Enter, verify the Work Details row, "
                        + "price + Enter, Save; verify the success toast (and the new order on the list). SAVES a real work order.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Purchase_page.WorkOrder w = new com.kpj.pages.Inventory_page.Purchase_page.WorkOrder(page);

        // 1) Inventory > Purchase > Work Order (and what is already listed today, to recognise the new order later)
        boolean list = w.navigateViaMenu();
        step(page, "Open Work Order", "Click Inventory -> Purchase -> Work Order", "The Work Order list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — Work Order not reached"); return; }
        Set<String> before = w.searchList();

        boolean form = w.clickNew();
        step(page, "Click New", "Click New (AddNewInventoryWorkOrder)", "The new Work Order form is shown",
                form ? "Form opened (" + page.url() + "); " + before.size() + " work order(s) were already listed for today" : "The form did not open (" + page.url() + ")",
                form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the Work Order form did not open"); return; }

        // 2) store (pharmacy) and supplier
        String store = w.selectStore(STORE);
        step(page, "Select store", "Select the Store (" + STORE + " — a pharmacy store)", "The store is selected",
                store.isEmpty() ? "Not selected — " + w.problems : "Selected \"" + store + "\"" + (w.ok() ? "" : " | " + w.problems),
                store.isEmpty() || !w.ok() ? "FAIL" : "PASS");
        if (store.isEmpty() || !w.ok()) { addSummary("Result", "FAILED — no pharmacy store: " + w.problems); return; }

        String supplier = w.selectSupplier();
        step(page, "Select supplier", "Select a Supplier", "A supplier is selected",
                supplier.isEmpty() ? "Not selected — " + w.problems : "Selected \"" + supplier + "\"", supplier.isEmpty() || !w.ok() ? "FAIL" : "PASS");
        if (supplier.isEmpty() || !w.ok()) { addSummary("Result", "FAILED — no supplier: " + w.problems); return; }

        // 3) item code, item name, quantity, Enter
        String code = w.enterCode(CODE);
        step(page, "Enter item code", "Enter the item Code (" + CODE + ")", "The item code is entered",
                w.ok() ? "Entered \"" + code + "\"" : "Problem — " + w.problems, w.ok() ? "PASS" : "FAIL");
        if (!w.ok()) { addSummary("Result", "FAILED — item code: " + w.problems); return; }

        String name = w.enterName(ITEM);
        step(page, "Enter item name", "Enter the item Name (" + ITEM + ")", "The item name is entered",
                w.ok() ? "Entered \"" + name + "\"" : "Problem — " + w.problems, w.ok() ? "PASS" : "FAIL");
        if (!w.ok()) { addSummary("Result", "FAILED — item name: " + w.problems); return; }

        String qty = w.enterQuantity(QUANTITY);
        step(page, "Enter quantity", "Enter the Quantity (" + QUANTITY + ")", "The quantity is entered",
                w.ok() ? "Entered \"" + qty + "\"" : "Problem — " + w.problems, w.ok() ? "PASS" : "FAIL");
        if (!w.ok()) { addSummary("Result", "FAILED — quantity: " + w.problems); return; }

        int rows = w.pressEnterInQuantity();
        boolean rowOk = w.ok() && rows == 1 && w.rowHolds(CODE, ITEM, QUANTITY);
        step(page, "Click Enter — verify the row is populated in Work Details", "Press Enter in Quantity; look at the Work Details table",
                "The line (code, name, quantity) is listed in Work Details",
                (rowOk ? "PASSES because Work Details now has " + rows + " line: " + w.lastRow
                       : "FAILS because " + (w.ok() ? "Work Details shows " + rows + " line(s): " + w.lastRow : w.problems + " (" + rows + " line(s): " + w.lastRow + ")"))
                        + (w.lastStrayToast.isEmpty() ? "" : " — NOTE the Enter also raised a warning toast: \"" + w.lastStrayToast + "\" (the Save button is the form's default submit button)"),
                rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — the line did not populate: " + w.lastRow); return; }

        // 4) price in the row, Enter
        String row = w.enterPriceAndEnter(PRICE);
        BigDecimal expected = new BigDecimal(QUANTITY).multiply(new BigDecimal(PRICE));
        boolean amountOk = false;
        try { amountOk = !w.amount().isEmpty() && new BigDecimal(w.amount()).compareTo(expected) == 0; } catch (NumberFormatException ignore) { }
        boolean netOk = false;
        try { netOk = !w.netAmount().isEmpty() && new BigDecimal(w.netAmount()).compareTo(expected) == 0; } catch (NumberFormatException ignore) { }
        boolean priceOk = w.ok() && amountOk && netOk;
        step(page, "Enter price and click Enter", "Enter the Price in the Work Details row and press Enter (once)",
                "The price is held and the line's amounts are calculated (Amount = Quantity x Price)",
                (priceOk ? "PASSES because " : "FAILS because ") + (w.ok() ? "" : w.problems + " — ") + row + "; the form shows " + w.lastTotals
                        + " (expected " + expected.toPlainString() + ")"
                        + (w.lastStrayToast.isEmpty() ? "" : " — NOTE the Enter also raised a warning toast: \"" + w.lastStrayToast + "\" (the implicit Save ran before the amounts were calculated)"),
                priceOk ? "PASS" : "FAIL");
        if (!priceOk) { addSummary("Result", "FAILED — price / amounts: " + w.problems + " " + row); return; }

        // 5) Save -> toast
        String toast = w.clickSaveAndCapture();
        boolean ok = w.saveSucceeded() && w.savedOnServer;
        step(page, "Click Save", "Click Save (IUDWorkOrder)", "The work order is saved",
                w.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"") + "; the server answered " + w.lastSaveAnswer,
                toast.isEmpty() ? "FAIL" : "PASS");

        step(page, "Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed the save (ResultStatus 1)"
                   : (toast.isEmpty() ? "FAILS because no toast appeared — server: " + w.lastSaveAnswer
                                      : "FAILS because the toast is not a success message: \"" + toast + "\" — server: " + w.lastSaveAnswer),
                ok ? "PASS" : "FAIL");

        // 6) read the new order back from the list
        String wo = "";
        if (ok) {
            wo = w.findNewWorkOrder(before);
            boolean linesOk = !wo.isEmpty() && w.lastWoLines.toLowerCase().contains(CODE.toLowerCase()) && w.lastWoLines.toLowerCase().contains(ITEM.toLowerCase());
            step(page, "Verify the new work order is listed", "After Save the app returns to the list: Search today and open the new order",
                    "The new work order is listed and carries the saved line",
                    wo.isEmpty() ? "FAILS because no new work order number appeared on the list for today (" + before.size() + " were listed before)"
                                 : (linesOk ? "PASSES because " : "FAILS because ") + "new work order " + wo + " is listed; its line reads: " + w.lastWoLines,
                    linesOk ? "PASS" : "FAIL");
        }

        addSummary("Store / supplier", w.lastStore + " / " + w.lastSupplier);
        addSummary("Item", CODE + " — " + ITEM + " x " + QUANTITY + " @ " + PRICE);
        addSummary("Work Details row", w.lastRow);
        addSummary("Totals", w.lastTotals);
        addSummary("Work order no.", wo.isEmpty() ? "(none read back)" : wo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast + " — " + w.lastSaveAnswer));
    }
}
