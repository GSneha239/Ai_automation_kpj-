package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DepartmentSchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Department Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Department Schedule</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b>, <b>Start Time</b>, <b>End Time</b>, <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class DepartmentSchedule extends DevHisBase {

    public DepartmentSchedule() { super("ApplicationConfig_Location_DepartmentSchedule"); }

    public static void main(String[] args) {
        DepartmentSchedule t = new DepartmentSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Department Schedule",
                "Application Configuration > Location > Department Schedule",
                "Add a department schedule: Department, Start/End Time, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.DepartmentSchedule ds =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.DepartmentSchedule(page);

        boolean onScreen = ds.navigateViaMenu();
        step(page, "Open Department Schedule screen",
                "Click Application Configuration -> Location -> Department Schedule",
                "The Department Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + ds.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("schedule links => " + ds.findScheduleLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + ds.describeForm());

        boolean added = ds.clickAdd() && ds.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + ds.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }

        // Location is mandatory on this form even though the flow does not name it.
        String loc = ds.selectLocation();
        System.out.println("Location (mandatory, not in the flow) = " + loc);

        // 3) Department
        String dept = ds.selectDepartment(0);
        step(page, "Select Department", "Choose the Department", "A Department is selected",
                dept.isEmpty() ? "Department NOT selected" : "Department = " + dept, dept.isEmpty() ? "FAIL" : "PASS");

        // 4) Start / End time
        String times = ds.fillTimes("09:00 AM", "05:00 PM");
        boolean timesOk = !ds.lastStart.isEmpty() && !ds.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // 5) Days
        String days = ds.tickDays("Monday", "Tuesday", "Wednesday");
        boolean daysOk = !days.isEmpty();
        step(page, "Select Days", "Tick the days of the schedule", "The days are ticked",
                daysOk ? "Ticked: " + days : "No day ticked", daysOk ? "PASS" : "FAIL");

        // 6) Inner Add — commits the schedule line
        String inner = ds.clickInnerAdd();
        System.out.println("schedule grid now: " + ds.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(ds.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // 7) Submit. A department that already has a schedule is rejected without writing anything; the retry then
        // starts from a FRESH form, because the form keeps its schedule id and Submit would UPDATE that schedule.
        String toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptIndex = ds.lastDepartmentIndex + 1;
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            int held = ds.scheduleId();
            if (held > 0) {
                System.out.println("Submit: the form is holding schedule id " + held + " — Submit would UPDATE it; taking a fresh form");
                tries.append(attempt == 0 ? "" : " | ").append("(skipped: form held schedule ").append(held).append(')');
            } else {
                toast = ds.submitAndGetToast();
                tl = toast == null ? "" : toast.toLowerCase();
                ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
                tries.append(attempt == 0 ? "" : " | ").append(ds.lastDepartment).append(" -> \"").append(toast).append('"');
                if (ok) break;
                if (!ds.lastToasts.toLowerCase().contains("already")) break;
            }
            if (!ds.clickBack()) break;
            if (!(ds.clickAdd() && ds.addFormOpen())) break;
            String refilled = ds.fillAll(deptIndex, "09:00 AM", "05:00 PM", "Monday", "Tuesday", "Wednesday");
            deptIndex = ds.lastDepartmentIndex + 1;
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (ds.lastDepartment.isEmpty()) break;
        }
        step(page, "Click Submit", "Click Submit", "The department schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (ds.lastSaveApi.isEmpty() ? "" : "  [" + ds.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // 8) Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(ds.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department", ds.lastDepartment);
        addSummary("Schedule", ds.lastDays + "  " + ds.lastStart + " - " + ds.lastEnd);
        addSummary("Save API", ds.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
