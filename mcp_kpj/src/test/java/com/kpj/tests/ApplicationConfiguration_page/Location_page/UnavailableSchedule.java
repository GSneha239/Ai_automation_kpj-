package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named UnavailableSchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Unavailable Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Unavailable Schedule</b>.</li>
 *   <li>Select the list's own <b>Schedule Type</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the form's own <b>Schedule Type</b>, <b>Location</b>, <b>Department</b>, <b>Payable</b>,
 *       <b>Modality</b>, <b>Start Time</b>, <b>End Time</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class UnavailableSchedule extends DevHisBase {

    public UnavailableSchedule() { super("ApplicationConfig_Location_UnavailableSchedule"); }

    public static void main(String[] args) {
        UnavailableSchedule t = new UnavailableSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Unavailable Schedule",
                "Application Configuration > Location > Unavailable Schedule",
                "Add an unavailable schedule: Schedule Type, Location, Department, Payable, Modality, Start/End Time, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.UnavailableSchedule us =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.UnavailableSchedule(page);

        boolean onScreen = us.navigateViaMenu();
        step(page, "Open Unavailable Schedule screen",
                "Click Application Configuration -> Location -> Unavailable Schedule",
                "The Unavailable Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + us.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("links => " + us.findUnavailableScheduleLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + us.describeForm());

        // Select the list screen's own Schedule Type
        String listType = us.selectListScheduleType(0);
        step(page, "Select Schedule Type", "Choose the Schedule Type on the list screen", "A Schedule Type is selected",
                listType.isEmpty() ? "Schedule Type NOT selected" : "Schedule Type = " + listType,
                listType.isEmpty() ? "FAIL" : "PASS");

        boolean added = us.clickAdd() && us.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + us.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + us.describeForm());

        // Form's own Schedule Type, Location, Department, Payable, Modality
        String formType = us.selectFormScheduleType(0);
        String loc = us.selectLocation();
        String dept = us.selectDepartment(0);
        String pay = us.selectPayable(0);
        String mod = us.selectModality(0);
        boolean selOk = !formType.isEmpty() && !loc.isEmpty() && !dept.isEmpty() && !pay.isEmpty() && !mod.isEmpty();
        step(page, "Select Schedule Type, Location, Department, Payable, Modality",
                "Choose the form's Schedule Type, Location, Department, Payable and Modality",
                "All five are selected",
                "Schedule Type=" + formType + " | Location=" + loc + " | Department=" + dept + " | Payable=" + pay + " | Modality=" + mod,
                selOk ? "PASS" : "FAIL");

        // Start / End time
        String times = us.fillTimes("09:00 AM", "05:00 PM");
        boolean timesOk = !us.lastStart.isEmpty() && !us.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // Inner Add — commits the schedule line
        String inner = us.clickInnerAdd();
        System.out.println("schedule grid now: " + us.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(us.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // Submit. A combination that already has a schedule is rejected — retry walks Department forward on a
        // FRESH form (Back -> Add), same pattern as the sibling schedule screens.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptFrom = us.lastDepartmentIndex + 1;
        for (int attempt = 0; attempt < 6 && !ok; attempt++) {
            toast = us.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(us.lastDepartment).append('/').append(us.lastPayable)
                    .append('/').append(us.lastModality).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            if (!us.clickBack()) break;
            if (!(us.clickAdd() && us.addFormOpen())) break;
            String refilled = us.fillAll(deptFrom, "09:00 AM", "05:00 PM");
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            deptFrom = us.lastDepartmentIndex + 1;
            if (us.lastPayable.isEmpty()) break;
        }
        step(page, "Click Submit", "Click Submit", "The unavailable schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (us.lastSaveApi.isEmpty() ? "" : "  [" + us.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(us.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Schedule Type (list)", us.lastListScheduleType);
        addSummary("Schedule Type (form)", us.lastFormScheduleType);
        addSummary("Department", us.lastDepartment);
        addSummary("Payable", us.lastPayable);
        addSummary("Modality", us.lastModality);
        addSummary("Schedule", us.lastStart + " - " + us.lastEnd);
        addSummary("Save API", us.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
