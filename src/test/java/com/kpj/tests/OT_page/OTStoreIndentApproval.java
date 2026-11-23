package com.kpj.tests.OT_page;

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

// The page object is also named OTStoreIndentApproval — referenced by its fully-qualified name.

/**
 * OT &gt; <b>OT Store Indent Approval</b> ({@code #/OTStoreIndentList}) - the Manual Indent form of the shared Store Indent controller.
 *
 * <ol>
 *   <li>Click <b>Manual Indent</b> ("add a new tab - Manual Indent": the screen has no such tab, the button opens the form).</li>
 *   <li>Select the <b>Requesting Store</b> (Pharmacy Main Store) and the <b>Issuing Store</b> (Pharmacy (Central)).</li>
 *   <li><b>Get Items</b>; in the Item Search dialog click <b>Search</b> (its Store* is left as it is: the Requesting Store) and verify the rows appear.</li>
 *   <li>Tick an item whose <b>available stock is at least 1</b> (the one with the most), click <b>OK</b>. The item must take a Unit Quantity of 1 (Minimum Indent Quantity of 1 or less); otherwise it is deleted and
 *       the next one is taken.</li>
 *   <li>Enter the <b>Unit Quantity</b> (1) and the <b>Remarks</b>, click <b>Save</b>, verify the success toast, verify the report opened in another tab, click <b>OK</b> in the Indent Number dialog.</li>
 * </ol>
 *
 * <p><b>Auto Indent</b> ("add another tab - Auto Indent": again a button, not a tab): click <b>Auto Indent</b>; select the <b>Requesting Store*</b> (Pharmacy Main Store) and the <b>Issuing Store*</b> (Pharmacy (Central));
 * <b>Search</b> (choosing the stores lists nothing) and verify the rows appear in <b>Auto Indent Details</b>; tick a row whose <b>Available Qty is at least 1</b>; enter the <b>Unit Quantity</b> (1) and the <b>Remarks</b>;
 * click <b>Save Indent</b>; verify the success toast and the report opened in another tab (this screen has no Indent Number dialog).</p>
 *
 * <p>The grid row's own Select tick is not part of the spec: Save is tried without it, and when the screen refuses (nothing saved) the row is ticked and Save clicked again - the report says so.</p>
 *
 * <p>Options: {@code -Dot.only=manual|auto} (run one section), {@code -Dot.requesting=}, {@code -Dot.issuing=}, {@code -Dot.qty=}.</p>
 *
 * <p>&#9888; A successful run creates TWO REAL Store Indents (one manual, one auto) in the target environment.</p>
 */
public class OTStoreIndentApproval extends DevHisBase {

    public OTStoreIndentApproval() { super("OT_OTStoreIndentApproval"); }

    public static void main(String[] args) {
        OTStoreIndentApproval t = new OTStoreIndentApproval();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    private final String requesting = System.getProperty("ot.requesting", "Pharmacy Main Store");
    private final String issuing = System.getProperty("ot.issuing", "Pharmacy (Central)");
    private final String qty = System.getProperty("ot.qty", "1");

    @Override
    protected void body() {
        meta("Operation Theatre - OT Store Indent Approval", "OT > OT Store Indent Approval",
                "&#9888; Creates REAL Store Indents (one manual, one auto). Manual Indent: Requesting Store (Pharmacy Main Store), Issuing Store (Pharmacy (Central)), Get Items, Search (rows appear), tick an item with available stock of at least 1, OK; "
                        + "Unit Quantity; Remarks; Save; verify the success toast; verify the report opened in another tab; OK in the Indent Number dialog. "
                        + "Auto Indent: Requesting Store, Issuing Store, Search, verify the rows in Auto Indent Details, tick a row with Available Qty of at least 1, Unit Quantity 1, Remarks, Save Indent; verify the success toast and the report in another tab.");

        final String only = System.getProperty("ot.only", "").toLowerCase();

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.OT_page.OTStoreIndentApproval si = new com.kpj.pages.OT_page.OTStoreIndentApproval(page);

        boolean on = si.navigateViaMenu();
        step("Open OT Store Indent Approval", "Click OT -> OT Store Indent Approval", "The OT Store Indent Approval list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — OT Store Indent Approval not reached"); return; }

        if (!"auto".equals(only)) manualIndent(si);
        if (!"manual".equals(only)) autoIndent(si);
    }

