package com.kpj.tests.AncillaryServices_page.DoctorRoster_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MORoster — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Doctor Roster &gt; <b>MO Roster</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>MO Roster</b>.</li>
 *   <li>Click <b>New Roster</b>.</li>
 *   <li>Select <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Select <b>Department</b>, <b>Shift</b> and <b>Medical Officer</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Dates default to a FUTURE window that shifts with the time of day, because DevHIS rejects a roster
 * whose date/officer/shift combination already exists — a fixed window would pass once and fail on every
 * re-run. Pin a range with {@code -DfromDate=} / {@code -DtoDate=} ({@code yyyy-MM-dd}).</p>
 *
 * <p>&#9888; A successful run CREATES a real MO roster in the target environment.</p>
 *
 * <h2>KNOWN DEFECT — Save always fails on this screen</h2>
 * <p>As of 2026-08-06 the final Save returns a bare <b>"×KPJ PortalError!"</b> every time, so this flow
 * ends FAIL by design — the failure is the app's, not the script's. Steps 1-6 all pass: the form opens,
 * the dates set, Department / Shift / Medical Officer all take real values, and Add appends the roster
 * row ({@code rowsAdded=1}).</p>
 *
 * <p>Reproduced across <b>7 combinations</b>, all with a future date window so no duplicate rule applies:</p>
 * <ul>
 *   <li>Departments: Administration, Emergency Department, Hand &amp; Microsurgery (Orthopaedic)</li>
 *   <li>Shifts: AM, PM, NIGHT, AOC/SSB - ON CALL</li>
 *   <li>Medical Officers: Doctor 213 TIEBA, A, Demo Doctor</li>
 * </ul>
 *
 * <p>For contrast, the sibling <b>Consultant On Call</b> screen — same {@code fnGoToNewRoster} /
 * {@code fnAddRow} / {@code fnSaveRoster} handlers — saves successfully with comparable input, and it
 * reports duplicates with a <i>specific</i> message ("One or more roster rows already exist..."), so this
 * bare "Error!" is a different failure, not a duplicate clash.</p>
 *
 * <p>Narrow it further with {@code -Ddepartment=} / {@code -Dshift=} / {@code -DfromDate=}. Once the
 * underlying defect is fixed this flow should pass unchanged.</p>
 */
public class MORoster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public MORoster() { super("AncillaryServices_DoctorRoster_MORoster"); }

    public static void main(String[] args) {
        MORoster t = new MORoster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("MO Roster", "Ancillary Services > Doctor Roster > MO Roster",
                "&#9888; Creates a REAL MO roster: New Roster, pick From/To dates, pick Department + Shift + "
                        + "Medical Officer, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        java.time.LocalDate base = java.time.LocalDate.now()
                .plusDays(30 + (java.time.LocalTime.now().toSecondOfDay() % 90));
        String fromDate = System.getProperty("fromDate", base.toString());
        String toDate = System.getProperty("toDate", base.plusDays(6).toString());

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.DoctorRoster_page.MORoster roster =
                new com.kpj.pages.AncillaryServices_page.DoctorRoster_page.MORoster(page);

        // 1) Navigate
        boolean onScreen = roster.navigateViaMenu();
        step(page, "Open MO Roster screen",
                "Click Ancillary Services -> Doctor Roster -> MO Roster",
                "The MO Roster screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New Roster
        boolean formOpen = roster.clickNewRoster();
        step(page, "Click New Roster", "Click New Roster (fnGoToNewRoster)",
                "The new roster form opens",
                formOpen ? "Roster form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — roster form not reached"); return; }

        roster.describeControls();   // diagnostics only

        // 3) From Date / To Date
        String dates = roster.selectDates(fromDate, toDate);
        boolean datesOk = roster.datesSet();
        step(page, "Select From Date and To Date",
                "Set From Date " + fromDate + " and To Date " + toDate,
                "Both dates are set", dates, datesOk ? "PASS" : "FAIL");

        // 4) Department / Shift / Medical Officer
        // -Ddepartment= / -Dshift= pin a specific option; default = first real option.
        String dso = roster.selectDepartmentShiftAndOfficer(
                System.getProperty("department"), System.getProperty("shift"));
        boolean dsoOk = roster.departmentShiftOfficerSelected();
        step(page, "Select Department, Shift and Medical Officer",
                "Select a Department, a Shift and a Medical Officer (ui-select)",
                "Department, Shift and Medical Officer are all selected", dso, dsoOk ? "PASS" : "FAIL");

        // 5) Add the row
        String added = roster.clickAdd();
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        step(page, "Click Add", "Click Add (fnAddRow) to add the roster line",
                "A roster row is added to the grid", added, addOk ? "PASS" : "FAIL");

        // 6) Save -> success toast
        String toast = roster.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (fnSaveRoster); wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("From Date / To Date", fromDate + " -> " + toDate);
        addSummary("Department", roster.lastDepartment);
        addSummary("Shift", roster.lastShift);
        addSummary("Medical Officer", roster.lastMedicalOfficer);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
