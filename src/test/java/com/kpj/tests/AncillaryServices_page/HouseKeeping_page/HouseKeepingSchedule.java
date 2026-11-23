package com.kpj.tests.AncillaryServices_page.HouseKeeping_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named HouseKeepingSchedule — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; House Keeping &gt; <b>House Keeping Schedule</b> — both tabs, <b>Schedule
 * Template</b> then <b>Assigning Schedule</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>House Keeping</b> → <b>House Keeping Schedule</b>.</li>
 *   <li><b>Schedule Template</b> tab: enter the <b>Name of Schedule Template</b>, select <b>Activity</b> +
 *       <b>Day</b>, click <b>Add</b> (verify the row in <b>Activities Performed</b>), click
 *       <b>Save Template</b> (verify the success toast and the name in the <b>Schedule Template Name</b>
 *       list).</li>
 *   <li><b>Assigning Schedule</b> tab: select the just-saved <b>Schedule</b> + <b>Employee Name</b>, enter
 *       the <b>Assigned Date</b>, click <b>Add</b> (pulls the Schedule's own activity into
 *       <b>Assigned/Change Activities</b>), select <b>Activity</b> + <b>Day</b> and enter <b>Duration</b>,
 *       click <b>Add</b> again (a second, manually-specified row), click <b>Save Schedule</b> (verify the
 *       success toast).</li>
 * </ol>
 *
 * <p>&#9888; A successful run CREATES a real schedule template AND a real employee schedule assignment in
 * the target environment (each run uses a timestamped template name so re-runs never collide with a
 * previous one). Verified live 2026-09-08: on the Schedule Template tab, Add works with only Activity + Day
 * set (Start Time keeps its default, Duration/Repeat are optional) and Save Template answers "Template
 * saved successfully."; on the Assigning Schedule tab, {@code AddEmployeeActivity()} only requires Employee
 * to be set — with Activity/Day left unselected it pulls the chosen Schedule's own activity/day instead —
 * and Save Schedule answers "Employee Schedule Saved Successfully."</p>
 */
public class HouseKeepingSchedule extends DevHisBase {

    public HouseKeepingSchedule() { super("AncillaryServices_HouseKeeping_HouseKeepingSchedule"); }

    public static void main(String[] args) {
        HouseKeepingSchedule t = new HouseKeepingSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - House Keeping - House Keeping Schedule (Schedule Template)",
                "Ancillary Services > House Keeping > House Keeping Schedule > Schedule Template",
                "&#9888; Creates a REAL schedule template: name, Activity, Day, Add, Save Template.");

        String templateName = System.getProperty("templateName", "AutoTest Template " + System.currentTimeMillis());
        String activity = System.getProperty("activity");   // null => first real option
        String day = System.getProperty("day");             // null => first real option

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingSchedule hk =
                new com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingSchedule(page);

