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
 * <p>Dates default to a FUTURE window offset by {@code nanoTime}, because DevHIS rejects a roster whose
 * date/officer/shift combination already exists — a fixed window would pass once and fail on every
 * re-run, and re-runs can happen only minutes (or seconds) apart. Pin a range with {@code -DfromDate=} /
 * {@code -DtoDate=} ({@code yyyy-MM-dd}).</p>
 *
 * <p>&#9888; A successful run CREATES a real MO roster in the target environment.</p>
 *
 * <h2>UPDATE 2026-09-08 — the original "always fails" defect no longer reproduces</h2>
 * <p>On 2026-08-06, Save returned a bare, unexplained <b>"×KPJ PortalError!"</b> on every attempt (7
 * Department/Shift/Officer combinations tried). Re-run live today with the same script, Save instead
 * returned a specific, sensible rejection: <b>"One or more roster rows already exist for the selected
 * date / medical officer / shift combination."</b> — the exact wording the sibling <b>Consultant On
 * Call</b> screen has always used for genuine duplicates. That reads as the app correctly enforcing
 * uniqueness, not the earlier mystery failure — the collision itself is an artifact of this test (and
 * others like it) always defaulting to the first Department/Shift/Officer option, so a wide future date
 * window still fills up after enough automated runs.</p>
 *
 * <p>So this flow now retries on exactly that rejection —
 * {@link com.kpj.pages.AncillaryServices_page.DoctorRoster_page.MORoster#addAndSaveWithDuplicateRetry}
 * reopens a fresh roster form with a new random future date window (same Department/Shift preference) and
 * tries again, up to 5 times — to demonstrate the real happy path rather than get stuck reporting a
 * duplicate as if it were the original defect. Any OTHER rejection (including a bare "Error!", should it
 * resurface) is reported as-is, not retried.</p>
 *
 * <p>Narrow it with {@code -Ddepartment=} / {@code -Dshift=} / {@code -DfromDate=} / {@code -Dattempts=}.</p>
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
        meta("Ancillary Services - Doctor Roster - MO Roster", "Ancillary Services > Doctor Roster > MO Roster",
                "&#9888; Creates a REAL MO roster: New Roster, pick From/To dates, pick Department + Shift + "
                        + "Medical Officer, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // DevHIS rejects a roster whose date/officer/shift combination already exists, so the offset must
        // change on every run, not just every couple of minutes. Time-of-day-in-seconds % 90 only advances
        // one day every 90 seconds, so two runs a few minutes apart (as happens while iterating on this
        // test) land on the SAME window and collide. nanoTime's low bits change on every JVM start, so the
        // offset is effectively unique per run regardless of how close together runs happen.
        java.time.LocalDate base = java.time.LocalDate.now()
                .plusDays(30 + Math.floorMod(System.nanoTime(), 300));
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

        // 5) Add the row, then 6) Save -> success toast, retrying on a "already exists" duplicate
        // rejection (a wide future date window still collides after enough automated runs default to
        // the same Department/Shift/Officer — see the class javadoc's 2026-09-08 update).
        int maxAttempts = Integer.getInteger("attempts", 5);
        String toast = roster.addAndSaveWithDuplicateRetry(
                System.getProperty("department"), System.getProperty("shift"), maxAttempts);
        String added = roster.lastAddResult;
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        step(page, "Click Add", "Click Add (fnAddRow) to add the roster line"
                        + (roster.attemptsTried > 1 ? " (attempt " + roster.attemptsTried + ")" : ""),
                "A roster row is added to the grid", added, addOk ? "PASS" : "FAIL");

        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast + (roster.attemptsTried > 1 ? "  (attempt " + roster.attemptsTried + " of "
                                                              + maxAttempts + ", after earlier duplicate rejections)" : "")
                      : "Save not confirmed after " + roster.attemptsTried + " attempt(s) — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (fnSaveRoster); wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("From Date / To Date", fromDate + " -> " + toDate);
        addSummary("Department", roster.lastDepartment);
        addSummary("Shift", roster.lastShift);
        addSummary("Medical Officer", roster.lastMedicalOfficer);
        addSummary("Attempts tried", roster.attemptsTried + " of " + maxAttempts);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
