package com.kpj.tests.Inventory_page.Transfer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;

// The page object is also named CrossLocationTransfer — referenced by its fully-qualified name.

/**
 * Inventory &gt; Transfer &gt; <b>Cross Location Transfer</b>.
 *
 * <ol>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select the <b>requesting store</b> (the form's From Store: Pharmacy (Central), else Pharmacy Main Store) and the <b>issuing store</b> (the To Store: Pharmacy Main Store, else Pharmacy (Central)).</li>
 *   <li>Click <b>Get Indent</b>; in the <b>Item Search</b> dialog click <b>Search</b>.</li>
 *   <li>Tick a row in <b>Selected Item</b> (the indents); tick a row in <b>Indent Item List</b> (fills <b>Item Batch List</b>); tick the <b>Item Batch List</b> row (fills the bottom
 *       <b>Selected Item</b>); tick the bottom <b>Selected Item</b> row; click <b>OK</b>.</li>
 *   <li>Enter the <b>Quantity</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>The "or" in the store steps is handled as an ordered fallback: the test starts with requesting = Pharmacy (Central) and issuing = Pharmacy Main Store; if that pair has no indent item with
 * stock it closes the dialog and tries the swapped pair. This test SAVES A REAL TRANSFER on every run (stock moves, the indent's pending quantity falls). Pin the pair with
 * {@code -Dcrosslocation.requesting=} / {@code -Dcrosslocation.issuing=} (then no fallback is tried) and the quantity with {@code -Dcrosslocation.qty=}.</p>
 */
public class CrossLocationTransfer extends DevHisBase {

    static final String REQUESTING = System.getProperty("crosslocation.requesting", "");
    static final String ISSUING = System.getProperty("crosslocation.issuing", "");
    static final String QTY = System.getProperty("crosslocation.qty", "1");
    static final int MAX_INDENTS = Integer.getInteger("crosslocation.maxindents", 15);

    public CrossLocationTransfer() { super("Inventory_Transfer_CrossLocationTransfer"); }

