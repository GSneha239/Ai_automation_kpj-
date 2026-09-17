package com.kpj.tests.AncillaryServices_page.Equipment_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ComplaintDetails — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Equipment/Asset &gt; <b>Complaint Details</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Equipment/Asset</b> → <b>Complaint Details</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select <b>Location</b> and <b>Store</b>.</li>
 *   <li>Select <b>Equipment</b>.</li>
 *   <li>Select <b>Complaint By</b>, <b>Reported By</b> and <b>Status</b>.</li>
 *   <li>Enter the <b>complaint details</b> text and <b>Entry Date</b>.</li>
 *   <li>Select <b>Reported To</b> and <b>Verified By</b>.</li>
 *   <li>Set <b>Problem Date</b>, <b>Reported Date</b> and <b>Complaint Category</b>.</li>
 *   <li>Set <b>Problem Time</b> and <b>Reported Time</b>.</li>
 *   <li>In <b>Down From</b>, enter the <b>Down Date</b>.</li>
 *   <li>Set the <b>Down Time</b>.</li>
 *   <li>Click <b>Add</b>, then <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Dates default to today; override with {@code -DentryDate=} / {@code -DproblemDate=} /
 * {@code -DreportedDate=} / {@code -DdownDate=} ({@code yyyy-MM-dd}). Times default to 09:00 / 09:30 /
 * 10:00; override with {@code -DproblemTime=} / {@code -DreportedTime=} / {@code -DdownTime=}.</p>
 *
 * <p>&#9888; A successful run CREATES a real equipment complaint in the target environment.</p>
 *
 * <h2>BLOCKED on devhis — the Equipment dropdown is empty</h2>
 * <p>As of 2026-08-06 this flow ends FAIL at <b>Select Equipment</b>, and Add + Save fail as a consequence.
 * The block is the environment's, not the script's: every other field on the form fills correctly
 * (Location, Store, Complaint By / Reported By / Status, the complaint text, Reported To / Verified By,
 * Problem &amp; Reported dates and times, Category, and the Down From date + time all pass).</p>
 *
 * <p>The Equipment list ({@code ComplaintDetails.equipmentid}) offers <b>zero</b> real options under every
 * combination tried:</p>
 * <ul>
 *   <li>all <b>96 stores</b> (run with {@code -Dstores=96}), each selected in turn and given time to cascade</li>
 *   <li>all <b>5 complaint types</b> — Equipment not functioning, Malfunctioning, No Power, Not functioning, testing</li>
 *   <li>the only <b>location</b> the screen offers (KPJ)</li>
 * </ul>
 *
 * <p>Without an equipment there is nothing to complain about, so {@code AddDetails()} adds no row
 * ({@code rowsAdded=0}) and {@code IUDOperations()} has nothing to save — both later failures are
 * consequences of this one.</p>
 *
 * <p><b>It is NOT missing master data.</b> Re-checked 2026-08-14: the sibling <b>Complaint Resolution</b>
 * screen lists <b>244 equipment</b> on the same environment, same login, same day, while this screen
 * yields zero under all 96 stores (each retried, so not a list read too early). Equipment exists; this
 * screen's Location/Store &rarr; Equipment cascade does not deliver it. An earlier version of this note
 * blamed the environment's data — that reading was wrong.</p>
 */
