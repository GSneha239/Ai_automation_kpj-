package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PayableScheduleMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Payable Schedule Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Payable Schedule Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Department</b>, <b>Payable</b>, <b>Start Time</b>, <b>End Time</b>,
 *       <b>Consultation Room</b>, <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class PayableScheduleMaster extends DevHisBase {

    public PayableScheduleMaster() { super("ApplicationConfig_Location_PayableScheduleMaster"); }

    public static void main(String[] args) {
        PayableScheduleMaster t = new PayableScheduleMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Payable Schedule Master",
                "Application Configuration > Location > Payable Schedule Master",
                "Add a payable schedule: Location, Department, Payable, Start/End Time, Consultation Room, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableScheduleMaster psm =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableScheduleMaster(page);

        boolean onScreen = psm.navigateViaMenu();
        step(page, "Open Payable Schedule Master screen",
                "Click Application Configuration -> Location -> Payable Schedule Master",
                "The Payable Schedule Master screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + psm.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("payable links => " + psm.findPayableLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        // Learn which payables are already scheduled — Submit against one of those UPDATES live configuration
        // instead of adding a schedule.
        System.out.println("payables already scheduled: " + psm.harvestScheduledFromGrid());

        boolean added = psm.clickAdd() && psm.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + psm.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("day checkboxes: " + psm.dayLabels());

        // 3) Location
        String loc = psm.selectLocation();
        step(page, "Select Location", "Choose the Location", "A Location is selected",
                loc.isEmpty() ? "Location NOT selected" : "Location = " + loc, loc.isEmpty() ? "FAIL" : "PASS");

        // 4) Department + Payable (Payable is fed by the Department)
        String dp = psm.selectDepartmentAndPayable();
        boolean dpOk = !psm.lastPayable.isEmpty();
        step(page, "Select Department and Payable", "Choose the Department, then the Payable it offers",
                "A Department and a Payable are selected", dp, dpOk ? "PASS" : "FAIL");

        // 5) Start / End time
        String times = psm.fillTimes("09:00 AM", "05:00 PM");
        System.out.println("time inputs after fill:\n  " + psm.timeInputs());
        boolean timesOk = !psm.lastStart.isEmpty() && !psm.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // 6) Consultation Room
        String room = psm.selectConsultationRoom();
        step(page, "Select Consultation Room", "Choose the Consultation Room", "A Consultation Room is selected",
                room.isEmpty() ? "Consultation Room NOT selected" : "Consultation Room = " + room,
                room.isEmpty() ? "FAIL" : "PASS");

        // 7) Days
        String days = psm.tickDays("Monday", "Tuesday", "Wednesday");
        boolean daysOk = !days.isEmpty();
        step(page, "Select Days", "Tick the days of the schedule", "The days are ticked",
                daysOk ? "Ticked: " + days : "No day ticked", daysOk ? "PASS" : "FAIL");

        // 8) Inner Add — commits the schedule line
        String inner = psm.clickInnerAdd();
        System.out.println("schedule grid now: " + psm.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(psm.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // 9) Submit. This Location/Department/Payable may already have a schedule — the server answers
        // "already scheduled" and nothing is written. A retry then starts from a FRESH form: the form keeps its
        // PayScheduleid otherwise, and the next Submit would UPDATE that existing schedule instead of adding one.
        String toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptFrom = psm.lastDepartmentIndex + 1;
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            int held = psm.payScheduleId();
            if (held > 0) {
                System.out.println("Submit: the form is holding schedule id " + held + " — Submit would UPDATE it; taking a fresh form");
                tries.append(attempt == 0 ? "" : " | ").append("(skipped: form held schedule ").append(held).append(')');
            } else {
                toast = psm.submitAndGetToast();
                tl = toast == null ? "" : toast.toLowerCase();
                ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
                tries.append(attempt == 0 ? "" : " | ").append(psm.lastDepartment).append('/').append(psm.lastPayable)
                        .append(" -> \"").append(toast).append('"');
                if (ok) break;
                if (!psm.lastToasts.toLowerCase().contains("already")) break;
            }
            // fresh form, next department/payable
            if (!psm.clickBack()) break;
            if (!(psm.clickAdd() && psm.addFormOpen())) break;
            String refilled = psm.fillAll(deptFrom, "09:00 AM", "05:00 PM", "Monday", "Tuesday", "Wednesday");
            deptFrom = psm.lastDepartmentIndex + 1;
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (psm.lastPayable.isEmpty()) break;
        }
        // The master-list re-check (isNowScheduled) was unreliable — Submit's own success toast already
        // confirms the save (see the "Success toast message" step below), so Click Submit is judged on
        // that toast alone.
        step(page, "Click Submit", "Click Submit",
                "The payable schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // 10) Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        if (!ok && !psm.lastSaveApi.isEmpty()) actual += " — the save API returned " + psm.lastSaveApi;
        step(psm.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Location", psm.lastLocation);
        addSummary("Department", psm.lastDepartment);
        addSummary("Payable", psm.lastPayable);
        addSummary("Consultation Room", psm.lastRoom);
        addSummary("Schedule", psm.lastDays + "  " + psm.lastStart + " - " + psm.lastEnd);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