    public static void main(String[] args) {
        CrossLocationTransfer t = new CrossLocationTransfer();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** One recorded step, held back until we know whether the store pair it ran on is the one that is kept. */
    private static final class Rec {
        final String name, desc, expected, actual, status;
        Rec(String name, String desc, String expected, String actual, String status) { this.name = name; this.desc = desc; this.expected = expected; this.actual = actual; this.status = status; }
    }

    private void flush(List<Rec> recs) { for (Rec r : recs) step(r.name, r.desc, r.expected, r.actual, r.status); recs.clear(); }

    @Override
    protected void body() {
        meta("Inventory - Cross Location Transfer", "Inventory > Transfer > Cross Location Transfer",
                "New; requesting store (Pharmacy (Central) or Pharmacy Main Store); issuing store (Pharmacy Main Store or Pharmacy (Central)); Get Indent; Search in the Item Search dialog; tick the indent, "
                        + "its item, the batch and the queued Selected Item row; OK; quantity; Save; verify the success toast. SAVES A REAL TRANSFER.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Inventory_page.Transfer_page.CrossLocationTransfer xl = new com.kpj.pages.Inventory_page.Transfer_page.CrossLocationTransfer(page);

        boolean on = xl.navigateViaMenu();
        step("Open Cross Location Transfer", "Click Inventory -> Transfer -> Cross Location Transfer", "The Cross Location Transfer list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Cross Location Transfer not reached"); return; }

        // store pairs: {requesting, issuing}; pinned by properties, otherwise Central -> Main Store with the swap as the fallback
        List<String[]> pairs = new ArrayList<>();
        if (!REQUESTING.isEmpty() || !ISSUING.isEmpty()) pairs.add(new String[]{REQUESTING.isEmpty() ? "Pharmacy (Central)" : REQUESTING, ISSUING.isEmpty() ? "Pharmacy Main Store" : ISSUING});
        else { pairs.add(new String[]{"Pharmacy (Central)", "Pharmacy Main Store"}); pairs.add(new String[]{"Pharmacy Main Store", "Pharmacy (Central)"}); }

        List<Rec> recs = new ArrayList<>();
        List<String> attempts = new ArrayList<>();
        boolean ready = false;
        for (int p = 0; p < pairs.size() && !ready; p++) {
            String req = pairs.get(p)[0], iss = pairs.get(p)[1];
            boolean last = p == pairs.size() - 1;
            recs.clear();

            // 1) New
            if (p > 0) { xl.closeDialog(); }                                   // leave the previous attempt's dialog; the form is reused, only the stores change
            if (p == 0) {
                boolean opened = xl.clickNew();
                recs.add(new Rec("Click New", "Click New", "The Cross Location Transfer form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + xl.problems, opened ? "PASS" : "FAIL"));
                if (!opened) { flush(recs); addSummary("Result", "FAILED — New: " + xl.problems); return; }
            } else {
                recs.add(new Rec("Click New", "Click New", "The Cross Location Transfer form opens", "Form already open (reused for the swapped store pair)", "PASS"));
            }

            // 2) requesting store
            String r = xl.selectRequestingStore(req);
            boolean rOk = !r.isEmpty() && xl.ok();
            recs.add(new Rec("Select requesting store", "Select the requesting store (From Store: " + req + ")", "The store is selected", rOk ? "Selected \"" + r + "\"" : "Not selected — " + xl.problems, rOk ? "PASS" : "FAIL"));
            if (!rOk) { flush(recs); addSummary("Result", "FAILED — requesting store: " + xl.problems); return; }

            // 3) issuing store
            String i = xl.selectIssuingStore(iss);
            boolean iOk = !i.isEmpty() && xl.ok();
            recs.add(new Rec("Select issuing store", "Select the issuing store (To Store: " + iss + ")", "The store is selected", iOk ? "Selected \"" + i + "\"" : "Not selected — " + xl.problems, iOk ? "PASS" : "FAIL"));
            if (!iOk) { flush(recs); addSummary("Result", "FAILED — issuing store: " + xl.problems); return; }

            // 4) Get Indent
            boolean dlg = xl.clickGetIndent();
            recs.add(new Rec("Click Get Indent", "Click Get Indent", "The Item Search dialog opens with the two stores filled in",
                    dlg ? "Item Search dialog opened — " + xl.dialogStores() : "Problem — " + xl.problems, dlg ? "PASS" : "FAIL"));
            if (!dlg) { flush(recs); addSummary("Result", "FAILED — Get Indent: " + xl.problems); return; }

            // 5) Search
            int n = xl.clickSearch();
            recs.add(new Rec("Click Search (Item Search dialog)", "Click Search", "The indents between the two stores are listed under Selected Item",
                    n > 0 ? n + " indent(s) listed, first: \"" + xl.indentRows().get(0).replaceAll("\\s+", " ") + "\"" : "Problem — " + xl.problems, n > 0 ? "PASS" : "FAIL"));
            if (n == 0) {
                attempts.add(req + " -> " + iss + ": Search listed no indents");
                if (last) { flush(recs); addSummary("Result", "FAILED — Search: " + xl.problems); return; }
                continue;
            }

            // 6-8) tick the indent, its item, (the batch fills in)
            boolean found = xl.selectIndentItemWithStock(MAX_INDENTS);
            if (!found) {
                attempts.add(req + " -> " + iss + ": " + n + " indent(s) but " + xl.problems);
                if (last) {
                    recs.add(new Rec("Tick the indent / indent item / batch", "Tick a row in Selected Item, then in Indent Item List", "Item Batch List and the bottom Selected Item fill in",
                            "Problem — " + xl.problems, "FAIL"));
                    flush(recs); addSummary("Result", "FAILED — no indent item with stock: " + xl.problems); return;
                }
                continue;
            }
            attempts.add(req + " -> " + iss + ": OK, indent " + xl.lastIndent);
            ready = true;
            // the winning pair's steps now go to the report, in the order the requirement lists them
            flush(recs);
            step("In Selected Item, tick the checkbox to select a row", "Tick the checkbox of an indent in the Selected Item table",
                    "The indent is ticked and its items appear in Indent Item List", "Ticked indent " + xl.lastIndent + " — Indent Item List populated", "PASS");
            step("In Indent Item List, tick the checkbox for the row", "Tick the checkbox of an item in Indent Item List",
                    "The item is ticked and its batches populate Item Batch List", "Ticked \"" + xl.lastIndentItem + "\"", "PASS");
        }

        String batch = xl.ensureBatchTicked();
        boolean batchOk = !batch.isEmpty() && xl.ok();
        step("In Item Batch List, tick the checkbox for the row", "Make sure the Item Batch List row is ticked (the dialog ticks it itself when the item is ticked; it is clicked only if it is not)",
                "The batch is ticked and the bottom Selected Item table is populated", batchOk ? "Batch row ticked: \"" + batch + "\"; " + xl.lastBatchState : "Problem — " + xl.problems, batchOk ? "PASS" : "FAIL");
        if (!batchOk) { addSummary("Result", "FAILED — batch: " + xl.problems); return; }

        String sel = xl.tickSelectedItemRow();
        boolean selOk = !sel.isEmpty() && xl.ok();
        step("In Selected Item, tick the checkbox for the row", "Tick the checkbox of the row in the bottom Selected Item table", "The row is ticked (OK rejects with \"Please Select Item !!!\" otherwise)",
                selOk ? "Ticked \"" + sel + "\"" : "Problem — " + xl.problems, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED — selected item: " + xl.problems); return; }

        boolean okOk = xl.clickOk();
        String row = xl.gridRowText();
        step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the item is added to the transfer grid",
                okOk ? "Dialog closed; the grid shows: " + row : "Problem — " + xl.problems, okOk ? "PASS" : "FAIL");
        if (!okOk) { addSummary("Result", "FAILED — OK: " + xl.problems); return; }

        // the quantity must not exceed the indent's pending quantity; the grid columns are Sr.No, Code, Name, Batch, Expiry, Indent Qty, Pending Indent Qty, Available Stock, Quantity ...
        String qty = QTY;
        List<String> cells = xl.gridCells();
        try {
            double pending = Double.parseDouble(cells.get(6)), want = Double.parseDouble(QTY);
            if (want > pending && pending >= 1) qty = String.valueOf((long) pending);
        } catch (Exception ignore) { }
        boolean qtyOk = xl.enterQuantity(qty);
        step("Enter quantity", "Enter the Quantity (" + qty + ")", "The quantity is accepted and the totals update",
                qtyOk ? "Quantity " + xl.lastQty + " entered — " + xl.lastTotals : "Problem — " + xl.problems, qtyOk ? "PASS" : "FAIL");
        if (!qtyOk) { addSummary("Result", "FAILED — quantity: " + xl.problems); return; }

        String toast = xl.saveAndGetToast();
        boolean ok = xl.ok() && xl.saveSucceeded();
        step("Click Save", "Click Save" + (xl.lastDialog.isEmpty() ? "" : " and answer YES to \"" + xl.lastDialog + "\""), "The transfer is saved",
                xl.lastDialog.isEmpty() ? "Save went straight through (no confirmation)" : "Confirmation: \"" + xl.lastDialog + "\" — YES clicked", xl.problems.isEmpty() ? "PASS" : "FAIL");
        step("Verify success toast message", "Read the toast shown after the save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\" and the server confirmed it (ResultStatus 1" + (xl.lastSaveMessage.isEmpty() ? "" : ", \"" + xl.lastSaveMessage + "\"")
                        + (xl.lastTransNo.isEmpty() ? "" : ", transfer no. " + xl.lastTransNo) + ")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + xl.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + xl.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Requesting store -> Issuing store", xl.lastRequesting + " -> " + xl.lastIssuing);
        addSummary("Store pairs tried", String.join(" | ", attempts));
        addSummary("Indent / item", xl.lastIndent + " — " + xl.lastIndentItem);
        addSummary("Transfer grid row", xl.lastRow);
        addSummary("Quantity / totals", qty + " — " + xl.lastTotals);
        addSummary("Transfer no.", xl.lastTransNo.isEmpty() ? "(none — not saved)" : xl.lastTransNo);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