    // =====================================================================================================================
    // Manual Indent
    // =====================================================================================================================

    private void manualIndent(com.kpj.pages.OT_page.OTStoreIndentApproval si) {
        final int maxAttempts = 8;

        // 1) Manual Indent
        boolean realTab = si.hasManualIndentTab();
        String control = si.manualIndentControl();
        boolean opened = si.clickManualIndent();
        step("Click Manual Indent", "Add a new tab - Manual Indent; click Manual Indent", "The Manual Indent form is shown",
                opened ? (realTab ? "Manual Indent tab clicked" : "The screen has no separate Manual Indent tab - Manual Indent is the " + control + "; clicked it, the form (heading \"" + si.formHeading() + "\") opened at " + page.url())
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

        // 3) Get Items -> Search -> rows -> tick an item with stock -> OK, checked in a loop
        Set<String> tried = new LinkedHashSet<>();
        List<String> attempts = new ArrayList<>();
        int acceptIdx = -1;
        JsonObject acceptRow = null;
        String dialogStore = "";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            boolean first = attempt == 1;
            boolean got = si.clickGetItems();
            if (first) step("Click Get Items", "Click Get Items", "The Item Search dialog opens",
                    got ? "The Item Search dialog opened" : "Problem — " + si.problems, got ? "PASS" : "FAIL");
            if (!got) { addSummary("[Manual] Result", "FAILED — Get Items: " + si.problems); return; }

            dialogStore = si.dialogStoreShown();
            int rows = si.clickDialogSearch();
            List<com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item> items = si.dialogItems();
            List<com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item> top = new ArrayList<>(items);
            top.sort(Comparator.comparingDouble((com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item i) -> i.from).reversed());
            if (first) {
                step("Click Search", "In the Item Search dialog click Search", "The search is run for the dialog's store",
                        rows > 0 ? "Search clicked for Store* \"" + dialogStore + "\" (the Requesting Store, left as it started)" : "Problem — " + si.problems, rows > 0 ? "PASS" : "FAIL");
                step("Verify rows of data appear", "Verify the rows of data are populated in the Item Search dialog", "Item rows are listed",
                        rows > 0 ? rows + " item row(s) listed (" + si.dialogPager() + "); most stock first: " + top.stream().limit(3).map(Object::toString).toList() : "FAILS — no row was listed " + si.problems, rows > 0 ? "PASS" : "FAIL");
            }
            if (rows == 0) { addSummary("[Manual] Result", "FAILED — the dialog listed no items"); return; }

            com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.Item pick = si.pickMostStock(tried, "", items);
            if (pick == null) { attempts.add("attempt " + attempt + ": no further item with an available stock of at least 1"); si.closeDialog(); break; }
            tried.add(pick.code);
            boolean ticked = si.tickItem(pick.code);
            if (first) step("Tick the checkbox of an item with available stock", "Tick the checkbox of an item whose Available Stock is at least 1",
                    "The row is ticked",
                    ticked ? "Ticked " + pick + " - available stock " + (long) pick.from + " (at least 1; the most of the " + items.size() + " items listed)" : "Problem — " + si.problems, ticked ? "PASS" : "FAIL");
            if (!ticked) { addSummary("[Manual] Result", "FAILED — tick: " + si.problems); return; }

            int gridRows = si.clickDialogOk();
            if (first) step("Click OK", "Click OK in the Item Search dialog", "The dialog closes and the item is added to the grid",
                    gridRows > 0 ? "OK clicked; the grid lists " + gridRows + " item(s)" : "Problem — " + si.problems, gridRows > 0 ? "PASS" : "FAIL");
            if (gridRows <= 0) { addSummary("[Manual] Result", "FAILED — OK: " + si.problems); return; }

            List<JsonObject> grid = si.gridRows();
            int idx = -1;
            for (int i = 0; i < grid.size(); i++) if (pick.code.equalsIgnoreCase(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(grid.get(i), "Item Code"))) idx = i;
            if (idx < 0) { attempts.add("attempt " + attempt + ": " + pick.code + " is not in the grid after OK"); break; }
            JsonObject row = grid.get(idx);
            double minIndent = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Minimum Indent Quantity"));
            double avl = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Avl Quantity"));
            String desc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeRow(row);
            if (!Double.isNaN(minIndent) && minIndent <= 1 && avl >= 1) {
                attempts.add("attempt " + attempt + ": " + pick.code + " accepted - Avl Quantity " + (long) avl + ", Minimum Indent Quantity " + (long) minIndent + " allows a Unit Quantity of 1");
                acceptIdx = idx;
                acceptRow = row;
                break;
            }
            boolean deleted = si.deleteRow(idx);
            attempts.add("attempt " + attempt + ": " + pick.code + " REJECTED (" + desc + ") - " + (deleted ? "deleted it, Get Items again" : "could not be deleted: " + si.problems));
            if (!deleted) break;
        }
        if (acceptIdx < 0) { addSummary("[Manual] Attempts", String.valueOf(attempts)); addSummary("[Manual] Result", "FAILED — no item with stock that takes a quantity of 1: " + attempts); step("Select an item", "Select an item with stock", "An item is in the grid", "FAILS: " + attempts, "FAIL"); return; }
        final int idx = acceptIdx;
        final String itemCode = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(acceptRow, "Item Code");
        final String itemDesc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeRow(acceptRow);

        // 4) unit quantity
        boolean qtyOk = si.enterUnitQuantity(idx, qty);
        JsonObject afterQty = si.gridRows().size() > idx ? si.gridRows().get(idx) : null;
        double indentQty = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity"));
        boolean indentOk = qtyOk && !Double.isNaN(indentQty) && indentQty >= 1;
        step("Enter unit quantity", "Enter Unit Quantity " + qty, "The quantity is accepted and the Indent Quantity becomes " + qty + " or more",
                indentOk ? "Unit Quantity " + qty + " entered for " + itemCode + "; the grid's Indent Quantity is now " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity")
                        + " (it was " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(acceptRow, "Indent Quantity") + " until a quantity is typed)"
                        : "Problem — " + (qtyOk ? "the Indent Quantity is " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity") + " after the quantity" : si.problems.toString()),
                indentOk ? "PASS" : "FAIL");
        if (!indentOk) { addSummary("[Manual] Result", "FAILED — unit quantity: " + si.problems); return; }

        // 5) remarks
        String remarks = "QA auto indent remarks - OT Store Indent Approval " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = si.enterRemarks(remarks);
        step("Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + si.problems, remarksOk ? "PASS" : "FAIL");
        if (!remarksOk) { addSummary("[Manual] Result", "FAILED — remarks: " + si.problems); return; }

        // 6) Save (the row's own Select tick is not in the spec: only when the screen refuses is it ticked)
        int tabsBefore = si.clickSave();
        boolean clicked = si.ok();
        String toast = clicked ? si.awaitToast(25000) : "";
        String tickNote = "";
        if (clicked && !si.saveSucceeded() && !si.savedByServer() && !toast.isEmpty()) {
            boolean t = si.tickRow(idx);
            if (t) {
                tickNote = "The first Save answered \"" + toast + "\" (nothing saved), so the grid row's Select tick was ticked and Save clicked again. ";
                tabsBefore = si.clickSave();
                clicked = si.ok();
                toast = clicked ? si.awaitToast(25000) : "";
            }
        }
        step("Click Save", "Click Save", "The indent is saved", clicked ? tickNote + "Save clicked" : "Problem — " + si.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary("[Manual] Result", "FAILED — Save: " + si.problems); return; }

        boolean success = si.saveSucceeded();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                success ? "PASSES because the toast is a success message: \"" + toast + "\"" + (si.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + si.lastSaveMessage.trim() + "\"")
                        + (si.lastTransNo.isEmpty() ? "" : " (indent " + si.lastTransNo + ")")
                      : si.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the indent (ResultStatus 1" + (si.lastTransNo.isEmpty() ? "" : ", " + si.lastTransNo) + ")"
                      : toast.isEmpty() ? "FAILS because no toast appeared — " + si.saveDiagnosis()
                                        : "FAILS because the toast is not a success message: \"" + toast + "\" — " + si.saveDiagnosis(),
                success ? "PASS" : "FAIL");

        // 7) the report in another tab (the save itself opens it)
        PdfReport.Result pdf = PdfReport.capture(page, tabsBefore, 20000);
        boolean reportOk = !pdf.url.isEmpty() && !pdf.blank;
        if (pdf.png != null && pdf.png.length > 0) {
            step(pdf.png, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated",
                    pdf.diagnostics + " The screenshot is the report's first page rendered from the file itself.", reportOk ? "PASS" : "FAIL");
        } else {
            step(page, "Verify report generated in another tab", "Check a report opened in another tab", "A non-blank report is generated", pdf.diagnostics, reportOk ? "PASS" : "FAIL");
        }

        // 8) Indent Number dialog
        String dialogText = si.awaitIndentNumberDialog(15000);
        boolean dialogShown = !dialogText.isEmpty();
        boolean dialogOk = dialogShown && si.clickIndentNumberOk();
        step("Click OK on the Indent Number dialog", "Click OK in the Indent Number dialog popup", "The dialog shows the indent number and closes",
                dialogOk ? "The Indent Number dialog showed \"" + dialogText + "\"" + (si.lastIndentNumbers.isEmpty() ? "" : " (indent number " + si.lastIndentNumbers + ")") + " and was closed with OK"
                        : dialogShown ? "Problem — " + si.problems : "The Indent Number dialog never appeared" + (success ? "" : " (the save was not confirmed)"),
                dialogOk ? "PASS" : "FAIL");

        addSummary("[Manual] Requesting / Issuing store", si.lastRequestingStore + " / " + si.lastIssuingStore);
        addSummary("[Manual] Item Search store", dialogStore);
        addSummary("[Manual] Attempts", String.valueOf(attempts));
        addSummary("[Manual] Item", itemDesc);
        addSummary("[Manual] Unit Quantity / Indent Quantity", qty + " / " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterQty, "Indent Quantity"));
        addSummary("[Manual] Remarks", remarks);
        addSummary("[Manual] Row tick needed", tickNote.isEmpty() ? "no - Save worked without ticking the grid row" : "yes - " + tickNote.trim());
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

    private void autoIndent(com.kpj.pages.OT_page.OTStoreIndentApproval si) {
        final String P = "[Auto Indent] ";
        final String S = "com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent";

        // back to the list (the Manual Indent form is still open after its Save)
        if (!si.onScreen()) {
            boolean back = si.navigateViaMenu();
            step(P + "Return to the OT Store Indent Approval list", "Click OT -> OT Store Indent Approval", "The list is shown",
                    back ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", back ? "PASS" : "FAIL");
            if (!back) { addSummary("[Auto] Result", "FAILED — the list was not reached"); return; }
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

        // 3) Auto Indent Details: choosing the stores lists nothing, Search does
        int before = si.gridRowCount();
        int rows = si.clickAutoSearch();
        step(P + "Click Search", "Click Search (choosing the stores lists nothing: " + before + " row(s) before)", "The items are listed",
                rows > 0 ? "Search clicked; " + rows + " item(s) listed (" + si.autoTotalRecords() + ")" : "Problem — no items were listed " + si.problems, rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("[Auto] Result", "FAILED — Search listed no items"); return; }
        List<JsonObject> grid = si.gridRows();
        step(P + "Verify rows of data appear in Auto Indent Details", "Verify the rows of data are populated in Auto Indent Details", "Item rows are listed",
                rows + " item row(s) listed in Auto Indent Details (" + si.autoTotalRecords() + "); first rows: "
                        + grid.stream().limit(3).map(r -> com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(r, "Item Code") + " " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(r, "Item Name")
                                + " (Available Qty " + com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(r, "Available Qty") + ")").toList(),
                "PASS");

        // 4) one row: the first with an available stock of at least 1, an enabled Unit Quantity box and a Minimum Indent Quantity that allows a quantity of 1
        int idx = -1;
        for (int i = 0; i < grid.size() && idx < 0; i++) {
            double avl = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(grid.get(i), "Available Qty"));
            double min = num(com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(grid.get(i), "Minimum Indent Quantity"));
            boolean disabled = grid.get(i).has("__unitDisabled") && "true".equals(grid.get(i).get("__unitDisabled").getAsString());
            if (!disabled && avl >= 1 && (Double.isNaN(min) || min <= 1)) idx = i;
        }
        if (idx < 0) { step(P + "Tick the checkbox to select one of the items", "Tick the checkbox of an item whose available stock is at least 1", "A row is selected", "No row among the " + grid.size() + " listed has an available stock of at least 1 and takes a quantity of 1", "FAIL"); addSummary("[Auto] Result", "FAILED — no usable row"); return; }
        JsonObject row = grid.get(idx);
        String rowDesc = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.describeAutoRow(row);
        boolean ticked = si.tickRow(idx);
        step(P + "Tick the checkbox to select one of the items", "Tick the checkbox of an item whose available stock is at least 1", "The row is selected",
                ticked ? "Row " + (idx + 1) + " ticked (the first of " + grid.size() + " with an available stock of at least 1): " + rowDesc : "Problem — " + si.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("[Auto] Result", "FAILED — row tick: " + si.problems); return; }

        // 5) unit quantity
        boolean qtyOk = si.enterUnitQuantity(idx, qty);
        List<JsonObject> after = si.gridRows();
        JsonObject afterRow = after.size() > idx ? after.get(idx) : null;
        String indentBefore = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(row, "Indent Quantity");
        String indentAfter = com.kpj.pages.AncillaryServices_page.CSSD_page.StoreIndent.cell(afterRow, "Indent Quantity");
        double indentNow = num(indentAfter);
        boolean indentOk = qtyOk && !Double.isNaN(indentNow) && indentNow >= 1;
        step(P + "Enter unit quantity", "Enter Unit Quantity " + qty, "The quantity is accepted and the row's Indent Quantity follows it",
                indentOk ? "Unit Quantity " + qty + " entered; the row's Indent Quantity is now " + indentAfter + " (it was " + indentBefore + ", the reorder quantity)"
                        : "Problem — " + (qtyOk ? "the Indent Quantity is " + indentAfter + " after the quantity" : si.problems.toString()),
                indentOk ? "PASS" : "FAIL");
        if (!indentOk) { addSummary("[Auto] Result", "FAILED — unit quantity: " + si.problems); return; }

        // 6) remarks
        String remarks = "QA auto indent remarks - OT Auto Indent " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarksOk = si.enterRemarks("AutoIndent.Remarks", remarks);
        step(P + "Enter remarks", "Enter Remarks (" + remarks + ")", "The remarks are accepted", remarksOk ? "Remarks \"" + remarks + "\" entered; the screen's model holds them" : "Problem — " + si.problems, remarksOk ? "PASS" : "FAIL");
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
                      : si.savedByServer() ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the indent (ResultStatus 1)"
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
