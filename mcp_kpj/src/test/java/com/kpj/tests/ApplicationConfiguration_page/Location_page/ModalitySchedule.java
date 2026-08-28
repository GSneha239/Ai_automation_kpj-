package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ModalitySchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Modality Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Modality Schedule</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b>, <b>Modality</b>, <b>Start Time</b>, <b>End Time</b>, <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class ModalitySchedule extends DevHisBase {

    public ModalitySchedule() { super("ApplicationConfig_Location_ModalitySchedule"); }

    public static void main(String[] args) {
        ModalitySchedule t = new ModalitySchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final String[] DAYS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

    @Override
    protected void body() {
        meta("Application Configuration - Modality Schedule",
                "Application Configuration > Location > Modality Schedule",
                "Add a modality schedule: Department, Modality, Start/End Time, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.ModalitySchedule ms =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.ModalitySchedule(page);

        boolean onScreen = ms.navigateViaMenu();
        step(page, "Open Modality Schedule screen",
                "Click Application Configuration -> Location -> Modality Schedule",
                "The Modality Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + ms.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("modality links => " + ms.findModalityLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + ms.describeForm());

        boolean added = ms.clickAdd() && ms.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + ms.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + ms.describeForm());

        // Location (mandatory, not named in the flow), Department, Modality
        String loc = ms.selectLocation();
        String dept = ms.selectDepartment(0);
        String mod = ms.selectModality(0);
        boolean selOk = !loc.isEmpty() && !dept.isEmpty() && !mod.isEmpty();
        step(page, "Select Department and Modality", "Choose the Department and the Modality",
                "Both are selected", "Location=" + loc + " | Department=" + dept + " | Modality=" + mod,
                selOk ? "PASS" : "FAIL");

        // Start / End time
        String times = ms.fillTimes("09:00 AM", "05:00 PM");
        boolean timesOk = !ms.lastStart.isEmpty() && !ms.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // Days
        String day = ms.tickDay(DAYS[0]);
        boolean dayOk = !day.isEmpty();
        step(page, "Select Days", "Tick a day of the schedule", "The day is ticked",
                dayOk ? "Ticked: " + day : "No day ticked", dayOk ? "PASS" : "FAIL");

        // Inner Add — commits the schedule line
        String inner = ms.clickInnerAdd();
        System.out.println("schedule grid now: " + ms.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(ms.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // Submit. A Department/Modality that already has a schedule is rejected AND wipes the whole form (see the
        // page object's class doc) — the retry must Back -> Add for a genuinely fresh form, not just re-fill.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptIndex = 0, modIndex = ms.lastModalityIndex + 1;
        for (int attempt = 0; attempt < 6 && !ok; attempt++) {
            toast = ms.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(ms.lastDepartment).append('/').append(ms.lastModality).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("scheduled") && !tl.contains("exist")) break;
            if (!ms.clickBack()) break;
            if (!(ms.clickAdd() && ms.addFormOpen())) break;
            String refilled = ms.fillAll(deptIndex, modIndex, "09:00 AM", "05:00 PM", DAYS[0]);
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (ms.lastModality.isEmpty()) { modIndex = 0; deptIndex++; }
            else modIndex = ms.lastModalityIndex + 1;
        }
        step(page, "Click Submit", "Click Submit", "The modality schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (ms.lastSaveApi.isEmpty() ? "" : "  [" + ms.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(ms.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Modality Schedule Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department", ms.lastDepartment);
        addSummary("Modality", ms.lastModality);
        addSummary("Schedule", ms.lastDay + "  " + ms.lastStart + " - " + ms.lastEnd);
        addSummary("Save API", ms.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
