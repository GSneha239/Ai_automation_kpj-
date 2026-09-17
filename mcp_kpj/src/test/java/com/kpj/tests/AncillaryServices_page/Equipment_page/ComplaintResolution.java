package com.kpj.tests.AncillaryServices_page.Equipment_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ComplaintResolution — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Equipment/Asset &gt; <b>Complaint Resolution</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>Complaint Resolution</b>.</li>
 *   <li>Enter <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Select <b>Location</b>.</li>
 *   <li>Select <b>Store</b>.</li>
 *   <li>Click <b>Search</b> → verify the search returned results.</li>
 * </ol>
 *
 * <p>Only Location and Store are filled in — Equipment Type, Equipment Code, Equipment Name, Complaint
 * Category, Status, In House/Service Agent and Employee are left untouched.</p>
 *
 * <p>Read-only: this flow only searches, it creates nothing.</p>
 *
 * <h2>BLOCKED on devhis — no complaint data, and the Status list is empty</h2>
 * <p>As of 2026-08-06 two steps fail, both on missing data rather than on the script:</p>
 * <ul>
 *   <li><b>Select Status</b> — {@code ComplaintResolution.statusid} offers no real options at all, only
 *       the placeholder. Nothing selectable exists.</li>
 *   <li><b>Search</b> — returns zero rows. The search itself works: it runs cleanly and, when Store is
 *       left blank, correctly answers "Please Select Store!" (Store is mandatory — which is also why
 *       clearing every filter is not a valid comparison). Broadening the search by clearing Equipment
 *       Type, Code, Name and Category and scanning <b>25 stores</b> across a <b>2-year</b> window still
 *       returned nothing.</li>
 * </ul>
 *
 * <p>This is consistent with the sibling <b>Complaint Details</b> screen, where no complaint can be
 * created because its Equipment list is empty under all 96 stores: with nothing able to raise a
 * complaint, there is nothing here to resolve. Note the equipment master data itself is fine — this
 * screen's own Equipment list carries 243 options — so the fault on Complaint Details looks like a
 * broken cascade, not absent data.</p>
 *
 * <p>Once complaints exist this flow should pass unchanged. Widen the scan with {@code -Dstores=96}.</p>
 *
 * <p>The date window defaults to the last 2 years so the search has history to find; override with
 * {@code -DfromDate=} / {@code -DtoDate=} (in the screen's own {@code dd/MM/yyyy} format). Equipment Code
 * and Name default to the parts of the selected Equipment Type ({@code CODE - NAME}) so the filters stay
 * consistent with the selection; override with {@code -DequipmentCode=} / {@code -DequipmentName=}.</p>
 */
public class ComplaintResolution extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ComplaintResolution() { super("AncillaryServices_Equipment_ComplaintResolution"); }

    public static void main(String[] args) {
        ComplaintResolution t = new ComplaintResolution();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Equipment/Asset - Complaint Resolution",
                "Ancillary Services > Equipment/Asset > Complaint Resolution",
                "Search equipment complaints by date range, location, store, equipment, category and status. "
                        + "Read-only — nothing is saved.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        String fromDate = System.getProperty("fromDate", today.minusYears(2).format(DMY));
        String toDate = System.getProperty("toDate", today.format(DMY));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintResolution cr =
                new com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintResolution(page);

        // 1) Navigate
        boolean onScreen = cr.navigateViaMenu();
        step(page, "Open Complaint Resolution screen",
                "Click Ancillary Services -> Equipment/Asset -> Complaint Resolution",
                "The Complaint Resolution screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        cr.describeControls();   // diagnostics only

        // 2) From / To dates
        String dates = cr.enterDates(fromDate, toDate);
        step(page, "Enter From Date and To Date",
                "Enter From Date " + fromDate + " and To Date " + toDate,
                "Both dates are entered", dates, cr.datesSet() ? "PASS" : "FAIL");

        // 3) Location
        String loc = cr.selectLocation();
        boolean locOk = loc != null && !loc.startsWith("(");
        step(page, "Select Location", "Select a Location",
                "A Location is selected",
                locOk ? "Location = " + loc : "Location NOT selected " + loc, locOk ? "PASS" : "FAIL");

        // 4) Store
        String store = cr.selectStore();
        boolean storeOk = store != null && !store.startsWith("(");
        step(page, "Select Store", "Select a Store (list cascades from the Location)",
                "A Store is selected",
                storeOk ? "Store = " + store : "Store NOT selected " + store, storeOk ? "PASS" : "FAIL");

        // 5) Search + verify — only Location and Store are filled in. Equipment Type, Equipment Code,
        // Equipment Name, Complaint Category, Status, In House/Service Agent and Employee are all left
        // untouched, deliberately.
        String result = cr.clickSearchAndVerify();
        boolean found = cr.searchReturnedResults();

        // If the specified filters found nothing, broaden and scan stores. That distinguishes "these
        // filters were too narrow" from "there are no complaints at all", and the answer goes in the report.
        String broadened = "";
        int stored = -1;
        if (!found) {
            broadened = cr.searchScanningStores(Integer.getInteger("stores", 25));
            addSummary("Broadened retry", broadened);
            // Then settle WHY it is empty: is there any complaint stored at all? Complaint Details is
            // where complaints are created and listed, so it is the reference. Done last, because it
            // navigates away from the search screen.
            stored = cr.complaintsInMasterList();
            addSummary("Is there data to find?", cr.lastMasterListCheck);
        }
        // No details loaded in the table = FAIL, regardless of whether the emptiness is explainable. The
        // broadened retry and master-list check are still run and reported, since they tell you WHY the
        // table is empty (a data gap vs. a broken search) — but that explanation no longer flips the verdict.
        boolean searchBroken = !found && stored > 0;
        step(page, "Click Search & verify the result",
                "Click Search (GetComplaintGrid) and check the result grid",
                "The search returns matching complaints",
                (found
                    ? "PASSES because the search returned " + cr.lastRowCount + " row(s) -> " + result
                    : searchBroken
                    ? "FAILS — THE SEARCH IS NOT RETURNING STORED DATA. It returned no rows even after "
                      + "clearing the narrowing filters and scanning stores, yet complaint records DO "
                      + "exist: " + cr.lastMasterListCheck + ". So this is the search function, not a "
                      + "data gap. Detail: " + result + " | broadened retry: " + broadened
                    : "FAILS — no complaint rows loaded in the table. " + cr.lastMasterListCheck
                      + ". NOTE: create a complaint first (Complaint Details cannot do that today — its "
                      + "Equipment list is empty) and run this again. Detail: " + result
                      + " | broadened retry: " + broadened),
                found ? "PASS" : "FAIL");

        addSummary("From / To", fromDate + " -> " + toDate);
        addSummary("Location / Store", cr.lastLocation + " / " + cr.lastStore);
        addSummary("Result rows", String.valueOf(cr.lastRowCount));
        if (!cr.lastSearchMessage.isEmpty()) addSummary("App message", cr.lastSearchMessage);
    }
}
