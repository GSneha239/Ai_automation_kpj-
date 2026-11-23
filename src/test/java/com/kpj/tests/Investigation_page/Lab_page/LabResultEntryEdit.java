package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is named LabResultEntry — referenced by its fully-qualified name.

/**
 * Investigation &gt; Lab &gt; <b>Lab Result Entry</b> &gt; <b>Edit</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Lab</b> → <b>Lab Result Entry</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b> — the From Date is <b>15 days before</b> the To Date.</li>
 *   <li>Click <b>Search</b>, then <b>List of Orders</b>.</li>
 *   <li>Select an order by clicking the <b>tick symbol</b> in its row; verify the row is populated in <b>Test
 *       Details</b>.</li>
 *   <li>In <b>Test Details</b>, click the <b>tick symbol</b> on the row and tick its <b>checkbox</b>.</li>
 *   <li>Click <b>Edit</b> — the Edit screen opens.</li>
 *   <li>Click the <b>Item Details</b> tab.</li>
 *   <li>Click <b>Choose File</b> and upload a test doc.</li>
 *   <li>Click <b>Add</b> — the document joins the Document List.</li>
 *   <li>Click <b>Save</b>, answer <b>Yes</b> on the confirmation dialog.</li>
 *   <li>Verify the success toast.</li>
 * </ol>
 *
 * <p>Steps 1-6 are the same as {@link LabResultEntry}. This is a separate test because THIS flow SAVES: the
 * original is read-only.</p>
 *
 * <p>&#9888; A successful run ATTACHES A REAL DOCUMENT to the first pending test's result entry in the target
 * environment. The document is a small {@code .txt} generated at run time with a unique name (so it is told apart
 * from files already on the order); pin your own with {@code -Ddoc=&lt;path&gt;}.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=} (dd/MM/yyyy). By default the To Date is today and the From Date is
 * the To Date minus 15 days. The default status filter is <b>Pending</b>, so the run needs pending tests in the
 * range — it is DATA-DEPENDENT.</p>
 */