public class ComplaintDetails extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ComplaintDetails() { super("AncillaryServices_Equipment_ComplaintDetails"); }

    public static void main(String[] args) {
        ComplaintDetails t = new ComplaintDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Equipment/Asset - Complaint Details", "Ancillary Services > Equipment/Asset > Complaint Details",
                "&#9888; Creates a REAL equipment complaint: New, pick Location/Store/Equipment, fill the "
                        + "complaint details, dates and times, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String today = java.time.LocalDate.now().toString();
        String entryDate = System.getProperty("entryDate", today);
        String problemDate = System.getProperty("problemDate", today);
        String reportedDate = System.getProperty("reportedDate", today);
        String downDate = System.getProperty("downDate", today);
        String problemTime = System.getProperty("problemTime", "09:00");
        String reportedTime = System.getProperty("reportedTime", "09:30");
        String downTime = System.getProperty("downTime", "10:00");
        String complaintText = System.getProperty("complaintText",
                "Automated test complaint - equipment not powering on.");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintDetails cd =
                new com.kpj.pages.AncillaryServices_page.Equipment_page.ComplaintDetails(page);

        // 1) Navigate
        boolean onScreen = cd.navigateViaMenu();
        step(page, "Open Complaint Details screen",
                "Click Ancillary Services -> Equipment/Asset -> Complaint Details",
                "The Complaint Details list screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = cd.clickNew();
        step(page, "Click New", "Click New (AddComplaint) on the list screen",
                "The complaint form opens",
                formOpen ? "Complaint form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — complaint form not reached"); return; }

        cd.describeControls();   // diagnostics only
        if (Boolean.getBoolean("diagnose")) cd.diagnoseEquipmentAvailability();

        // 3) Location + Store
        String ls = cd.selectLocationAndStore();
        step(page, "Select Location and Store", "Select a Location, then a Store",
                "Location and Store are selected", ls,
                cd.locationAndStoreSelected() ? "PASS" : "FAIL");

        // 4) Equipment
        // -Dstores=N caps how many stores are scanned for one holding equipment. Default is ALL 96 the
        // screen offers: a cap of 40 left the report open to "you simply did not look far enough".
        String eq = cd.selectEquipment(Integer.getInteger("stores", 96));
        step(page, "Select Equipment",
                "Select an Equipment (the list cascades from Location/Store; most stores hold none, "
                        + "so stores are scanned until one does)",
                "An Equipment is selected",
                cd.equipmentSelected()
                    ? "PASSES because an equipment was selected: " + eq + " (store: " + cd.lastStore + ")"
                    : "FAILS because this screen's Equipment list is empty for EVERY store. Re-checked "
                      + "2026-08-14 by walking the form directly: it offers 1 location (KPJ) and 96 "
                      + "stores, and all 96 yield zero equipment — each retried up to 6 times, so this is "
                      + "not a list read before it had loaded. The data is NOT missing: the sibling "
                      + "Ancillary Services > Equipment/Asset > Complaint Resolution screen lists 244 "
                      + "equipment on the same environment, same login, same day. Equipment master data "
                      + "therefore exists, and it is this screen's Location/Store -> Equipment cascade "
                      + "that fails to deliver it. Detail: " + eq,
                cd.equipmentSelected() ? "PASS" : "FAIL");

        // 5) Complaint By / Reported By / Status
        String crs = cd.selectComplaintByReportedByStatus();
        step(page, "Select Complaint By, Reported By and Status",
                "In Complaint Details select Complaint By, Reported By and Status",
                "All three are selected", crs,
                cd.complaintByReportedByStatusSelected() ? "PASS" : "FAIL");

        // 6) Complaint text + Entry Date
        String ce = cd.enterComplaintDetailsAndEntryDate(complaintText, entryDate);
        step(page, "Enter complaint details and Entry Date",
                "Type the complaint details and set the Entry Date " + entryDate,
                "Complaint text and Entry Date are set", ce,
                cd.complaintTextAndEntryDateSet() ? "PASS" : "FAIL");

        // 7) Reported To + Verified By
        String rv = cd.selectReportedToAndVerifiedBy();
        step(page, "Select Reported To and Verified By", "Select Reported To and Verified By",
                "Both are selected", rv,
                cd.reportedToAndVerifiedBySelected() ? "PASS" : "FAIL");

        // 8) Problem/Reported dates + Category
        String dc = cd.selectDatesAndCategory(problemDate, reportedDate);
        step(page, "Select Problem Date, Reported Date and Complaint Category",
                "Set Problem Date " + problemDate + ", Reported Date " + reportedDate + ", pick a Category",
                "Both dates and a Category are set", dc,
                cd.datesAndCategorySet() ? "PASS" : "FAIL");

        // 9) Problem + Reported times
        String tt = cd.selectProblemAndReportedTimes(problemTime, reportedTime);
        step(page, "Select Problem Time and Reported Time",
                "Set the Problem Time and Reported Time (both share ng-model=inputTime; "
                        + "each is located by the date field it follows)",
                "Both times are set", tt, cd.timesSet() ? "PASS" : "FAIL");

        // 10) Down From date
        String dd = cd.enterDownDate(downDate);
        step(page, "Down From - enter Down Date", "Tick Down and set the Down Date " + downDate,
                "The Down Date is set",
                dd != null && !dd.startsWith("(") ? "DownDate = " + dd : "Down Date NOT set " + dd,
                dd != null && !dd.startsWith("(") ? "PASS" : "FAIL");

        // 11) Down time
        String dt = cd.selectDownTime(downTime);
        step(page, "Select Down Time", "Set the Down Time",
                "The Down Time is set",
                dt != null && !dt.startsWith("(") ? "DownTime = " + dt : "Down Time NOT set " + dt,
                dt != null && !dt.startsWith("(") ? "PASS" : "FAIL");

        // 12) Add
        String added = cd.clickAdd();
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        step(page, "Click Add", "Click Add (AddDetails) to add the complaint line",
                "A row is added to the grid",
                (addOk
                    ? "PASSES because a complaint line was added: " + added
                    : "FAILS as a CONSEQUENCE of the empty Equipment list, not as a separate defect: "
                      + "AddDetails() builds the line from the selected equipment, so with none selected "
                      + "it adds nothing and reports no error. Detail: " + added),
                addOk ? "PASS" : "FAIL");

        // 13) Save -> toast
        String toast = cd.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (IUDOperations); wait for the success toast",
                "'... saved successfully' toast",
                (ok
                    ? "PASSES because the screen answered with a success message: " + actual
                    : "FAILS as a CONSEQUENCE of the empty Equipment list: with no complaint line added "
                      + "there is nothing for IUDOperations() to save. " + actual),
                ok ? "PASS" : "FAIL");

        addSummary("Location / Store", cd.lastLocation + " / " + cd.lastStore);
        addSummary("Equipment", cd.lastEquipment);
        addSummary("Complaint By / Reported By / Status",
                cd.lastComplaintBy + " / " + cd.lastReportedBy + " / " + cd.lastStatus);
        addSummary("Reported To / Verified By", cd.lastReportedTo + " / " + cd.lastVerifiedBy);
        addSummary("Problem / Reported", cd.lastProblemDate + " " + cd.lastProblemTime
                + "  |  " + cd.lastReportedDate + " " + cd.lastReportedTime);
        addSummary("Category", cd.lastCategory);
        addSummary("Down From", cd.lastDownDate + " " + cd.lastDownTime);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
