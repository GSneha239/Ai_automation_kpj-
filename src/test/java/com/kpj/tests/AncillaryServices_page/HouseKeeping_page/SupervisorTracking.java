package com.kpj.tests.AncillaryServices_page.HouseKeeping_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SupervisorTracking — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; House Keeping &gt; <b>Supervisor Tracking</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li><b>Setup</b> — via {@link com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingSchedule}:
 *       create a fresh Schedule Template and assign it to a known employee, dated today. Supervisor
 *       Tracking has no data to show for most employees (verified live — see that page object's javadoc),
 *       so this guarantees the Activities Assigned grid is non-empty for the employee this test uses.</li>
 *   <li>Click <b>Ancillary Services</b> → <b>House Keeping</b> → <b>Supervisor Tracking</b>.</li>
 *   <li>Select <b>Employee Name</b> (the one just assigned) → verify <b>Activities Assigned</b> populates.</li>
 *   <li>Enter <b>From Date</b> and <b>To Date</b>.</li>
 *   <li>Select <b>Activity</b> and <b>Status</b>, enter a <b>Remark</b>.</li>
 *   <li>Click <b>Change</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES a real schedule template, a real employee schedule assignment, AND
 * updates a real Supervisor Tracking record in the target environment. Verified live 2026-09-08:
 * {@code GetActivityDetails()} — fired by the Employee Name select's own {@code ng-change}, no separate
 * Search click needed — populates Activities Assigned; From Date/To Date are entered but are NOT part of
 * the actual server request (confirmed by intercepting the XHR), so they do not gate anything here; Change
 * ({@code Updatedata()}) answers with "Supervisor Tracking records updated successfully." and then resets
 * the whole form (Employee Name reverts to "-Select-"), the same "clears on save" behavior seen on the
 * other House Keeping Schedule tabs.</p>
 */
public class SupervisorTracking extends DevHisBase {

    public SupervisorTracking() { super("AncillaryServices_HouseKeeping_SupervisorTracking"); }

    public static void main(String[] args) {
        SupervisorTracking t = new SupervisorTracking();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - House Keeping - Supervisor Tracking",
                "Ancillary Services > House Keeping > Supervisor Tracking",
                "&#9888; Creates a REAL schedule template + employee assignment (setup), then updates a REAL "
                        + "Supervisor Tracking record: Employee, dates, Activity, Status, Remark, Change.");

        // "Employee 2" is pinned rather than left to "first option": verified live that some employees
        // (e.g. the "Sancy Admin" seed/admin employee) never show tracking data even once assigned a
        // schedule, while "Employee 2" reliably does — see the page object's javadoc.
        String employee = System.getProperty("employee", "Employee 2");
        String activity = System.getProperty("activity", "Cleaning");
        String day = System.getProperty("day");                 // null => first real option
        String status = System.getProperty("status", "Completed");
        String remark = System.getProperty("remark", "AutoTest remark " + System.currentTimeMillis());
        String templateName = System.getProperty("templateName", "AutoTest Template " + System.currentTimeMillis());

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        // ==================== Setup: guarantee tracking data for `employee` ====================

        com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingSchedule hk =
                new com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingSchedule(page);

        boolean hkScreenOk = hk.navigateViaMenu();
        step(page, "[Setup] Open House Keeping Schedule screen",
                "Click Ancillary Services -> House Keeping -> House Keeping Schedule",
                "The House Keeping Schedule screen is shown",
                hkScreenOk ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                hkScreenOk ? "PASS" : "FAIL");
        if (!hkScreenOk) { addSummary("Result", "FAILED — setup could not reach House Keeping Schedule"); return; }

        boolean templateTabOk = hk.clickScheduleTemplateTab();
        String templateNameEntered = hk.enterTemplateName(templateName);
        String setupActivity = hk.selectActivity(activity);
        String setupDay = hk.selectDay(day);
        String setupRow = hk.clickAdd();
        String templateToast = hk.saveAndGetToast();
        boolean templateSaved = templateToast != null && templateToast.toLowerCase().matches(".*(success|saved).*");
        step(page, "[Setup] Create Schedule Template",
                "Schedule Template tab: name \"" + templateName + "\", Activity \"" + setupActivity
                        + "\", Day \"" + setupDay + "\", Add, Save Template",
                "A new Schedule Template is saved",
                templateTabOk && templateSaved
                    ? "Template \"" + templateName + "\" saved (row: " + setupRow + "; toast: " + templateToast + ")"
                    : "Template setup did NOT complete (tab=" + templateTabOk + ", row=" + setupRow + ", toast=\"" + templateToast + "\")",
                templateTabOk && templateSaved ? "PASS" : "FAIL");
        if (!(templateTabOk && templateSaved)) { addSummary("Result", "FAILED — setup could not save the Schedule Template"); return; }

        boolean assignTabOk = hk.clickAssigningScheduleTab();
        String schedulePicked = hk.selectSchedule(templateName);
        String employeePicked = hk.selectEmployee(employee);
        String dateEntered = hk.enterAssignedDate();
        String assignRow = hk.clickAddEmployeeActivity();
        String assignToast = hk.saveScheduleAndGetToast();
        boolean assignSaved = assignToast != null && assignToast.toLowerCase().matches(".*(success|saved).*");
        step(page, "[Setup] Assign the Schedule to " + employee,
                "Assigning Schedule tab: Schedule \"" + templateName + "\", Employee \"" + employee
                        + "\", Assigned Date " + dateEntered + ", Add, Save Schedule",
                "The schedule is assigned to " + employee,
                assignTabOk && assignSaved
                    ? "Assigned (row: " + assignRow + "; toast: " + assignToast + ")"
                    : "Assignment setup did NOT complete (tab=" + assignTabOk + ", row=" + assignRow + ", toast=\"" + assignToast + "\")",
                assignTabOk && assignSaved ? "PASS" : "FAIL");
        if (!(assignTabOk && assignSaved)) { addSummary("Result", "FAILED — setup could not assign the schedule to " + employee); return; }

        // ==================== Supervisor Tracking ====================

        com.kpj.pages.AncillaryServices_page.HouseKeeping_page.SupervisorTracking st =
                new com.kpj.pages.AncillaryServices_page.HouseKeeping_page.SupervisorTracking(page);

        boolean stScreenOk = st.navigateViaMenu();
        step(page, "Open Supervisor Tracking screen",
                "Click Ancillary Services -> House Keeping -> Supervisor Tracking",
                "The Supervisor Tracking screen is shown",
                stScreenOk ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                stScreenOk ? "PASS" : "FAIL");
        if (!stScreenOk) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 1) Employee Name -> auto-fetches (ng-change="GetActivityDetails()")
        String employeeSelected = st.selectEmployee(employee);
        boolean employeeOk = employeeSelected != null && !employeeSelected.isEmpty() && !employeeSelected.startsWith("(");
        step(page, "Select Employee Name", "Select Employee Name \"" + employee + "\"",
                "The Employee is selected",
                employeeOk ? "Employee = " + employeeSelected : "Employee NOT selected " + employeeSelected,
                employeeOk ? "PASS" : "FAIL");

        // 2) From Date / To Date (entered but confirmed NOT part of the server request — see class javadoc)
        String fromDate = st.enterFromDate();
        String toDate = st.enterToDate();
        boolean datesOk = isReal(fromDate) && isReal(toDate);
        step(page, "Enter from date and to date", "Enter From Date and To Date (today)",
                "Both dates are entered",
                datesOk ? "From Date = " + fromDate + ", To Date = " + toDate
                        : "Dates NOT entered (From = " + fromDate + ", To = " + toDate + ")",
                datesOk ? "PASS" : "FAIL");

        // 3) Verify data populated in Activities Assigned
        java.util.List<String> rows = st.activitiesAssignedRows();
        boolean dataOk = !rows.isEmpty();
        step(page, "Verify data populated in Activities Assigned",
                "Check the Activities Assigned grid for " + employee,
                "At least one row is shown (the schedule just assigned in setup)",
                dataOk
                    ? "PASSES because Activities Assigned shows " + rows.size() + " row(s): " + rows
                    : "FAILS — WHAT: Activities Assigned is empty for \"" + employee + "\" despite a schedule "
                      + "having just been assigned to them in setup (toast: \"" + assignToast + "\").",
                dataOk ? "PASS" : "FAIL");
        if (!dataOk) { addSummary("Result", "FAILED — Activities Assigned had no data to act on"); return; }

        // 4) Activity
        String activityPicked = st.selectActivity(activity);
        boolean activityOk = isReal(activityPicked);
        step(page, "Select activity", "Select Activity \"" + activity + "\"",
                "The Activity is selected",
                activityOk ? "Activity = " + activityPicked : "Activity NOT selected " + activityPicked,
                activityOk ? "PASS" : "FAIL");

        // 5) Status
        String statusPicked = st.selectStatus(status);
        boolean statusOk = isReal(statusPicked);
        step(page, "Select status", "Select Status \"" + status + "\"",
                "The Status is selected",
                statusOk ? "Status = " + statusPicked : "Status NOT selected " + statusPicked,
                statusOk ? "PASS" : "FAIL");

        // 6) Remark
        String remarkEntered = st.enterRemark(remark);
        boolean remarkOk = remarkEntered != null && remarkEntered.equals(remark);
        step(page, "Enter remark", "Enter Remark \"" + remark + "\"",
                "The Remark is entered",
                remarkOk ? "Remark = " + remarkEntered : "Remark NOT entered " + remarkEntered,
                remarkOk ? "PASS" : "FAIL");

        // 7) Change -> success toast
        String toast = st.clickChangeAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean changeOk = tl.contains("success") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : toast;
        step(page, "Click Change & success toast", "Click Change",
                "'Supervisor Tracking records updated successfully.' toast",
                changeOk ? "PASSES because the screen answered with a success message: " + actual
                         : "FAILS — Update not confirmed. Server returned: \"" + actual + "\"",
                changeOk ? "PASS" : "FAIL");

        addSummary("Employee", employeeSelected);
        addSummary("From Date / To Date", fromDate + " / " + toDate);
        addSummary("Activities Assigned rows (before Change)", String.valueOf(rows.size()));
        addSummary("Activity / Status / Remark", activityPicked + " / " + statusPicked + " / " + remarkEntered);
        addSummary("Result", changeOk ? toast : "Not confirmed (toast: \"" + toast + "\")");
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
