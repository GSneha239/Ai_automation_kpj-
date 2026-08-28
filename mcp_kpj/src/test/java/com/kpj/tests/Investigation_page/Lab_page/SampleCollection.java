package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SampleCollection — referenced by its fully-qualified name.

/**
 * Investigation &gt; Lab &gt; <b>Sample Collection</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Investigation</b> → <b>Lab</b> → <b>Sample Collection</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>In the <b>List of Orders</b>, verify records exist.</li>
 *   <li>Click the <b>tick symbol</b> on one order.</li>
 *   <li>In <b>Test Details</b>, click the <b>tick symbol</b> on one test.</li>
 *   <li>Click <b>Collect</b> — the Sample Collection dialog opens.</li>
 *   <li>Enter the <b>Sample Collection Date</b> and <b>Time</b>.</li>
 *   <li>Click <b>Save</b> → verify the barcode is generated and the success toast appears.</li>
 * </ol>
 *
 * <p>The screen carries TWO grids and each has its own tick, so every selection is scoped to its grid —
 * an unscoped lookup ticks the wrong one. DATA-DEPENDENT: it needs an order whose sample has not been
 * collected, which is the screen's default "Non Collected" filter.</p>
 *
 * <p>Pin values with {@code -Dfrom=}, {@code -Dto=}, {@code -Ddate=}, {@code -Dtime=}.</p>
 *
 * <p>&#9888; A successful run COLLECTS A REAL SAMPLE in the target environment.</p>
 */
