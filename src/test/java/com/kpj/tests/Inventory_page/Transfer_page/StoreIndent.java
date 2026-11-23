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
 *   <li><b>Indent List</b> (read-only): select the <b>Requesting Store</b> from its list, enter a one-year <b>From Date</b>/<b>To Date</b> ending today, click <b>Search</b> — when the store has no
 *       record the next store of the list is selected and searched, through the whole list, until one returns a record — verify the records are displayed in the Indent List, click one record,
 *       verify it is displayed in <b>Indent Details</b>, click <b>Print</b> (the report opens in a new tab) and take its screenshot. Pin with {@code -Dsi.store=}, {@code -Dsi.from=}, {@code -Dsi.to=}.</li>
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
                "&#9888; Creates REAL Store Indents (one manual, one auto). Indent List: Requesting Store (next store until a record), one-year dates, Search, click a record, "
                        + "Indent Details, Print (report screenshot). Then Manual Indent: click Manual Indent, select Issuing Store, "
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
        addDiagnostic("Content loaded", contentLoaded ? "yes" : "NO — still just the header shell after 15s");

        addDiagnostic("List screen controls", si.describeControls());

        // ==================== Indent List: search, record, Indent Details, Print (read-only) ====================
        {
            java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
            java.time.LocalDate today = java.time.LocalDate.now();
            String listTo = System.getProperty("si.to", today.format(df));
            String listFrom = System.getProperty("si.from", today.minusYears(1).format(df));
            String pinned = System.getProperty("si.store", "");
            com.kpj.pages.Inventory_page.Transfer_page.StoreIndentList list = new com.kpj.pages.Inventory_page.Transfer_page.StoreIndentList(page);

            java.util.List<String> stores = list.storeNames();
            if (stores.isEmpty()) {
                step(page, "[List] Select requesting store", "Open the Requesting Store list", "The list offers stores", "FAILED — the Requesting Store list is empty", "FAIL");
                addSummary("Result", "FAILED — no stores in the Requesting Store list");
                return;
            }
            // the order the stores are tried in: the pinned one first, then the list order
            java.util.List<String> order = new java.util.ArrayList<>();
            if (!pinned.isEmpty() && stores.contains(pinned)) order.add(pinned);
            for (String s : stores) if (!s.equals(pinned)) order.add(s);

            // Requesting store (the first; the Search step moves on through the list when it has no record)
            String first = order.get(0);
            boolean storeOk = list.selectStore(first);
            step(page, "[List] Select requesting store", "Open the Requesting Store list and click \"" + first + "\"", "The store is selected from the list",
                    storeOk ? "Requesting Store \"" + list.lastStore + "\" selected from the list (" + stores.size() + " stores offered)" : "FAILED — " + list.problems,
                    storeOk ? "PASS" : "FAIL");
            if (!storeOk) { addSummary("Result", "FAILED — store: " + list.problems); return; }

            // Date range: one year ending today
            String dates = list.enterDateRange(listFrom, listTo);
            boolean datesOk = list.ok();
            step(page, "[List] Enter date (1 year range)", "Enter the From Date " + listFrom + " and the To Date " + listTo, "Both dates are entered",
                    datesOk ? dates : "FAILED — " + list.problems + " (" + dates + ")",
                    datesOk ? "PASS" : "FAIL");
            if (!datesOk) { addSummary("Result", "FAILED — dates: " + list.problems); return; }

            // Search; store after store until a record comes back
            java.util.List<String> tried = new java.util.ArrayList<>();
            java.util.List<String> skipped = new java.util.ArrayList<>();
            tried.add(first);
            int n = list.clickSearch();
            String foundStore = n > 0 ? list.lastStore : "";
            System.out.println("StoreIndent: [1/" + order.size() + "] " + first + " -> " + n + " indent(s)");
            for (int i = 1; i < order.size() && n == 0; i++) {
                String next = order.get(i);
                if (!list.selectStore(next)) { skipped.add(next + " (" + list.problems + ")"); continue; }
                tried.add(next);
                n = list.clickSearch();
                if (!(list.lastSearchStatus > 0 && list.lastSearchStatus < 400)) skipped.add(next + " (search: " + list.problems + ")");
                System.out.println("StoreIndent: [" + (i + 1) + "/" + order.size() + "] " + next + " -> " + n + " indent(s)");
                if (n > 0) foundStore = list.lastStore;
            }
            boolean found = n > 0;
            String triedText = tried.size() == 1 ? "\"" + tried.get(0) + "\"" : tried.size() + " store(s) in list order, from \"" + tried.get(0) + "\" to \"" + tried.get(tried.size() - 1) + "\"";
            step(page, "[List] Click search (try the next store until a record comes back)", "Click Search; if the store has no record select the next store of the list and search again",
                    "The Indent List shows a record",
                    found ? n + " indent(s) found for \"" + foundStore + "\" after trying " + triedText + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped)
                          : "FAILED — none of the " + tried.size() + " store(s) tried (" + triedText + ") returned an indent between " + listFrom + " and " + listTo
                              + (skipped.isEmpty() ? "" : "; stores that could not be tried: " + skipped) + (list.ok() ? "" : "; last Search problem: " + list.problems),
                    found ? "PASS" : "FAIL");
            if (!found) { addSummary("Result", "FAILED — no store has an indent in " + listFrom + " - " + listTo); return; }

            // Records displayed in the Indent List
            java.util.List<java.util.List<String>> rows = list.indentRows();
            int bad = list.incompleteRows(rows);
            boolean populated = !rows.isEmpty() && bad == 0;
            String sample = list.sample(rows, 2);
            step(page, "[List] Verify records are displayed in the Indent List table", "Read the Indent List after Search", "The indents are listed with their date, number, status and stores",
                    populated ? rows.size() + " indent(s) listed, each with date-time, indent no., status, location, type and requesting store; first: " + sample
                              : "FAILED — " + (rows.isEmpty() ? "the table is empty" : bad + " of " + rows.size() + " row(s) lack a date, number, status, location, type or requesting store") + "; " + sample,
                    populated ? "PASS" : "FAIL");
            if (!populated) { addSummary("Result", "FAILED — Indent List not populated"); return; }

            // Click 1 record -> Indent Details
            String no = list.selectFirstRecord();
            boolean rowOk = !no.isEmpty() && list.ok();
            step(page, "[List] Click 1 record", "Click the Indent No. of the first record in the Indent List", "The indent is selected (highlighted) and its items load",
                    rowOk ? "Selected " + no + " (" + list.lastRowText + ") — row highlighted, " + list.lastDetailRows + " item row(s) loaded" : "FAILED — " + list.problems,
                    rowOk ? "PASS" : "FAIL");
            if (!rowOk) { addSummary("Result", "FAILED — record: " + list.problems); return; }

            java.util.List<String> det = list.detailRows();
            boolean detOk = !det.isEmpty() && det.get(0).trim().matches("^\\d+\\s+\\S+.*");          // a numbered item line: Sr.No. then the item code
            step(page, "[List] Verify the record is displayed in Indent Details", "Read Indent Details after selecting the indent", "The indent's item lines are shown (Sr.No., Item Code, Item Name ...)",
                    detOk ? "Indent Details lists " + det.size() + " item line(s) for " + no + "; first: " + det.get(0)
                          : "FAILED — Indent Details " + (det.isEmpty() ? "is empty" : "holds no numbered item line: " + det),
                    detOk ? "PASS" : "FAIL");

            // Print -> report tab -> screenshot
            PdfReport.Result listPdf = list.clickPrintAndCapture();
            boolean listTab = !listPdf.url.isEmpty();
            boolean listReportOk = listTab && !listPdf.blank && listPdf.url.toLowerCase().contains("indentid=");
            String listEvidence = listTab ? listPdf.diagnostics + (listPdf.png != null ? " The screenshot is the report's first page rendered from the file itself." : "")
                                          : "no report tab opened" + (list.lastToast.isEmpty() ? "" : " — the screen said \"" + list.lastToast + "\"");
            String listVerdict = (listReportOk ? "Report opened in a new tab for indent " + no + ": " : "FAILED — ") + listEvidence;
            if (listPdf.png != null && listPdf.png.length > 0) {
                step(listPdf.png, "[List] Click print; report opens in a new tab (screenshot)", "Click Print with the indent selected; take the report screenshot",
                        "The indent report opens in a new tab", listVerdict, listReportOk ? "PASS" : "FAIL");
            } else {
                step(page, "[List] Click print; report opens in a new tab (screenshot)", "Click Print with the indent selected; take the report screenshot",
                        "The indent report opens in a new tab", listVerdict, listReportOk ? "PASS" : "FAIL");
            }
            addSummary("[List] Indent printed", list.lastRowText + " | " + list.lastDetailRows + " item line(s) | " + tried.size() + " of " + order.size() + " store(s) tried | "
                    + listFrom + " - " + listTo + " | report " + (listTab ? listPdf.url : "(none)"));
        }

        // 2) "Add a new tab - Manual Indent" — reported plainly; not assumed to be a real tab.
        String manualIndentTab = si.clickManualIndentTab();
        addDiagnostic("\"Add a new tab - Manual Indent\"", manualIndentTab);

        // 3) Click Manual Indent
        String manualIndentResult = si.clickManualIndent();
        boolean manualIndentOk = si.manualIndentClicked();
        step(page, "Click Manual Indent", "Click Manual Indent",
                "The entry form opens",
                manualIndentOk ? manualIndentResult : "FAILED — " + manualIndentResult,
                manualIndentOk ? "PASS" : "FAIL");
        if (!manualIndentOk) { addSummary("Result", "FAILED — the entry form never opened"); return; }

        addDiagnostic("Entry form controls", si.describeControls());

        // 4) Select Issuing Store
        String issuingStoreResult = si.selectIssuingStore();
        boolean issuingStoreOk = si.issuingStoreSelected();
        step(page, "Select issuing store", "Select the Issuing Store",
                "An issuing store is selected",
                issuingStoreOk ? issuingStoreResult : "FAILED — " + issuingStoreResult,
                issuingStoreOk ? "PASS" : "FAIL");
        if (!issuingStoreOk) { addSummary("Result", "FAILED — no issuing store could be selected"); return; }

        // 4b) Requesting Store (the form's own Store, StoreIndent.SearchStoreID) — chosen from its dropdown AFTER the Issuing
        // Store, since its list may be filled from that choice. The Item Search dialog's Search refuses to run without it.
        String ownStore = si.selectOwnStore();
        boolean ownStoreOk = si.ownStoreSelected();
        step(page, "Select Requesting Store", "Select the Requesting Store from the dropdown",
                "A Requesting Store is selected",
                ownStoreOk ? "Requesting Store = " + ownStore : "FAILED — " + ownStore
                        + " — without it the Item Search dialog answers \"Please Select Store Name !!!!\"",
                ownStoreOk ? "PASS" : "FAIL");

        // 5) Click Get Items
        String getItemsResult = si.clickGetItems();
        boolean dialogOpenOk = si.dialogOpen();
        step(page, "Click get items", "Click Get Items",
                "The Item Search dialog opens",
                dialogOpenOk ? getItemsResult : "FAILED — " + getItemsResult,
                dialogOpenOk ? "PASS" : "FAIL");
        if (!dialogOpenOk) { addSummary("Result", "FAILED — the Item Search dialog never opened"); return; }

        addDiagnostic("Item Search dialog controls", si.describeControls());

        // 5b) The dialog's own Store dropdown — its Search refuses to run while no Store is chosen.
        String dialogStore = si.selectDialogStore();
        boolean dialogStoreOk = si.dialogStoreSelected();
        step(page, "In dialog popup -Item Search, select the Store", "Select the Store from the dialog's Store dropdown",
                "A Store is selected in the dialog",
                dialogStoreOk ? "Store = " + dialogStore : "FAILED — " + dialogStore,
                dialogStoreOk ? "PASS" : "FAIL");

        // 6) In dialog popup - Item Search, click the search button
        String dialogSearchResult = si.clickSearchInDialog();
        boolean dialogRowsOk = si.dialogItemRowsFound();
        step(page, "In dialog popup -Item Search, click the search button", "Click Search",
                "Item rows appear in the dialog",
                dialogRowsOk ? dialogSearchResult : "FAILED — " + dialogSearchResult,
                dialogRowsOk ? "PASS" : "FAIL");
        if (!dialogRowsOk) { addSummary("Result", "FAILED — no item to select"); return; }

        // 7) Click tick on checkbox to select item
        String itemTickResult = si.tickFirstItemInDialog();
        boolean itemTickOk = si.itemTicked();
        step(page, "Click tick on checkbox to select item", "Tick the checkbox for one item",
                "The item is selected",
                itemTickOk ? itemTickResult : "FAILED — " + itemTickResult,
                itemTickOk ? "PASS" : "FAIL");
        if (!itemTickOk) { addSummary("Result", "FAILED — no item was selected"); return; }

        // 8) Click OK
        String dialogOkResult = si.clickOkInDialog();
        boolean dialogOkOk = si.dialogOkClicked() && si.dialogClosed();
        step(page, "Click ok", "Click OK",
                "The dialog closes and the item appears on the main form",
                dialogOkOk ? dialogOkResult : "FAILED — " + dialogOkResult,
                dialogOkOk ? "PASS" : "FAIL");
        if (!dialogOkOk) { addSummary("Result", "FAILED — the Item Search dialog never closed"); return; }

        addDiagnostic("Main form controls after selecting the item", si.describeControls());

        // 9) Enter unit quantity
        String unitQtyResult = si.enterUnitQuantity(qty);
        boolean unitQtyOk = si.unitQuantityEntered(qty);
        step(page, "Enter unit quantity", "Enter Unit Quantity " + qty,
                "The Unit Quantity is entered",
                unitQtyOk ? unitQtyResult + si.quantityNote() : "FAILED — " + unitQtyResult + si.quantityNote(),
                unitQtyOk ? "PASS" : "FAIL");

        // 10) Enter remarks
        String remarksResult = si.enterRemarks(remarks);
        boolean remarksOk = si.remarksEntered(remarks);
        step(page, "Enter remarks", "Enter Remarks " + remarks,
                "Remarks is entered",
                remarksOk ? remarksResult : "FAILED — " + remarksResult,
                remarksOk ? "PASS" : "FAIL");

        // 11) Click Save
        int tabsBeforeSave = si.clickSave();
        boolean saveClickedOk = si.saveClicked();
        step(page, "Click save", "Click Save",
                "The Save action fires",
                saveClickedOk ? si.lastSaveDiagnostics : "FAILED — " + si.lastSaveDiagnostics,
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
        if (pdf.png != null && pdf.png.length > 0) {
            // Chrome's PDF viewer screenshots blank, so the screenshot is the PDF's first page rendered from the file.
            step(pdf.png, "Verify report generation", "Wait for the report to open",
                    "A non-blank PDF is generated",
                    pdf.diagnostics + " The screenshot is the PDF's first page rendered from the file itself.",
                    pdf.blank ? "FAIL" : "PASS");
        } else {
            step(page, "Verify report generation", "Wait for the report to open",
                    "A non-blank PDF is generated", pdf.diagnostics, pdf.blank ? "FAIL" : "PASS");
        }

        // 14) In the indent number pop up, click ok
        String indentNumberResult = si.clickIndentNumberOk();
        boolean indentNumberOk = si.indentNumberOkClicked();
        step(page, "In the indent number pop up, click ok", "Click OK on the Indent Number popup",
                "The popup is dismissed",
                indentNumberOk ? indentNumberResult : "FAILED — " + indentNumberResult,
                indentNumberOk ? "PASS" : "FAIL");

        addSummary("[Manual] Indent saved", "Issuing store " + si.lastIssuingStore + " | quantity " + si.lastUnitQty + " | remarks " + si.lastRemarks + " | "
                + (success ? toast : "not confirmed (\"" + toast + "\")") + " | report " + (pdf.url.isEmpty() ? "(none)" : pdf.url));

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
        addDiagnostic("\"Add new tab - Auto Indent\"", autoIndentTab);

        // 16) Click Auto Indent
        String autoIndentResult = si.clickAutoIndent();
        boolean autoIndentOk = si.autoIndentClicked();
        step(page, "[Auto Indent] Click auto indent", "Click Auto Indent",
                "The Auto Indent screen opens",
                autoIndentOk ? autoIndentResult : "FAILED — " + autoIndentResult,
                autoIndentOk ? "PASS" : "FAIL");
        if (!autoIndentOk) { addSummary("Auto Indent result", "FAILED — the Auto Indent screen never opened"); return; }

        addDiagnostic("[Auto Indent] Screen controls", si.describeControls());

        // 17) Select requesting store and issuing store
        String storesResult = si.selectRequestingAndIssuingStore();
        boolean storesOk = si.requestingAndIssuingStoreSelected();
        step(page, "[Auto Indent] Select requesting store and issuing store",
                "Select the Requesting Store (Pharmacy Main Store, else Pharmacy (Central)) and the Issuing Store",
                "Both stores are selected",
                storesOk ? storesResult : "FAILED — " + storesResult,
                storesOk ? "PASS" : "FAIL");
        if (!storesOk) { addSummary("Auto Indent result", "FAILED — the stores could not be selected"); return; }

        // 18) Click Search
        String autoSearchResult = si.clickAutoIndentSearch();
        boolean autoRowsOk = si.autoIndentRowsFound();
        String swapNote = "";
        if (!autoRowsOk) {                                              // the first pair lists nothing: try the other way round before giving up
            String swapped = si.selectRequestingAndIssuingStore(si.lastAutoIssuingStore, si.lastRequestingStore);
            autoSearchResult = si.clickAutoIndentSearch();
            autoRowsOk = si.autoIndentRowsFound();
            swapNote = " (the first pair listed no items, so the pair was swapped: " + swapped + ")";
            storesResult = swapped;
        }
        step(page, "[Auto Indent] Click search", "Click Search",
                "Records are displayed in Auto Indent Details",
                autoRowsOk ? autoSearchResult + swapNote : "FAILED — " + autoSearchResult + swapNote,
                autoRowsOk ? "PASS" : "FAIL");
        if (!autoRowsOk) { addSummary("Auto Indent result", "FAILED — no item to select"); return; }

        // 19) In auto indent details, click tick on checkbox to select item
        String autoTickResult = si.tickFirstAutoIndentItem();
        boolean autoTickOk = si.autoIndentItemTicked();
        step(page, "In auto indent details, click tick on checkbox to select item",
                "Tick the checkbox for one item",
                "The item is selected",
                autoTickOk ? autoTickResult : "FAILED — " + autoTickResult,
                autoTickOk ? "PASS" : "FAIL");
        if (!autoTickOk) { addSummary("Auto Indent result", "FAILED — no item was selected"); return; }

        // 20) Enter remarks
        String autoRemarksResult = si.enterAutoIndentRemarks(autoRemarks);
        boolean autoRemarksOk = si.autoIndentRemarksEntered(autoRemarks);
        step(page, "[Auto Indent] Enter remarks", "Enter Remarks " + autoRemarks,
                "Remarks is entered",
                autoRemarksOk ? autoRemarksResult : "FAILED — " + autoRemarksResult,
                autoRemarksOk ? "PASS" : "FAIL");

        // 21) Click Save Indent
        int tabsBeforeAutoSave = si.clickSaveIndent();
        boolean saveIndentOk = si.saveIndentClicked();
        step(page, "Click save indent", "Click Save Indent",
                "The Save Indent action fires",
                saveIndentOk ? si.lastSaveIndentDiagnostics : "FAILED — " + si.lastSaveIndentDiagnostics,
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
        if (autoPdf.png != null && autoPdf.png.length > 0) {
            step(autoPdf.png, "Verify report generation", "Wait for the report to open",
                    "A non-blank PDF is generated",
                    autoPdf.diagnostics + " The screenshot is the PDF's first page rendered from the file itself.",
                    autoPdf.blank ? "FAIL" : "PASS");
        } else {
            step(page, "Verify report generation", "Wait for the report to open",
                    "A non-blank PDF is generated", autoPdf.diagnostics, autoPdf.blank ? "FAIL" : "PASS");
        }

        addSummary("[Auto Indent] Indent saved", storesResult + " | remarks " + si.lastAutoIndentRemarks + " | "
                + (autoSuccess ? autoToast : "not confirmed (\"" + autoToast + "\")") + " | report " + (autoPdf.url.isEmpty() ? "(none)" : autoPdf.url));
        addSummary("Indents created", ((success ? 1 : 0) + (autoSuccess ? 1 : 0)) + " / 2");
    }
}
