package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SubDepartmentSchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Sub Department Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Sub Department Schedule</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b>, <b>Sub Department</b>, <b>Payable</b>, <b>Start Time</b>, <b>End Time</b>.</li>
 *   <li>Select <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 *
 * <p>The requested flow also mentions a "Code" field — the live screen has none (verified against both the DOM
 * and the Angular scope object), so this test does not attempt one. See the page object's class doc.</p>
 */
public class SubDepartmentSchedule extends DevHisBase {

    public SubDepartmentSchedule() { super("ApplicationConfig_Location_SubDepartmentSchedule"); }

    public static void main(String[] args) {
        SubDepartmentSchedule t = new SubDepartmentSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final String[] DAYS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

    @Override
    protected void body() {
        meta("Application Configuration - Sub Department Schedule",
                "Application Configuration > Location > Sub Department Schedule",
                "Add a sub department schedule: Department, Sub Department, Payable, Start/End Time, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.SubDepartmentSchedule sds =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.SubDepartmentSchedule(page);

        boolean onScreen = sds.navigateViaMenu();
        step(page, "Open Sub Department Schedule screen",
                "Click Application Configuration -> Location -> Sub Department Schedule",
                "The Sub Department Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + sds.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("links => " + sds.findSubDepartmentScheduleLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + sds.describeForm());

        boolean added = sds.clickAdd() && sds.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + sds.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + sds.describeForm());

        // Location (mandatory, not named in the flow), Department, Sub Department, Payable
        String loc = sds.selectLocation();
        String cascade = sds.selectCascade(0);
        boolean cascadeOk = !sds.lastDepartment.isEmpty() && !sds.lastSubDepartment.isEmpty() && !sds.lastPayable.isEmpty();
        step(page, "Select Department, Sub Department, Payable", "Choose Department, Sub Department and Payable",
                "All three are selected", "Location=" + loc + " | " + cascade, cascadeOk ? "PASS" : "FAIL");

        // Start / End time
        String times = sds.fillTimes("09:00 AM", "05:00 PM");
        boolean timesOk = !sds.lastStart.isEmpty() && !sds.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // Days
        String day = sds.tickDay(DAYS[0]);
        boolean dayOk = !day.isEmpty();
        step(page, "Select Days", "Tick a day of the schedule", "The day is ticked",
                dayOk ? "Ticked: " + day : "No day ticked", dayOk ? "PASS" : "FAIL");

        // Inner Add — commits the schedule line
        String inner = sds.clickInnerAdd();
        System.out.println("schedule grid now: " + sds.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(sds.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // Submit. A Department/Sub Department/Payable that already has a schedule is rejected — retry walks the
        // cascade forward on a FRESH form (Back -> Add), same pattern as the sibling schedule screens.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptFrom = sds.lastDepartmentIndex + 1;
        for (int attempt = 0; attempt < 6 && !ok; attempt++) {
            toast = sds.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(sds.lastDepartment).append('/').append(sds.lastSubDepartment)
                    .append('/').append(sds.lastPayable).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            if (!sds.clickBack()) break;
            if (!(sds.clickAdd() && sds.addFormOpen())) break;
            String refilled = sds.fillAll(deptFrom, "09:00 AM", "05:00 PM", DAYS[0]);
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            deptFrom = sds.lastDepartmentIndex + 1;
            if (sds.lastPayable.isEmpty()) break;
        }
        step(page, "Click Submit", "Click Submit", "The sub department schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (sds.lastSaveApi.isEmpty() ? "" : "  [" + sds.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(sds.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department", sds.lastDepartment);
        addSummary("Sub Department", sds.lastSubDepartment);
        addSummary("Payable", sds.lastPayable);
        addSummary("Schedule", sds.lastDay + "  " + sds.lastStart + " - " + sds.lastEnd);
        addSummary("Save API", sds.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
