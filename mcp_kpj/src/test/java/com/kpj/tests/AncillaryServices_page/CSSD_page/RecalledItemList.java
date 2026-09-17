package com.kpj.tests.AncillaryServices_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named RecalledItemList — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; CSSD &gt; <b>Recalled Item List</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li>Open <b>Ancillary Services</b> → <b>CSSD</b> → <b>Recalled Item List</b>.</li>
 *   <li>Click <b>New</b> → the Recall form opens.</li>
 *   <li>Enter <b>From Date</b> / <b>To Date</b> → click <b>Search</b>.</li>
 *   <li>In <b>Transfer List</b>: click a row (the "Select" column) → verify it populates <b>Item List</b>.</li>
 *   <li>In <b>Item List</b>: tick the row's checkbox to select the item.</li>
 *   <li>Select the required <b>Recall Reason</b>.</li>
 *   <li>Click <b>Recall</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES a real recall transaction in the target environment (a new
 * Recall Number against whichever transfer sorts first in the searched date range).</p>
 *
 * <p><b>Recall Reason is required but wasn't in the original step list</b> — confirmed live that
 * {@code fnSaveRecall()} needs {@code RecalledItem.recallreasonid} set (the field is marked * on screen);
 * "Other" is used here as a safe default. See {@link com.kpj.pages.AncillaryServices_page.CSSD_page.RecalledItemList}'s
 * class javadoc for the confirmed-live data dependency (the Transfer List is not filtered to exclude
 * already-recalled transfers, so a repeat run over the same date range may hit one already recalled).</p>
 */
public class RecalledItemList extends DevHisBase {

    public RecalledItemList() { super("AncillaryServices_CSSD_RecalledItemList"); }

    public static void main(String[] args) {
        RecalledItemList t = new RecalledItemList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - CSSD - Recalled Item List", "Ancillary Services > CSSD > Recalled Item List",
                "&#9888; Creates a REAL recall transaction: New, date range, Search, select transfer + item, Recall Reason, Recall.");

        String fromDate = System.getProperty("recall.fromDate", "01/08/2026");
        String toDate = System.getProperty("recall.toDate", java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String recallReason = System.getProperty("recall.reason", "Other");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.CSSD_page.RecalledItemList ril =
                new com.kpj.pages.AncillaryServices_page.CSSD_page.RecalledItemList(page);

        // 1) Navigate
        boolean onScreen = ril.navigateViaMenu();
        step(page, "Open Recalled Item List screen", "Click Ancillary Services -> CSSD -> Recalled Item List",
                "The Recalled Item List screen is shown",
                onScreen ? "Opened (Recalled Item List screen)" : "Did NOT reach the screen",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = ril.clickNew();
        step(page, "Click New", "Click New (fnAddRecalledItem)",
                "The Recall form opens (From Date / To Date / Search visible)",
                formOpen ? "Form opened" : "Form did NOT open",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — Recall form not reached"); return; }

        // 3) From Date / To Date
        String fromSet = ril.setFromDate(fromDate);
        String toSet = ril.setToDate(toDate);
        boolean datesOk = fromDate.equals(fromSet) && toDate.equals(toSet);
        step(page, "Enter From Date and To Date", "From Date = " + fromDate + ", To Date = " + toDate,
                "Both date fields hold the entered values",
                "From Date = " + fromSet + ", To Date = " + toSet,
                datesOk ? "PASS" : "FAIL");

        // 4) Search
        int rowCount = ril.clickSearch();
        List<String> transferRows = ril.transferListRows();
        step(page, "Click Search", "Click Search (fnFetchHeaderList), Issuing Store = CSSD",
                "The Transfer List shows matching transfer(s) in the date range",
                rowCount > 0 ? rowCount + " transfer(s) found: " + transferRows
                        : "Search ran, but no transfer was issued from CSSD in " + fromDate + " - " + toDate,
                rowCount > 0 ? "PASS" : "FAIL");
        if (rowCount == 0) { addSummary("Result", "FAILED — no transfer available to select (From " + fromDate + " To " + toDate + ")"); return; }

        // 5) Transfer List -> select row -> verify Item List populates
        String transferRow = ril.selectFirstTransferRow();
        List<String> itemRows = ril.itemListRows();
        boolean itemListOk = !itemRows.isEmpty();
        step(page, "Transfer List · select item (click the row in the Select column)", "Click the first Transfer List row",
                "The item populates in Item List",
                itemListOk
                        ? "PASSES because Item List shows: " + itemRows
                        : "FAILS — selected transfer \"" + transferRow + "\" but Item List stayed empty",
                itemListOk ? "PASS" : "FAIL");
        if (!itemListOk) { addSummary("Selected transfer", transferRow); addSummary("Result", "FAILED — Item List did not populate"); return; }

        // 6) Item List -> tick checkbox
        String itemRow = ril.tickFirstItemRow();
        step(page, "Item List · tick checkbox to select the item", "Tick the checkbox on the first Item List row",
                "The item is marked selected; Recall Reason/Remarks become enabled",
                itemRow.isEmpty() ? "FAILS — no row to tick" : "Ticked: " + itemRow,
                itemRow.isEmpty() ? "FAIL" : "PASS");
        if (itemRow.isEmpty()) { addSummary("Result", "FAILED — no Item List row to tick"); return; }

        // 7) Recall Reason (required)
        String reasonPicked = ril.selectRecallReason(recallReason);
        boolean reasonOk = recallReason.equalsIgnoreCase(reasonPicked);
        step(page, "Select Recall Reason", "Recall Reason = " + recallReason + " (required field, not in the original step list)",
                "Recall Reason is set to the requested value",
                "Recall Reason = " + reasonPicked,
                reasonOk ? "PASS" : "FAIL");

        // 8) Recall -> success toast
        String toast = ril.clickRecallAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean saveOk = tl.contains("success") || tl.contains("saved");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (saveOk ? toast : "Recall not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Recall & verify success toast", "Click Recall (fnSaveRecall)",
                "'Recall saved successfully. Recall Number: ...' toast", actual, saveOk ? "PASS" : "FAIL");

        addSummary("Transfer selected", transferRow);
        addSummary("Item", itemRow);
        addSummary("Recall Reason", reasonPicked);
        addSummary("Recall Number", ril.lastRecallNumber.isEmpty() ? "(not parsed)" : ril.lastRecallNumber);
        addSummary("Result", saveOk ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
