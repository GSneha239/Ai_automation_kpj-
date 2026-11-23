package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.google.gson.JsonObject;
import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.PdfReport;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// The page object is also named StoreIndent — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Store Indent</b> ({@code #/CSSDStoreIndentList}): the <b>Manual Indent</b> form ({@code #/add-StoreIndent}) and the <b>Auto Indent</b> screen ({@code #/AutoIndent}).
 *
 * <p><b>Manual Indent</b></p>
 * <ol>
 *   <li>Click <b>Manual Indent</b> ("add a new tab - Manual Indent": the screen has no such tab, the button opens the form).</li>
 *   <li>Select the <b>Requesting Store</b> (Pharmacy (Central)) and the <b>Issuing Store</b> (Pharmacy Main Store).</li>
 *   <li><b>Get Items</b>: in the Item Search dialog select the <b>Store</b>, Search (the dialog lists nothing before), tick the row with the most <b>available stock</b>, <b>OK</b>.</li>
 *   <li>Check the item in the grid, in a LOOP: its <b>Minimum Indent Quantity</b> must allow a Unit Quantity of 1 (1 or less); if it does not, delete the item, Get Items again and take the next item with the most stock.
 *       (The grid's Indent Quantity column is 0 for every item until a Unit Quantity is typed, so it can only be checked after step 4 - it must then be 1 or more.)</li>
 *   <li>Tick the item's row, enter the <b>Unit Quantity</b> (1) and the <b>Remarks</b>, <b>Save</b>, verify the success toast, click <b>OK</b> in the Indent Number dialog, verify the report opened in another tab.</li>
 * </ol>
 *
 * <p><b>Auto Indent</b> ("add another tab - Auto Indent": again a button, not a tab)</p>
 * <ol>
 *   <li>Click <b>Auto Indent</b>; select the <b>Requesting Store*</b> and the <b>Issuing Store*</b>; click <b>Search</b> (lists the items below their reorder level of the requesting store).</li>
 *   <li>Tick one row, enter its <b>Unit Quantity</b> (1) - the row's Indent Quantity, pre-filled with the reorder quantity, becomes the quantity typed - enter the <b>Remarks</b>, click <b>Save Indent</b>,
 *       verify the success toast and the report opened in another tab (this screen has no Indent Number dialog).</li>
 * </ol>
 *
 * <p>Options: {@code -Dsi.only=manual|auto} (run one section), {@code -Dsi.dialogStore=} (the Manual Indent dialog's Store*; default the Issuing Store), {@code -Dsi.firstCode=} (item code the first Manual Indent
 * attempt must take - used to exercise the loop), {@code -Dsi.qty=}.</p>
 *
 * <p>&#9888; A successful run creates TWO REAL Store Indents (one manual, one auto) in the target environment.</p>
 */
public class StoreIndent extends DevHisBase {

    public StoreIndent() { super("AncillaryServices_CSSD_StoreIndent"); }

    public static void main(String[] args) {
        StoreIndent t = new StoreIndent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    private static final String SI = "com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent";

    private final String requesting = System.getProperty("si.requesting", "Pharmacy (Central)");
    private final String issuing = System.getProperty("si.issuing", "Pharmacy Main Store");
    private final String qty = System.getProperty("si.qty", "1");

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Store Indent", "Ancillary Services > CSSD > Store Indent",
                "&#9888; Creates REAL Store Indents (one manual, one auto). Manual Indent: Requesting Store (Pharmacy (Central)), Issuing Store (Pharmacy Main Store), Get Items, select the store, tick the row with the most stock, OK; "
                        + "loop until the item can take a Unit Quantity of 1 (delete and Get Items again otherwise); tick the item row, Unit Quantity 1, Remarks, Save; verify the success toast; OK in the Indent Number dialog; "
                        + "verify the report opened in another tab. Auto Indent: Requesting Store, Issuing Store, Search, tick a row, Unit Quantity 1, Remarks, Save Indent; verify the success toast and the report in another tab.");

        final String only = System.getProperty("si.only", "").toLowerCase();

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent si = new com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent(page);

        boolean on = si.navigateViaMenu();
        step("Open Store Indent", "Click Ancillary Services -> CSSD -> Store Indent", "The Store Indent screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Store Indent not reached"); return; }

        if (!"auto".equals(only)) manualIndent(si);
        if (!"manual".equals(only)) autoIndent(si);
    }

    // =====================================================================================================================
    // Manual Indent
    // =====================================================================================================================

    private void manualIndent(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent si) {
        final String dialogStore = System.getProperty("si.dialogStore", issuing);
        final String firstCode = System.getProperty("si.firstCode", "");
        final int maxAttempts = 8;

        // 1) Manual Indent
        boolean realTab = si.hasManualIndentTab();
        String control = si.manualIndentControl();
        boolean opened = si.clickManualIndent();
        step("Click Manual Indent", "Add a new tab - Manual Indent; click Manual Indent", "The Manual Indent form is shown",
                opened ? (realTab ? "Manual Indent tab clicked" : "The screen has no separate Manual Indent tab - Manual Indent is the " + control + "; clicked it, the form opened at " + page.url())
                       : "Problem — " + si.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[Manual] Result", "FAILED — the Manual Indent form did not open"); return; }

        // 2) the two stores
        String req = si.selectRequestingStore(requesting);
        step("Select requesting store", "Select Requesting Store \"" + requesting + "\"", "The requesting store is selected",
                !req.isEmpty() ? "Requesting Store \"" + req + "\" selected (of " + si.options("StoreIndent.SearchStoreID").size() + " stores); the screen's model holds it" : "Problem — " + si.problems, !req.isEmpty() ? "PASS" : "FAIL");
        if (req.isEmpty()) { addSummary("[Manual] Result", "FAILED — requesting store: " + si.problems); return; }
        String iss = si.selectIssuingStore(issuing);
        step("Select issuing store", "Select Issuing Store \"" + issuing + "\"", "The issuing store is selected",
                !iss.isEmpty() ? "Issuing Store \"" + iss + "\" selected (of " + si.options("StoreIndent.ToStore").size() + " stores); the screen's model holds it" : "Problem — " + si.problems, !iss.isEmpty() ? "PASS" : "FAIL");
        if (iss.isEmpty()) { addSummary("[Manual] Result", "FAILED — issuing store: " + si.problems); return; }

        // 3) Get Items -> store -> Search -> tick the row with the most stock -> OK, checked in a loop
        Set<String> tried = new LinkedHashSet<>();
        List<String> attempts = new ArrayList<>();
        int acceptIdx = -1;
        JsonObject acceptRow = null;
        String chosenStore = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean first = attempt == 1;
            boolean got = si.clickGetItems();
            if (first) step("Click Get Items", "Click Get Items", "The Item Search dialog opens",
                    got ? "The Item Search dialog opened" : "Problem — " + si.problems, got ? "PASS" : "FAIL");
            if (!got) { addSummary("[Manual] Result", "FAILED — Get Items: " + si.problems); return; }

            String ds = si.selectDialogStore(dialogStore, issuing, requesting);
            chosenStore = ds;
            if (first) step("Select store", "In the Item Search dialog select the Store* \"" + dialogStore + "\"", "The store is selected",
                    !ds.isEmpty() ? "Store* \"" + ds + "\" selected (of " + si.options("ItemSearch.SearchStoreID").size() + " stores; it started on the Requesting Store)" : "Problem — " + si.problems, !ds.isEmpty() ? "PASS" : "FAIL");
            if (ds.isEmpty()) { addSummary("[Manual] Result", "FAILED — dialog store: " + si.problems); return; }

            int rows = si.clickDialogSearch();
            List<com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item> items = si.dialogItems();
            List<com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item> top = new ArrayList<>(items);
            top.sort(Comparator.comparingDouble((com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item i) -> i.from).reversed());
            if (first) step("Click Search", "Click Search (choosing the store does not list items)", "The items of the store are listed",
                    rows > 0 ? rows + " item(s) listed (" + si.dialogPager() + "); most stock first: " + top.stream().limit(3).map(Object::toString).toList() : "Problem — no items were listed " + si.problems, rows > 0 ? "PASS" : "FAIL");
            if (rows == 0) { addSummary("[Manual] Result", "FAILED — the dialog listed no items"); return; }

            com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item pick = si.pickMostStock(tried, first ? firstCode : "", items);
            if (pick == null) { attempts.add("attempt " + attempt + ": no further item with stock to try"); si.closeDialog(); break; }
            tried.add(pick.code);
            boolean ticked = si.tickItem(pick.code);
            if (first) step("Tick the checkbox of the row with the most available stock", "Tick the checkbox of the item with the most Available Stock",
                    "The row is ticked",
                    ticked ? "Ticked " + pick + (firstCode.isEmpty() ? " - the most stock of the " + items.size() + " items listed" : " (pinned with -Dsi.firstCode)") : "Problem — " + si.problems, ticked ? "PASS" : "FAIL");
            if (!ticked) { addSummary("[Manual] Result", "FAILED — tick: " + si.problems); return; }

            int gridRows = si.clickDialogOk();
            if (first) step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the item is added to the grid",
                    gridRows > 0 ? "OK clicked; the grid lists " + gridRows + " item(s)" : "Problem — " + si.problems, gridRows > 0 ? "PASS" : "FAIL");
            if (gridRows <= 0) { addSummary("[Manual] Result", "FAILED — OK: " + si.problems); return; }

            // find the grid row of this item
            List<JsonObject> grid = si.gridRows();
            int idx = -1;
            for (int i = 0; i < grid.size(); i++) if (pick.code.equalsIgnoreCase(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(grid.get(i), "Item Code"))) idx = i;
            if (idx < 0) { attempts.add("attempt " + attempt + ": " + pick.code + " is not in the grid after OK"); break; }
            JsonObject row = grid.get(idx);
            double minIndent = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Minimum Indent Quantity"));
            String desc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeRow(row);
            if (!Double.isNaN(minIndent) && minIndent <= 1) {
                attempts.add("attempt " + attempt + ": " + pick.code + " accepted - Minimum Indent Quantity " + (long) minIndent + " allows a Unit Quantity of 1");
                acceptIdx = idx;
                acceptRow = row;
                break;
            }
            boolean deleted = si.deleteRow(idx);
            attempts.add("attempt " + attempt + ": " + pick.code + " REJECTED - Minimum Indent Quantity " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Minimum Indent Quantity")
                    + " is above 1 (" + desc + ") - " + (deleted ? "deleted it, Get Items again" : "could not be deleted: " + si.problems));
            if (!deleted) break;
        }

        boolean accepted = acceptIdx >= 0;
        step("Check the indent quantity of the item", "Check the item in the grid: Minimum Indent Quantity allows a Unit Quantity of 1; otherwise delete it, click Get Items again and take a different item (loop)",
                "The grid holds an item that can be indented with a quantity of 1",
                accepted ? "PASSES: " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeRow(acceptRow) + ". The Indent Quantity column is " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(acceptRow, "Indent Quantity")
                        + " for every item until a Unit Quantity is typed, so it is checked again after the quantity. Attempts: " + attempts
                        : "FAILS: no item with a Minimum Indent Quantity of 1 or less was found in " + attempts.size() + " attempt(s): " + attempts,
                accepted ? "PASS" : "FAIL");
        if (!accepted) { addSummary("[Manual] Attempts", String.valueOf(attempts)); addSummary("[Manual] Result", "FAILED — no item that can be indented with a quantity of 1"); return; }
        final int idx = acceptIdx;
        final String itemCode = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(acceptRow, "Item Code");
        final String itemDesc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeRow(acceptRow);

        // 4) tick the item's row
        boolean rowTick = si.tickRow(idx);
        step("Tick the checkbox to select the item row", "Tick the Select checkbox of the item row", "The item row is selected",
                rowTick ? "Row " + (idx + 1) + " (" + itemCode + ") ticked; the row's data holds it" : "Problem — " + si.problems, rowTick ? "PASS" : "FAIL");
        if (!rowTick) { addSummary("[Manual] Result", "FAILED — row tick: " + si.problems); return; }

        // 5) unit quantity
        boolean qtyOk = si.enterUnitQuantity(idx, qty);
        JsonObject afterQty = si.gridRows().size() > idx ? si.gridRows().get(idx) : null;
        double indentQty = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity"));
        boolean indentOk = qtyOk && !Double.isNaN(indentQty) && indentQty >= 1;
        step("Enter unit quantity", "Enter Unit Quantity " + qty, "The quantity is accepted and the Indent Quantity becomes " + qty + " or more",
                indentOk ? "Unit Quantity " + qty + " entered; the grid's Indent Quantity is now " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity") + " (it was "
                        + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(acceptRow, "Indent Quantity") + ")"
                        : "Problem — " + (qtyOk ? "the Indent Quantity is " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity") + " after the quantity" : si.problems.toString()),
                indentOk ? "PASS" : "FAIL");
        if (!indentOk) { addSummary("[Manual] Result", "FAILED — unit quantity: " + si.problems); return; }

        // 6) remarks
        String remarks = "QA auto indent remarks - CSSD Store Indent " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = si.enterRemarks(remarks);
        step("Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + si.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("[Manual] Result", "FAILED — remarks: " + si.problems); return; }

        // 7) Save
        int tabsBefore = si.clickSave();
        boolean clicked = si.ok();
        String toast = clicked ? si.awaitToast(25000) : "";
        String uomNote = "";
        String tl = toast.toLowerCase();
        if (clicked && !si.saveSucceeded() && !si.savedByServer() && !toast.isEmpty() && (tl.contains("quantity") || tl.contains("uom") || tl.contains("unit"))) {
            // a validation message and nothing saved: choose the row's UOM (the grid starts with none) and save again
            String uom = si.selectUom(idx, "");
            if (!uom.isEmpty()) {
                uomNote = "The first Save answered \"" + toast + "\" (nothing saved), so the row's UOM \"" + uom + "\" was selected and Save clicked again. ";
                tabsBefore = si.clickSave();
                clicked = si.ok();
                toast = clicked ? si.awaitToast(25000) : "";
            }
        }
        step("Click Save", "Click Save", "The indent is saved",
                clicked ? uomNote + "Save clicked" : "Problem — " + si.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("[Manual] Result", "FAILED — Save: " + si.problems); return; }

        boolean success = si.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (si.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + si.lastSaveMessage.trim() + "\"")
                        + (si.lastTransNo.isEmpty() ? "" : " (indent " + si.lastTransNo + ")")
                      : si.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the indent (ResultStatus 1" + (si.lastTransNo.isEmpty() ? "" : ", " + si.lastTransNo) + ")"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + si.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + si.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 8) Indent Number dialog
        String dialogText = si.awaitIndentNumberDialog(15000);
        boolean dialogShown = !dialogText.isEmpty();
        boolean dialogOk = dialogShown && si.clickIndentNumberOk();
        step("Click OK in the Indent Number dialog", "Click OK in the Indent Number dialog popup", "The dialog shows the indent number and closes",
                dialogOk ? "The Indent Number dialog showed \"" + dialogText + "\"" + (si.lastIndentNumbers.isEmpty() ? "" : " (indent number " + si.lastIndentNumbers + ")") + " and was closed with OK"
                        : dialogShown ? "Problem — " + si.problems : "The Indent Number dialog never appeared" + (success ? "" : " (the save was not confirmed)"),
                dialogOk ? "PASS" : "FAIL");

        // 9) the report in another tab (the save itself opens it)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        addSummary("[Manual] Requesting / Issuing store", si.lastRequestingStore + " / " + si.lastIssuingStore);
        addSummary("[Manual] Item Search store", chosenStore);
        addSummary("[Manual] Attempts", String.valueOf(attempts));
        addSummary("[Manual] Item", itemDesc);
        addSummary("[Manual] Unit Quantity / Indent Quantity", qty + " / " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity"));
        addSummary("[Manual] Remarks", remarks);
        addSummary("[Manual] Server answer", si.savedByServer() ? "saved (ResultStatus 1" + (si.lastTransNo.isEmpty() ? "" : ", " + si.lastTransNo) + (si.lastResponseId.isEmpty() ? "" : ", indent id " + si.lastResponseId) + ", \"" + si.lastSaveMessage.trim() + "\")"
                : "(no save confirmed" + (si.lastSaveMessage.isEmpty() ? "" : ": " + si.lastSaveMessage) + ")");
        addSummary("[Manual] Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("[Manual] Indent Number dialog", dialogShown ? dialogText : "(did not appear)");
        addSummary("[Manual] Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("[Manual] Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (si.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }

    // =====================================================================================================================
    // Auto Indent
    // =====================================================================================================================

    private void autoIndent(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent si) {
        final String P = "[Auto Indent] ";

        // back to the list (the Manual Indent form is still open after its Save)
        if (!si.onScreen()) {
            boolean back = si.navigateViaMenu();
            step(P + "Return to the Store Indent list", "Click Ancillary Services -> CSSD -> Store Indent", "The Store Indent list is shown",
                    back ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", back ? "PASS" : "FAIL");
            if (!back) { addSummary("[Auto] Result", "FAILED — the Store Indent list was not reached"); return; }
        }

        // 1) Auto Indent
        boolean realTab = si.hasAutoIndentTab();
        String control = si.autoIndentControl();
        boolean opened = si.clickAutoIndent();
        step(P + "Click Auto Indent", "Add another tab - Auto Indent; click Auto Indent", "The Auto Indent screen is shown",
                opened ? (realTab ? "Auto Indent tab clicked" : "The screen has no separate Auto Indent tab - Auto Indent is the " + control + "; clicked it, the screen opened at " + page.url())
                       : "Problem — " + si.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[Auto] Result", "FAILED — the Auto Indent screen did not open"); return; }

        // 2) the two stores
        String req = si.selectAutoRequestingStore(requesting);
        step(P + "Select requesting store", "Select Requesting Store* \"" + requesting + "\"", "The requesting store is selected",
                !req.isEmpty() ? "Requesting Store \"" + req + "\" selected (of " + si.options("AutoIndent.FromStoreID").size() + " stores); the screen's model holds it" : "Problem — " + si.problems, !req.isEmpty() ? "PASS" : "FAIL");
        if (req.isEmpty()) { addSummary("[Auto] Result", "FAILED — requesting store: " + si.problems); return; }
        String iss = si.selectAutoIssuingStore(issuing);
        step(P + "Select issuing store", "Select Issuing Store* \"" + issuing + "\"", "The issuing store is selected",
                !iss.isEmpty() ? "Issuing Store \"" + iss + "\" selected (of " + si.options("AutoIndent.ToStoreID").size() + " stores); the screen's model holds it" : "Problem — " + si.problems, !iss.isEmpty() ? "PASS" : "FAIL");
        if (iss.isEmpty()) { addSummary("[Auto] Result", "FAILED — issuing store: " + si.problems); return; }

        // 3) Search
        int rows = si.clickAutoSearch();
        step(P + "Click Search", "Click Search", "Records are listed in Auto Indent Details",
                rows > 0 ? rows + " item(s) listed in Auto Indent Details (" + si.autoTotalRecords() + ")" : "Problem — no items were listed " + si.problems, rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("[Auto] Result", "FAILED — Search listed no items"); return; }

        // 4) one row: the first whose Unit Quantity box is enabled and whose Minimum Indent Quantity allows a quantity of 1
        List<JsonObject> grid = si.gridRows();
        int idx = -1;
        for (int i = 0; i < grid.size() && idx < 0; i++) {
            double min = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(grid.get(i), "Minimum Indent Quantity"));
            boolean disabled = grid.get(i).has("__unitDisabled") && "true".equals(grid.get(i).get("__unitDisabled").getAsString());
            if (!disabled && (Double.isNaN(min) || min <= 1)) idx = i;
        }
        if (idx < 0) { step(P + "Tick the checkbox to select one of the rows", "Tick the checkbox of one row", "A row is selected", "No row can take a Unit Quantity of 1 among the " + grid.size() + " listed", "FAIL"); addSummary("[Auto] Result", "FAILED — no usable row"); return; }
        JsonObject row = grid.get(idx);
        String rowDesc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeAutoRow(row);
        boolean ticked = si.tickRow(idx);
        step(P + "Tick the checkbox to select one of the rows", "Tick the checkbox of one row", "The row is selected",
                ticked ? "Row " + (idx + 1) + " ticked (the first of " + grid.size() + " that takes a quantity of 1): " + rowDesc : "Problem — " + si.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("[Auto] Result", "FAILED — row tick: " + si.problems); return; }

        // 5) unit quantity
        boolean qtyOk = si.enterUnitQuantity(idx, qty);
        List<JsonObject> after = si.gridRows();
        JsonObject afterRow = after.size() > idx ? after.get(idx) : null;
        String indentBefore = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Indent Quantity");
        String indentAfter = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterRow, "Indent Quantity");
        double conv = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterRow, "Conversion Factor"));
        double indentNow = num(indentAfter);
        boolean indentOk = qtyOk && !Double.isNaN(indentNow) && indentNow >= 1;
        step(P + "Enter unit quantity", "Enter Unit Quantity " + qty, "The quantity is accepted and the row's Indent Quantity follows it",
                indentOk ? "Unit Quantity " + qty + " entered; the row's Indent Quantity is now " + indentAfter + " (it was " + indentBefore + ", the reorder quantity" + (Double.isNaN(conv) ? "" : "; Unit Quantity x Conversion Factor " + qty + " x " + (long) conv + ")") + (Double.isNaN(conv) ? ")" : "")
                        : "Problem — " + (qtyOk ? "the Indent Quantity is " + indentAfter + " after the quantity" : si.problems.toString()),
                indentOk ? "PASS" : "FAIL");
        if (!indentOk) { addSummary("[Auto] Result", "FAILED — unit quantity: " + si.problems); return; }

        // 6) remarks
        String remarks = "QA auto indent remarks - CSSD Auto Indent " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = si.enterRemarks("AutoIndent.Remarks", remarks);
        step(P + "Enter remark", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + si.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("[Auto] Result", "FAILED — remarks: " + si.problems); return; }

        // 7) Save Indent
        int tabsBefore = si.clickSaveIndent();
        boolean clicked = si.ok();
        String toast = clicked ? si.awaitToast(25000) : "";
        step(P + "Click Save Indent", "Click Save Indent", "The indent is saved", clicked ? "Save Indent clicked" : "Problem — " + si.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("[Auto] Result", "FAILED — Save Indent: " + si.problems); return; }

        boolean success = si.saveSucceeded();
        step(P + "Verify success toast message", "Read the toast shown after Save Indent", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (si.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + si.lastSaveMessage.trim() + "\"")
                        + (si.lastTransNo.isEmpty() ? "" : " (indent " + si.lastTransNo + ")")
                      : si.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the indent (ResultStatus 1" + (si.lastTransNo.isEmpty() ? "" : ", " + si.lastTransNo) + ")"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + si.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + si.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 8) the report in another tab (the save itself opens it)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, P + "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, P + "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        addSummary("[Auto] Requesting / Issuing store", si.lastRequestingStore + " / " + si.lastIssuingStore);
        addSummary("[Auto] Items listed", rows + " (" + si.autoTotalRecords() + ")");
        addSummary("[Auto] Item", rowDesc);
        addSummary("[Auto] Unit Quantity / Indent Quantity", qty + " / " + indentAfter + " (was " + indentBefore + ")");
        addSummary("[Auto] Remarks", remarks);
        addSummary("[Auto] Server answer", si.savedByServer() ? "saved (ResultStatus 1" + (si.lastTransNo.isEmpty() ? "" : ", " + si.lastTransNo) + (si.lastResponseId.isEmpty() ? "" : ", indent id " + si.lastResponseId) + ", \"" + si.lastSaveMessage.trim() + "\")"
                : "(no save confirmed" + (si.lastSaveMessage.isEmpty() ? "" : ": " + si.lastSaveMessage) + ")");
        addSummary("[Auto] Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("[Auto] Report", pdf.url.isEmpty() ? "(none)" : pdf.url);
        addSummary("[Auto] Result", success ? toast : (toast.isEmpty() ? "Not confirmed" : (si.savedByServer() ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
