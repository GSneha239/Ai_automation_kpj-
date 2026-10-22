package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named StaffSchedule — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Staff Schedule</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Staff Schedule</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b>, <b>Payable</b>, <b>Start Time</b>, <b>End Time</b>, <b>Consultation Room</b>,
 *       <b>Nursing Station</b>, <b>Days</b>.</li>
 *   <li>Click the inner <b>Add</b>, then <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class StaffSchedule extends DevHisBase {

    public StaffSchedule() { super("ApplicationConfig_Location_StaffSchedule"); }

    public static void main(String[] args) {
        StaffSchedule t = new StaffSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Staff Schedule",
                "Application Configuration > Location > Staff Schedule",
                "Add a staff schedule: Department, Payable, Start/End Time, Consultation Room, Nursing "
                        + "Station, Days, Add, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.StaffSchedule ss =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.StaffSchedule(page);

        boolean onScreen = ss.navigateViaMenu();
        step(page, "Open Staff Schedule screen",
                "Click Application Configuration -> Location -> Staff Schedule",
                "The Staff Schedule screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + ss.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("schedule links => " + ss.findScheduleLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + ss.describeForm());

        boolean added = ss.clickAdd();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + ss.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + ss.describeForm());

        // Location is mandatory on this form family even though the flow does not name it.
        String loc = ss.selectLocation();
        System.out.println("Location (mandatory, not in the flow) = " + loc);

        // 3) Department, Payable
        // The Payable list DEPENDS on the Department, and many Departments have no Payables at all (the first one, "(NAMA DR) MR C/N",
        // has none, so the old "first Department + first Payable" left Payable empty and every later step failed). Walk the Departments
        // until one offers a Payable. NB: a real Department can be named "(NAMA DR) ..." - only the helper's own markers are placeholders.
        // The server also refuses a Payable that ALREADY has a schedule on those days ("Staff Schedule already has scheduled", whatever
        // the hours), and the old "first Payable" had been used up, so the Payable is chosen at a RANDOM position in the list and each
        // retry moves on to the next one.
        final int payablePick = new java.util.Random().nextInt(25);
        String dept = "", payable = "";
        int deptFound = 0;
        for (int d = 0; d < 60; d++) {
            dept = ss.selectDepartment(d);
            if (isPlaceholder(dept)) break;                     // ran out of Departments
            payable = ss.selectPayable(payablePick);
            deptFound = d;
            if (!isPlaceholder(payable)) break;
        }
        boolean deptPayableOk = !dept.isEmpty() && !isPlaceholder(dept) && !payable.isEmpty() && !isPlaceholder(payable);
        step(page, "Select Department and Payable", "Choose the Department and the Payable",
                "A Department and a Payable are selected",
                "Department=" + dept + " | Payable=" + payable, deptPayableOk ? "PASS" : "FAIL");

        // 4) Start / End time
        // A Payable cannot have two overlapping schedules on the same days, and the old fixed 09:00-17:00 window on the same first
        // Department / Payable collided with an earlier run's schedule ("Staff Schedule already has scheduled"). So the one-hour
        // window starts at a RANDOM hour (06:00-19:00) and moves on by two hours on every retry.
        final int baseSlot = new java.util.Random().nextInt(14);
        String times = ss.fillTimes(clock(6 + baseSlot % 14), clock(6 + baseSlot % 14 + 1));
        boolean timesOk = !ss.lastStart.isEmpty() && !ss.lastEnd.isEmpty();
        step(page, "Enter Start Time and End Time", "Type the Start Time and the End Time",
                "Both times are entered", times, timesOk ? "PASS" : "FAIL");

        // 5) Consultation Room, Nursing Station
        String room = ss.selectConsultationRoom(0);
        String station = ss.selectNursingStation(0);
        boolean roomStationOk = !room.isEmpty() && !room.startsWith("(") && !station.isEmpty() && !station.startsWith("(");
        step(page, "Select Consultation Room and Nursing Station",
                "Choose the Consultation Room and the Nursing Station",
                "A Consultation Room and a Nursing Station are selected",
                "Consultation Room=" + room + " | Nursing Station=" + station, roomStationOk ? "PASS" : "FAIL");

        // 6) Days
        String days = ss.tickDays("Monday", "Tuesday", "Wednesday");
        boolean daysOk = !days.isEmpty();
        step(page, "Select Days", "Tick the days of the schedule", "The days are ticked",
                daysOk ? "Ticked: " + days : "No day ticked", daysOk ? "PASS" : "FAIL");

        // 7) Inner Add — commits the schedule line
        String inner = ss.clickInnerAdd();
        System.out.println("schedule grid now: " + ss.scheduleRows());
        java.util.regex.Matcher rows = java.util.regex.Pattern.compile("rows (\\d+) -> (\\d+)").matcher(inner);
        boolean innerOk = rows.find() && Integer.parseInt(rows.group(2)) > Integer.parseInt(rows.group(1))
                && "[]".equals(ss.lastAddToasts);
        step(page, "Click Add (schedule line)", "Click the Add button of the schedule section",
                "The schedule line is added to the grid", inner, innerOk ? "PASS" : "FAIL");

        // 8) Submit. A conflicting schedule is rejected without writing anything; the retry then starts from a
        // FRESH form and shifts the department/payable choice, same defensive pattern as DepartmentSchedule.
        String toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int roomIndex = 0, stationIndex = 0;
        for (int attempt = 0; attempt < 10 && !ok; attempt++) {
            toast = ss.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            // The screen shows a clashing schedule as a vague "Message Not Found!"; the server's own answer
            // ("Staff Schedule already has scheduled", ResultStatus=2) is what says it is a conflict.
            boolean conflict = tl.contains("already") || ss.lastSaveApi.toLowerCase().contains("already");
            tries.append(attempt == 0 ? "" : " | ").append(ss.lastDepartment).append(" -> \"").append(toast).append('"');
            if (ok || !conflict) break;
            if (!ss.clickBack()) break;
            if (!ss.clickAdd()) break;
            int slot = 6 + (baseSlot + 2 * (attempt + 1)) % 14;
            String refilled = ss.fillAll(deptFound, payablePick + attempt + 1, clock(slot), clock(slot + 1), roomIndex, stationIndex,
                    "Monday", "Tuesday", "Wednesday");
            System.out.println("Submit retry " + (attempt + 1) + " (window " + clock(slot) + "-" + clock(slot + 1) + "): " + refilled);
            if (isPlaceholder(ss.lastDepartment)) break;
        }
        step(page, "Click Submit", "Click Submit", "The staff schedule is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (ss.lastSaveApi.isEmpty() ? "" : "  [" + ss.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // 9) Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(ss.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Department", ss.lastDepartment);
        addSummary("Payable", ss.lastPayable);
        addSummary("Schedule", ss.lastDays + "  " + ss.lastStart + " - " + ss.lastEnd);
        addSummary("Consultation Room", ss.lastConsultationRoom);
        addSummary("Nursing Station", ss.lastNursingStation);
        addSummary("Save API", ss.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    /** 24-hour clock hour -> the "hh:00 AM/PM" text the time widgets take. */
    private static String clock(int h24) {
        int h12 = h24 % 12 == 0 ? 12 : h24 % 12;
        return String.format("%02d:00 %s", h12, h24 % 24 < 12 ? "AM" : "PM");
    }

    /** True only for the page object's own failure markers - never a real option's text (a Department can be named "(NAMA DR) ..."). */
    private static boolean isPlaceholder(String s) {
        return s == null || s.isEmpty() || s.startsWith("(not found") || s.startsWith("(no-options");
    }
}
