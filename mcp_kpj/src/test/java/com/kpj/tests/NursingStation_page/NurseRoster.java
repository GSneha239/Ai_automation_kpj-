package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named NurseRoster — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Nurse Roster</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>NurseRoster</b>.</li>
 *   <li>Click <b>New Roster</b>.</li>
 *   <li>Select <b>Department</b> and <b>Nurse</b>.</li>
 *   <li>Enter the <b>Staff No</b>.</li>
 *   <li>Enter <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Select <b>Nursing Station</b>, <b>Team</b> and <b>Shift</b>.</li>
 *   <li>Click <b>Add</b> and verify the row was added.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>Dates default to a FUTURE window that shifts with the time of day. The sibling Doctor Roster screens
 * reject a roster whose date/person combination already exists, so a fixed window would pass once and fail
 * on every re-run. Pin one with {@code -DfromDate=} / {@code -DtoDate=} ({@code yyyy-MM-dd}).</p>
 *
 * <p>&#9888; A successful run CREATES a REAL nurse roster in the target environment.</p>
 */
public class NurseRoster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public NurseRoster() { super("NursingStation_NurseRoster"); }

    public static void main(String[] args) {
        NurseRoster t = new NurseRoster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Nurse Roster", "Nursing Station > Nurse Roster",
                "&#9888; Creates a REAL nurse roster: New Roster, pick Department + Nurse, enter Staff No, "
                        + "set the date range, pick Nursing Station + Team + Shift, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String staffNo = System.getProperty("staffNo", "1001");

        // Future window, shifted by time of day, so consecutive runs do not collide on the same dates.
        java.time.LocalDate base = java.time.LocalDate.now()
                .plusDays(30 + (java.time.LocalTime.now().toSecondOfDay() % 90));
        java.time.LocalDate end = base.plusDays(6);
        java.time.format.DateTimeFormatter DMY = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String isoFrom = System.getProperty("fromDate", base.toString());
        String isoTo = System.getProperty("toDate", end.toString());
        String textFrom = base.format(DMY);
        String textTo = end.format(DMY);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.NurseRoster nr =
                new com.kpj.pages.NursingStation_page.NurseRoster(page);

        // 1) Navigate
        boolean rendered = nr.navigateViaMenu(BASE);
        step(page, "Open Nurse Roster screen",
                "Click Nursing Station -> NurseRoster (retrying via the route and a full page load)",
                "The Nurse Roster screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + nr.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New Roster
        boolean formOpen = nr.clickNewRoster();
        nr.describeControls();   // diagnostics: the real ng-models and widget shapes
        step(page, "Click New Roster", "Click New Roster to open the roster form",
                "The roster form opens",
                formOpen ? "New Roster clicked (" + page.url() + ")" : "New Roster did NOT open a form",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — roster form not reached"); return; }

        // 3) Department + Nurse
        String dept = nr.selectDepartment();
        String nurse = nr.selectNurse();
        boolean dnOk = !dept.startsWith("(") && !nurse.startsWith("(");
        step(page, "Select department and nurse", "Select the Department, then the Nurse",
                "Both are selected", "Department = " + dept + " | Nurse = " + nurse,
                dnOk ? "PASS" : "FAIL");

        // 4) Staff No
        String staff = nr.enterStaffNo(staffNo);
        boolean staffOk = !staff.startsWith("(");
        step(page, "Enter staff no", "Enter the Staff No " + staffNo,
                "The Staff No is entered",
                staffOk ? "Staff No = " + staff : "Staff No NOT entered " + staff,
                staffOk ? "PASS" : "FAIL");

        // 5) From / To dates
        String dates = nr.enterDates(isoFrom, isoTo, textFrom, textTo);
        step(page, "Enter from date and to date",
                "Set From " + isoFrom + " and To " + isoTo + " (native date or masked text, whichever the "
                        + "field is)",
                "Both dates are set", dates, nr.datesSet() ? "PASS" : "FAIL");

        // 6) Nursing Station / Team / Shift
        String sts = nr.selectStationTeamShift();
        step(page, "Select nursing station, team and shift",
                "Select the Nursing Station, Team and Shift",
                "All three are selected", sts, nr.stationTeamShiftSelected() ? "PASS" : "FAIL");

        // 7) Add — and verify the row actually appeared
        String added = nr.clickAdd();
        step(page, "Click Add & verify the added row",
                "Click Add and confirm a roster row appears in the grid",
                "A roster row is added and visible", added, nr.rowAdded() ? "PASS" : "FAIL");

        // 8) Save -> toast
        String toast = nr.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + nr.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department / Nurse", nr.lastDepartment + " / " + nr.lastNurse);
        addSummary("Staff No", nr.lastStaffNo);
        addSummary("From / To", isoFrom + " -> " + isoTo);
        addSummary("Station / Team / Shift", nr.lastStation + " / " + nr.lastTeam + " / " + nr.lastShift);
        addSummary("Added row", nr.lastAdd);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