public class SampleCollection extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public SampleCollection() { super("Investigation_SampleCollection"); }

    public static void main(String[] args) {
        SampleCollection t = new SampleCollection();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Sample Collection", "Investigation > Lab > Sample Collection",
                "&#9888; COLLECTS A REAL SAMPLE: search a date range, tick an order, tick one of its "
                        + "tests, Collect, enter the collection date and time, Save, then check the "
                        + "barcode and the toast.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String from = System.getProperty("from", "01/01/2020");
        String to = System.getProperty("to", "31/12/2026");
        String date = System.getProperty("date",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String time = System.getProperty("time", "09:00 AM");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Lab_page.SampleCollection sc =
                new com.kpj.pages.Investigation_page.Lab_page.SampleCollection(page);

        boolean rendered = sc.navigateViaMenu(BASE);
        if (!rendered) addSummary("Lab submenu offered", sc.lastMenu);
        step(page, "Open Sample Collection screen",
                "Click Investigation -> Lab -> Sample Collection",
                "The Sample Collection screen is shown",
                rendered ? "Opened " + page.url()
                           + (sc.lastRoute.isEmpty() ? "" : " (menu route " + sc.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Lab submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Dates
        String dates = sc.enterDateRange(from, to);
        boolean datesOk = sc.datesEntered(from, to);
        step(page, "Enter from date and to date",
                "Enter the From Date " + from + " and the To Date " + to,
                "Both dates are entered",
                (datesOk
                    ? "PASSES because both boxes KEPT their value: " + dates
                      + ". These pickers arrive pre-filled and append to what is there, so each box is "
                      + "cleared before typing."
                    : "FAILS — a date did not stay in its box: " + dates),
                datesOk ? "PASS" : "FAIL");

        // Search
        String search = sc.clickSearch();
        int orders = sc.orderRows();
        step(page, "Click Search", "Click Search",
                "The List of Orders is filled",
                (orders > 0 ? "PASSES because the search returned orders: " + search
                            : "FAILS because the search returned nothing: " + search),
                orders > 0 ? "PASS" : "FAIL");

        // Orders exist
        String ordersText = sc.describeOrders();
        step(page, "Verify records exist in the List of Orders",
                "Read the List of Orders grid",
                "At least one order is listed",
                (orders > 0
                    ? "PASSES because the List of Orders holds " + ordersText
                    : "FAILS because the List of Orders is empty: " + ordersText
                      + "  ||  the screen's default filter is \"Non Collected\", so an empty list means "
                      + "every sample in this window has already been collected — a data gap, not a "
                      + "fault in the screen."),
                orders > 0 ? "PASS" : "FAIL");
        if (orders == 0) { addSummary("Result", "FAILED — no order to collect against"); return; }

        // Tick an order
        String orderTick = sc.tickFirstOrder();
        boolean orderOk = sc.orderSelected();
        step(page, "Click the tick symbol on one order",
                "Click the tick symbol on the first order",
                "The order is selected and its tests are listed",
                (orderOk
                    ? "PASSES because the order reports itself selected: " + orderTick
                      + ". The tick is scoped to the ORDERS grid — both grids carry the same control, "
                      + "so an unscoped lookup would tick the wrong one."
                    : "FAILS because the order did not become selected: " + orderTick),
                orderOk ? "PASS" : "FAIL");

        // Tests for that order
        int tests = sc.testRows();
        String testsText = sc.describeTests();
        step(page, "Verify the order's tests are listed in Test Details",
                "Read the Test Details grid",
                "At least one test is listed for the selected order",
                (tests > 0 ? "PASSES because Test Details holds " + testsText
                           : "FAILS because Test Details is empty for that order: " + testsText),
                tests > 0 ? "PASS" : "FAIL");
        if (tests == 0) { addSummary("Result", "FAILED — the selected order has no test to collect"); return; }

        // Tick a test
        String testTick = sc.tickFirstTest();
        boolean testOk = sc.testSelected();
        step(page, "Click the tick symbol on one test",
                "Click the tick symbol on the first test in Test Details",
                "The test is selected",
                (testOk
                    ? "PASSES because the test reports itself selected: " + testTick
                    : "FAILS because the test did not become selected: " + testTick),
                testOk ? "PASS" : "FAIL");

        // Collect
        String collect = sc.clickCollect();
        boolean dialogOpen = sc.collectDialogOpen();
        step(page, "Click Collect", "Click Collect",
                "The Sample Collection dialog opens",
                (dialogOpen ? "PASSES because the dialog came up: " + collect
                            : "FAILS because no dialog appeared: " + collect),
                dialogOpen ? "PASS" : "FAIL");
        if (!dialogOpen) { addSummary("Result", "FAILED — the collection dialog never opened"); return; }

        // Date and time
        String dt = sc.enterCollectionDateTime(date, time);
        boolean dtOk = sc.dateTimeEntered(date);
        step(page, "Enter the sample collection date and time",
                "Enter the Sample Collection Date " + date + " and the time " + time,
                "Both are entered",
                (dtOk
                    ? "PASSES because both controls read their value back: " + dt
                    : "FAILS — the date or the time did not take: " + dt),
                dtOk ? "PASS" : "FAIL");

        // Save
        int tabsBefore = page.context().pages().size();
        String toast = sc.saveAndGetToast();
        boolean ok = com.kpj.pages.Investigation_page.Lab_page.SampleCollection.isSuccess(toast);
        addSummary("What the screen put on screen after Save", sc.lastSave);

        // Barcode
        String barcode = sc.captureBarcode(tabsBefore);
        boolean barcodeOk = !sc.barcodeBlank;
        addSummary("Barcode", barcode);
        step(page, "Verify the barcode is generated",
                "Saving the collection should produce the sample barcode",
                "A barcode document is produced and carries content",
                (barcodeOk
                    ? "PASSES because a barcode document was produced with content in it: " + barcode
                    : "FAILS — " + barcode),
                barcodeOk ? "PASS" : "FAIL");

        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " + toast
                    : "FAILS — " + (toast == null || toast.isEmpty()
                        ? "no message appeared: " + sc.lastSave
                        : "the screen answered: \"" + toast + "\""))
                    + (sc.toastFromObserver
                        ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five seconds, so it "
                          + "was recorded by a mutation observer as the screen showed it."
                        : ""),
                ok ? "PASS" : "FAIL");

        addSummary("Date range", from + " to " + to);
        addSummary("List of Orders", sc.lastOrders);
        addSummary("Selected order", sc.lastOrderTick);
        addSummary("Test Details", sc.lastTests);
        addSummary("Selected test", sc.lastTestTick);
        addSummary("Collection date/time", sc.lastDialog);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