        // 1) Navigate
        boolean onScreen = hk.navigateViaMenu();
        step(page, "Open House Keeping Schedule screen",
                "Click Ancillary Services -> House Keeping -> House Keeping Schedule",
                "The House Keeping Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Schedule Template tab
        boolean tabOk = hk.clickScheduleTemplateTab();
        step(page, "Click Schedule Template tab", "Click the Schedule Template tab",
                "The Schedule Template form (Name of Schedule Template, Activities Performed) is shown",
                tabOk ? "Schedule Template tab opened" : "Tab did NOT open",
                tabOk ? "PASS" : "FAIL");
        if (!tabOk) { addSummary("Result", "FAILED — Schedule Template tab not reached"); return; }

        // 3) Name of Schedule Template
        String nameEntered = hk.enterTemplateName(templateName);
        boolean nameOk = nameEntered != null && !nameEntered.isEmpty() && !nameEntered.startsWith("(");
        step(page, "Enter name of Schedule Template", "Enter \"" + templateName + "\" as the Name of Schedule Template",
                "The template name is entered",
                nameOk ? "Name = " + nameEntered : "Name NOT entered " + nameEntered,
                nameOk ? "PASS" : "FAIL");

        // 4) Activity + Day
        String activityPicked = hk.selectActivity(activity);
        String dayPicked = hk.selectDay(day);
        boolean selOk = hk.activitySelected() && hk.daySelected();
        step(page, "Select Activity and Day",
                "Select an Activity" + (activity == null ? " (first option)" : " (\"" + activity + "\")")
                        + " and a Day" + (day == null ? " (first option)" : " (\"" + day + "\")"),
                "Both Activity and Day are selected",
                "Activity = " + activityPicked + ", Day = " + dayPicked,
                selOk ? "PASS" : "FAIL");

        // 5) Add -> verify row in Activities Performed
        String newRow = hk.clickAdd();
        boolean addOk = newRow != null && !newRow.isEmpty();
        step(page, "Click Add", "Click Add",
                "A new row is appended to the Activities Performed grid",
                addOk ? "Activities Performed row added: " + newRow : "No row was added to Activities Performed",
                addOk ? "PASS" : "FAIL");

        boolean rowHasDetails = addOk && isReal(activityPicked) && newRow.contains(activityPicked)
                && isReal(dayPicked) && newRow.contains(dayPicked);
        step(page, "Verify Activities Performed details",
                "Check the new Activities Performed row carries the selected Activity + Day",
                "The row shows Activity = " + activityPicked + " and Day = " + dayPicked,
                rowHasDetails
                    ? "PASSES because Activities Performed shows: " + newRow
                    : "FAILS — Activities Performed row was \"" + newRow + "\", expected it to contain Activity \""
                      + activityPicked + "\" and Day \"" + dayPicked + "\"",
                rowHasDetails ? "PASS" : "FAIL");

        // 6) Save Template -> toast + Schedule Template Name list
        String toast = hk.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean saveOk = tl.contains("success") || tl.contains("saved");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : toast;
        step(page, "Click Save Template & success toast", "Click Save Template",
                "'Template saved successfully.' toast",
                saveOk ? "PASSES because the screen answered with a success message: " + actual
                       : "FAILS — Submit not confirmed. Server returned: \"" + actual + "\"",
                saveOk ? "PASS" : "FAIL");

        java.util.List<String> templateNames = hk.scheduleTemplateNames();
        boolean nameListed = templateNames.stream().anyMatch(n -> n.contains(templateName));
        step(page, "Verify saved template name in Schedule Template Name list",
                "Check the Schedule Template Name list for \"" + templateName + "\"",
                "\"" + templateName + "\" appears as a row in the Schedule Template Name list",
                nameListed
                    ? "PASSES because the list now contains: " + templateName
                    : "FAILS — WHAT: \"" + templateName + "\" was not found in the Schedule Template Name list. "
                      + "WHERE: the list holds " + templateNames.size() + " row(s): " + templateNames,
                nameListed ? "PASS" : "FAIL");

        addSummary("Template name", templateName);
        addSummary("Activity", activityPicked);
        addSummary("Day", dayPicked);
        addSummary("Activities Performed row", newRow);
        addSummary("Schedule Template Name list", templateNames.toString());
        addSummary("Result", saveOk && nameListed ? toast : "Not fully confirmed (toast: \"" + toast + "\")");

        // ==================== Assigning Schedule tab ====================

        String employee = System.getProperty("employee");                 // null => first real option
        String assignActivity = System.getProperty("assignActivity", "Cleaning");
        String assignDay = System.getProperty("assignDay", "Monday");
        String duration = System.getProperty("duration", "30");

        // 7) Assigning Schedule tab
        boolean assignTabOk = hk.clickAssigningScheduleTab();
        step(page, "Click Assigning Schedule tab", "Click the Assigning Schedule tab",
                "The Assigning Schedule form (Schedule, Employee Name, Assigned Date) is shown",
                assignTabOk ? "Assigning Schedule tab opened" : "Tab did NOT open",
                assignTabOk ? "PASS" : "FAIL");
        if (!assignTabOk) { addSummary("Result (Assigning Schedule)", "FAILED — Assigning Schedule tab not reached"); return; }

        // 8) Schedule (the template just saved above, so it is guaranteed to exist in the dropdown)
        String schedulePicked = hk.selectSchedule(templateName);
        boolean scheduleOk = hk.scheduleSelected();
        step(page, "Select Schedule", "Select Schedule \"" + templateName + "\"",
                "The just-saved template is selected as the Schedule",
                scheduleOk ? "Schedule = " + schedulePicked : "Schedule NOT selected " + schedulePicked,
                scheduleOk ? "PASS" : "FAIL");

        // 9) Employee Name
        String employeePicked = hk.selectEmployee(employee);
        boolean employeeOk = hk.employeeSelected();
        step(page, "Select Employee Name",
                "Select an Employee Name" + (employee == null ? " (first option)" : " (\"" + employee + "\")"),
                "An Employee is selected",
                employeeOk ? "Employee = " + employeePicked : "Employee NOT selected " + employeePicked,
                employeeOk ? "PASS" : "FAIL");

        // 10) Assigned Date
        String dateEntered = hk.enterAssignedDate();
        boolean dateOk = hk.assignedDateEntered();
        step(page, "Enter assigned date", "Enter today's date as the Assigned Date",
                "The Assigned Date is entered",
                dateOk ? "Assigned Date = " + dateEntered : "Assigned Date NOT entered " + dateEntered,
                dateOk ? "PASS" : "FAIL");

        // 11) Add (1st) -> the Schedule's own template activity, keyed off Employee + Date alone
        String assignRow1 = hk.clickAddEmployeeActivity();
        boolean addOk1 = assignRow1 != null && !assignRow1.isEmpty();
        step(page, "Click Add (from Schedule)", "Click Add with the Schedule + Employee + Assigned Date set",
                "A new row (seeded from the selected Schedule's own activity) is appended to Assigned/Change Activities",
                addOk1 ? "Assigned/Change Activities row added: " + assignRow1 : "No row was added to Assigned/Change Activities",
                addOk1 ? "PASS" : "FAIL");

        // 12) Activity + Day (manual, for a second/custom row)
        String assignActivityPicked = hk.selectAssignActivity(assignActivity);
        String assignDayPicked = hk.selectAssignDay(assignDay);
        boolean assignSelOk = isReal(assignActivityPicked) && isReal(assignDayPicked);
        step(page, "Select Activity and Day", "Select Activity \"" + assignActivity + "\" and Day \"" + assignDay + "\"",
                "Both Activity and Day are selected",
                "Activity = " + assignActivityPicked + ", Day = " + assignDayPicked,
                assignSelOk ? "PASS" : "FAIL");

        // 13) Duration
        String durationEntered = hk.enterDuration(duration);
        boolean durationOk = isReal(durationEntered);
        step(page, "Enter duration", "Enter Duration = " + duration + " minutes",
                "The Duration is entered",
                durationOk ? "Duration = " + durationEntered : "Duration NOT entered " + durationEntered,
                durationOk ? "PASS" : "FAIL");

        // 14) Add (2nd) -> the manually-selected Activity/Day/Duration
        String assignRow2 = hk.clickAddEmployeeActivity();
        boolean addOk2 = assignRow2 != null && !assignRow2.isEmpty()
                && assignRow2.contains(assignActivityPicked) && assignRow2.contains(assignDayPicked);
        step(page, "Click Add (manual)", "Click Add with Activity + Day + Duration set",
                "A second row, carrying the manually-selected Activity + Day, is appended to Assigned/Change Activities",
                addOk2
                    ? "PASSES because Assigned/Change Activities shows: " + assignRow2
                    : "FAILS — Assigned/Change Activities row was \"" + assignRow2 + "\", expected it to contain Activity \""
                      + assignActivityPicked + "\" and Day \"" + assignDayPicked + "\"",
                addOk2 ? "PASS" : "FAIL");

        // 15) Save Schedule -> toast
        String scheduleToast = hk.saveScheduleAndGetToast();
        String stl = scheduleToast == null ? "" : scheduleToast.toLowerCase();
        boolean scheduleSaveOk = stl.contains("success") || stl.contains("saved");
        String scheduleActual = scheduleToast == null || scheduleToast.isEmpty() ? "No toast appeared" : scheduleToast;
        step(page, "Click Save Schedule & success toast", "Click Save Schedule",
                "'Employee Schedule Saved Successfully.' toast",
                scheduleSaveOk ? "PASSES because the screen answered with a success message: " + scheduleActual
                               : "FAILS — Submit not confirmed. Server returned: \"" + scheduleActual + "\"",
                scheduleSaveOk ? "PASS" : "FAIL");

        addSummary("Schedule", schedulePicked);
        addSummary("Employee", employeePicked);
        addSummary("Assigned Date", dateEntered);
        addSummary("Assigned/Change Activities rows", assignRow1 + " | " + assignRow2);
        addSummary("Result (Assigning Schedule)", scheduleSaveOk ? scheduleToast : "Not confirmed (toast: \"" + scheduleToast + "\")");
    }

    private static boolean isReal(String v) {
        return v != null && !v.isEmpty() && !v.startsWith("(");
    }
}