public class LabResultEntryEdit extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** The From Date sits this many days before the To Date. */
    public static final int RANGE_DAYS = 15;

    private static final java.time.format.DateTimeFormatter DTF =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public LabResultEntryEdit() { super("Investigation_LabResultEntryEdit"); }

    public static void main(String[] args) {
        LabResultEntryEdit t = new LabResultEntryEdit();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** The document to upload: {@code -Ddoc=<path>}, else a fresh uniquely-named .txt in a temp folder. */
    private static java.nio.file.Path testDoc() throws java.io.IOException {
        String pinned = System.getProperty("doc", "").trim();
        if (!pinned.isEmpty()) return java.nio.file.Paths.get(pinned);
        String stamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("mcp-kpj-testdoc");
        java.nio.file.Path f = dir.resolve("lab-result-test-doc-" + stamp + ".txt");
        java.nio.file.Files.writeString(f, "Automated test document for Lab Result Entry > Edit > Item Details.\n"
                + "Generated " + stamp + " by the mcp-kpj suite. Safe to delete.\n");
        return f;
    }

    @Override
    protected void body() {
        meta("Investigation - Lab - Lab Result Entry - Edit", "Investigation > Lab > Lab Result Entry > Edit",
                "&#9888; ATTACHES A REAL DOCUMENT: search a 15-day range, tick an order and a test, click Edit, "
                        + "open Item Details, upload a test doc, Add, Save, confirm Yes and check the success toast.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // To Date = today, From Date = 15 days before it — unless pinned with -Dto= / -Dfrom=.
        String to = System.getProperty("to", java.time.LocalDate.now().format(DTF));
        String from = System.getProperty("from",
                java.time.LocalDate.parse(to, DTF).minusDays(RANGE_DAYS).format(DTF));

        java.nio.file.Path doc;
        try { doc = testDoc(); }
        catch (java.io.IOException e) { throw new RuntimeException("could not create the test document: " + e.getMessage(), e); }
        String docName = doc.getFileName().toString();

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Lab_page.LabResultEntry lre =
                new com.kpj.pages.Investigation_page.Lab_page.LabResultEntry(page);

        // 1) Navigate
        boolean rendered = lre.navigateViaMenu(BASE);
        if (!rendered) addSummary("Lab submenu offered", lre.lastMenu);
        step(page, "Open Lab Result Entry screen",
                "Click Investigation -> Lab -> Lab Result Entry",
                "The Lab Result Entry screen is shown",
                rendered ? "Opened " + page.url()
                           + (lre.lastRoute.isEmpty() ? "" : " (menu route " + lre.lastRoute + ")")
                           + ". The label is matched in full — Lab also has Lab Result Entry Admin Auth."
                         : "Did NOT reach the screen (" + page.url() + ")",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Dates — From = To - 15 days
        String dates = lre.enterDateRange(from, to);
        boolean datesOk = lre.datesEntered(from, to);
        long span = java.time.temporal.ChronoUnit.DAYS.between(
                java.time.LocalDate.parse(from, DTF), java.time.LocalDate.parse(to, DTF));
        step(page, "Enter from date and to date",
                "Enter the To Date " + to + " and set the From Date " + RANGE_DAYS + " days before it (" + from + ")",
                "Both dates are entered, the From Date " + RANGE_DAYS + " days before the To Date",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates + ". The From Date is " + span
                      + " day(s) before the To Date" + (span == RANGE_DAYS ? "." : " — NOT the " + RANGE_DAYS
                      + " asked for, because the range was pinned with -Dfrom/-Dto.")
                      + " " + lre.lastDateRange + "."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        // 3) Search
        String search = lre.clickSearch();
        int tests = lre.testRows();
        step(page, "Click Search", "Click Search",
                "The screen returns tests for the date range",
                (tests > 0 ? "PASSES because the search returned tests: " + search
                           : "FAILS because the search returned nothing: " + search),
                tests > 0 ? "PASS" : "FAIL");
        if (tests == 0) { addSummary("Result", "FAILED — no pending tests in " + from + " to " + to); return; }

        // 4) List of Orders
        String orders = lre.openListOfOrders();
        int orderRows = lre.orderRows();
        step(page, "Click List of Orders",
                "Click the List of Orders header (fnSearchResultEntry) and read the orders grid",
                "The List of Orders opens and lists the orders",
                (orderRows > 0 ? "PASSES because the orders grid holds " + orders
                               : "FAILS because the orders grid is empty: " + orders),
                orderRows > 0 ? "PASS" : "FAIL");
        if (orderRows == 0) { addSummary("Result", "FAILED — List of Orders is empty"); return; }

        // 5) Tick an order
        String orderTick = lre.tickFirstOrder();
        step(page, "Select a row in List of Orders by clicking its tick symbol",
                "Click the tick symbol on the first order",
                "The order is selected",
                (lre.orderTicked ? "PASSES because the tick read back as selected: " + orderTick
                                 : "FAILS because the order did not select: " + orderTick),
                lre.orderTicked ? "PASS" : "FAIL");

        // 6) Test Details populated
        int detailRows = lre.testRows();
        String detail = lre.describeTests();
        step(page, "Verify the selected row is populated in Test Details",
                "Read the Test Details grid after the order is ticked",
                "Test Details lists the selected order's test(s)",
                (detailRows > 0 ? "PASSES because Test Details holds " + detail
                                : "FAILS because Test Details is empty: " + detail),
                detailRows > 0 ? "PASS" : "FAIL");
        if (detailRows == 0) { addSummary("Result", "FAILED — Test Details empty for the ticked order"); return; }

        // 7) Tick in Test Details
        String testTick = lre.tickFirstTest();
        step(page, "In Test Details, click the tick symbol to select the row",
                "Click the tick symbol on the first test row",
                "The test row is selected",
                (lre.testTicked ? "PASSES because the tick read back as selected: " + testTick
                                : "FAILS because the test row did not select: " + testTick),
                lre.testTicked ? "PASS" : "FAIL");

        // 8) Tick the checkbox
        String check = lre.tickFirstTestCheckbox();
        step(page, "Tick the checkbox in that row",
                "Tick the row's checkbox (IsChecked)",
                "The checkbox is ticked",
                (lre.testCheckboxTicked() ? "PASSES because the box read back as set: " + check
                                          : "FAILS because the box did not tick: " + check),
                lre.testCheckboxTicked() ? "PASS" : "FAIL");

        // 9) Edit
        String edit = lre.clickEdit();
        boolean onEdit = lre.onEditScreen();
        step(page, "Click Edit", "Click Edit (editLabResult) on the footer",
                "The Edit screen opens for the selected test",
                (onEdit ? "PASSES because " + edit : "FAILS because " + edit),
                onEdit ? "PASS" : "FAIL");
        if (!onEdit) { addSummary("Result", "FAILED — Edit screen not reached"); return; }

        // 10) Item Details
        String item = lre.openItemDetailsTab();
        boolean itemOpen = lre.itemDetailsOpen();
        step(page, "Click Item Details", "Click the Item Details tab next to Test Details",
                "The Item Details tab is open",
                (itemOpen ? "PASSES because the tab read back as active with its item grid showing: " + item
                          : "FAILS because the tab did not open: " + item),
                itemOpen ? "PASS" : "FAIL");

        // 11) Choose File
        String upload = lre.chooseFile(doc);
        boolean chosen = lre.fileChosen();
        step(page, "Click Choose File and upload a test doc",
                "Click Choose File and pick " + docName,
                "The test document is chosen",
                (chosen ? "PASSES because " + upload : "FAILS because " + upload),
                chosen ? "PASS" : "FAIL");
        if (!chosen) { addSummary("Result", "FAILED — the test doc could not be chosen"); return; }

        // 12) Add
        String add = lre.clickAdd(docName);
        boolean added = lre.documentAdded(docName);
        step(page, "Click Add", "Click Add (AddDocList)",
                "The document is added to the Document List",
                (added ? "PASSES because " + add : "FAILS because " + add),
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the document did not join the Document List"); return; }

        // 13) Save + Yes + toast
        String toast = lre.clickSaveConfirmAndGetToast();
        boolean saved = lre.saveSucceeded();
        step(page, "Click Save, click Yes on the confirmation dialog, and verify the success toast",
                "Click Save; click Yes on the confirmation dialog; read the toast",
                "A success toast appears",
                (saved ? "PASSES because " + lre.lastSave + " and the screen answered: \"" + toast + "\""
                       : "FAILS because " + lre.lastSave + "; the screen answered: \""
                         + (toast.isEmpty() ? "(no toast appeared)" : toast) + "\""),
                saved ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to + " (" + span + " days)");
        addSummary("Selected order", lre.lastOrderTick);
        addSummary("Selected test", lre.lastTestTick);
        addSummary("Uploaded document", docName + " — " + lre.lastUpload);
        addSummary("Save toast", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", saved ? "Document attached and saved" : "Save not confirmed");
    }
}
