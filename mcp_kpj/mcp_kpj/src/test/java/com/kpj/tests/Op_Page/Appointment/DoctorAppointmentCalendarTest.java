package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.BookAppointmentPage;
import com.kpj.pages.Op_page.Appointment.DoctorAppointmentCalendarPage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * OP &gt; Appointment &gt; Doctorwise Appointment Calendar (#/dr-appointment-calendar).
 *
 * Login; open the calendar from the menu; check Day / Week / Month, Next / Previous / Today; filter by
 * Appointment Type = Doctor and a Doctor (Total Booked updates) and by Appointment Status; click a free future
 * slot (Book Appointment unlocks); click Book Appointment — the capacity alert shows and Book Appointment opens in
 * a new tab carrying the slot.
 *
 * The Book Appointment tab is driven with the team's existing {@link BookAppointmentPage} (attachToOpenForm,
 * fillMandatoryDetails, clickAdd, saveAndCaptureReport) — no duplicate Book Appointment script.
 */
public class DoctorAppointmentCalendarTest extends DevHisBase {

    /** Doctor used on the calendar. Same doctor the team's BookAppointmentPage.fillMandatoryDetails() books with. */
    private static final String DOCTOR = "Demo Doctor";

    public DoctorAppointmentCalendarTest() {
        super("op_appointment_DoctorAppointmentCalendar");
    }

    public static void main(String[] args) {
        DoctorAppointmentCalendarTest t = new DoctorAppointmentCalendarTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
    }

    @Override
    protected void body() {
        meta("OP - Appointment - Doctorwise Appointment Calendar",
                "OP > Appointment > Doctorwise Appointment Calendar",
                "⚠ This test books a NEW appointment from a calendar slot (through the Book Appointment tab).");

        LoginPage loginPage = new LoginPage(page);
        DoctorAppointmentCalendarPage cal = new DoctorAppointmentCalendarPage(page);

        // 1) Authenticate
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // 2) Open the calendar from the menu
        boolean opened = cal.navigateViaMenu();
        if (opened) cal.prepareScreen();
        step(page, "Open Doctorwise Appointment Calendar", "Click OP -> Appointment -> Doctorwise Appointment Calendar",
                "The calendar opens in Week view with its filters",
                opened ? "Opened " + page.url() + " - " + cal.viewMode() + " view, " + cal.dateRangeLabel() : "Did NOT reach the screen (" + page.url() + ")",
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED - calendar not reached"); return; }

        // 3) Book Appointment is locked until a slot is picked
        boolean lockedAtStart = !cal.bookButtonEnabled();
        step(page, "Book Appointment locked without a slot", "Look at Book Appointment before choosing any slot",
                "Book Appointment is disabled", lockedAtStart ? "Disabled" : "Enabled with no slot selected",
                lockedAtStart ? "PASS" : "FAIL");

        // 4) Day / Month / Week views
        String weekLabel = cal.dateRangeLabel();
        String dayLabel = cal.clickToolbar("Day");
        String dayMode = cal.viewMode();
        String monthLabel = cal.clickToolbar("Month");
        String monthMode = cal.viewMode();
        String backLabel = cal.clickToolbar("Week");
        String weekMode = cal.viewMode();
        boolean viewsOk = "day".equals(dayMode) && "month".equals(monthMode) && "week".equals(weekMode) && backLabel.equals(weekLabel);
        step(page, "Switch Day / Month / Week view", "Click Day, then Month, then Week",
                "Each click changes the view and the date-range label; Week returns to the starting week",
                "Day = " + dayMode + " (" + dayLabel + ") | Month = " + monthMode + " (" + monthLabel + ") | Week = " + weekMode + " (" + backLabel + ")",
                viewsOk ? "PASS" : "FAIL");

        // 5) Next / Previous / Today
        String nextLabel = cal.clickArrow(true);
        String prevLabel = cal.clickArrow(false);
        cal.clickArrow(true);
        // Today is built to open the DAY view of today's date (goToday sets viewMode='day'); Week then brings back this week
        String todayLabel = cal.clickToolbar("Today");
        String todayMode = cal.viewMode();
        String todayText = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH));
        String weekAgain = cal.clickToolbar("Week");
        boolean navOk = !nextLabel.equals(weekLabel) && prevLabel.equals(weekLabel)
                && "day".equals(todayMode) && todayLabel.equals(todayText) && weekAgain.equals(weekLabel);
        step(page, "Next / Previous / Today", "Click Next, Previous, Next again, Today, then Week",
                "Next moves one week on, Previous comes back, Today opens today's Day view, Week returns to the current week",
                "Start " + weekLabel + " | Next " + nextLabel + " | Previous " + prevLabel + " | Today " + todayMode + " " + todayLabel + " | Week " + weekAgain,
                navOk ? "PASS" : "FAIL");

        // 6) Filter: Appointment Type = Doctor, Doctor
        String allTotal = cal.totalBooked();
        String type = cal.selectAppointmentType("Doctor");
        String doc = cal.selectDoctor(DOCTOR);
        String docTotal = cal.totalBooked();
        boolean filterOk = type.endsWith(":Doctor") && doc.endsWith(":" + DOCTOR) && docTotal.matches("\\d+");
        step(page, "Filter by Appointment Type and Doctor", "Appointment Type = Doctor; Doctor = " + DOCTOR,
                "The Doctor list unlocks; the calendar and Total Booked show only that doctor's appointments",
                "Type " + type + " | Doctor " + doc + " | Total Booked " + allTotal + " -> " + docTotal + " (" + cal.appointmentBlocks() + " blocks drawn)",
                filterOk ? "PASS" : "FAIL");

        // 7) Filter: Appointment Status
        String st = cal.selectAppointmentStatus("No Show");
        String noShowTotal = cal.totalBooked();
        String stBack = cal.selectAppointmentStatus("--All--");
        String backTotal = cal.totalBooked();
        boolean statusOk = st.endsWith(":No Show") && noShowTotal.matches("\\d+") && backTotal.equals(docTotal);
        step(page, "Filter by Appointment Status", "Appointment Status = No Show, then back to --All--",
                "Total Booked changes to the No Show count, then returns to the doctor's total",
                "No Show -> " + noShowTotal + " | --All-- -> " + backTotal + " (" + stBack + ")",
                statusOk ? "PASS" : "FAIL");

        // 8) Pick a free future slot -> Book Appointment unlocks
        String slot = cal.selectFreeFutureSlot();
        step(page, "Select a free slot", "Click a free slot on a future day (09:00 AM - 05:00 PM)",
                "The slot is highlighted and Book Appointment unlocks",
                slot == null ? "No free future slot unlocked Book Appointment" : "Slot " + slot + " selected; Book Appointment enabled",
                slot == null ? "FAIL" : "PASS");
        if (slot == null) { addSummary("Result", "FAILED - no free slot"); return; }

        // 9) Book Appointment -> capacity alert + new tab
        Page tab = cal.clickBookAppointmentAndCatchTab();
        List<String> calMsgs = cal.capturedMessages();
        String calSaid = calMsgs.isEmpty() ? "(no alert captured)" : String.join(" | ", calMsgs);
        boolean capacityShown = calSaid.toLowerCase().contains("slots are remaining");
        step(page, "Click Book Appointment", "Click Book Appointment with the slot selected",
                "The capacity alert shows and Book Appointment opens in a new tab",
                "Alert: " + calSaid + " | " + (tab != null ? "new tab " + tab.url() : "NO new tab opened") + " | hand-off: " + cal.handOff(),
                tab != null && capacityShown ? "PASS" : "FAIL");
        if (tab == null) { addSummary("Result", "FAILED - Book Appointment tab did not open"); return; }

        // 10) Book Appointment tab — attach with the team's page object and check the slot was carried over
        BookAppointmentPage book = new BookAppointmentPage(tab);
        try { book.attachToOpenForm(); }
        catch (Exception e) {
            step(tab, "Book Appointment form ready", "Wait for the Book Appointment form in the new tab",
                    "The form and its master data load", "Form not ready - " + String.valueOf(e.getMessage()).split("\n")[0], "FAIL");
            addSummary("Result", "FAILED - Book Appointment form did not load");
            return;
        }
        String slotDate = slot.split(" ")[0];
        String[] carried = DoctorAppointmentCalendarPage.carriedToBookAppointment(tab);
        boolean carriedOk = carried[1].equalsIgnoreCase(DOCTOR) && carried[2].equals(slotDate) && !carried[3].isEmpty();
        step(tab, "Slot carried to Book Appointment", "Read Appointment Type, Doctor, Date and From/To on the new tab",
                "Doctor " + DOCTOR + ", Date " + slotDate + " and the slot's From time are pre-filled",
                "Type '" + carried[0] + "' | Doctor '" + carried[1] + "' | Date '" + carried[2] + "' | From '" + carried[3] + "' | To '" + carried[4] + "'",
                carriedOk ? "PASS" : "FAIL");

        // 11) Reuse the team's Book Appointment steps
        book.fillMandatoryDetails();
        tab.evaluate("() => window.scrollTo(0, 0)");
        step(tab, "Fill mandatory details", "BookAppointmentPage.fillMandatoryDetails() (team script)",
                "All mandatory fields accepted", "Mandatory details filled", "PASS");

        String[] after = DoctorAppointmentCalendarPage.carriedToBookAppointment(tab);
        String slotUsed;
        if (!after[2].isEmpty() && !after[3].isEmpty()) {
            slotUsed = after[2] + " " + after[3] + " (calendar slot kept)";
        } else {
            // the calendar's date/time did not survive the form fill — book through the team's own slot picker
            String d = book.setAppointmentDateTomorrow();
            String s = book.selectFreeSlot();
            slotUsed = s == null ? null : d + " " + s + " (calendar slot lost; team slot picker used)";
        }
        step(tab, "Appointment date and slot", "Keep the calendar slot; if the form lost it, use BookAppointmentPage.selectFreeSlot()",
                "Appointment Date and From/To time are set",
                slotUsed == null ? "No slot set" : slotUsed, slotUsed == null ? "FAIL" : "PASS");

        int listSize = book.clickAdd();
        step(tab, "Add appointment to list", "BookAppointmentPage.clickAdd() (team script)",
                "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)",
                listSize > 0 ? "PASS" : "FAIL");

        Page report = book.saveAndCaptureReport();
        List<String> msgs = book.capturedMessages();
        String said = msgs.isEmpty() ? "(none captured)" : String.join(" | ", msgs);
        boolean saved = BookAppointmentPage.saveSucceeded(msgs) || report != null;
        step(tab, "Save & accept alert", "BookAppointmentPage.saveAndCaptureReport() (team script)",
                "Appointment saved; success/booking alert",
                saved ? said : "FAILED - not saved; the screen said: " + said, saved ? "PASS" : "FAIL");
        if (report != null) {
            step(report, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page is displayed", "Report opened: " + report.url(), "PASS");
        } else {
            step(tab, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page opens with a valid Appointment ID", "No report tab opened", "FAIL");
        }

        // --- Summary ---
        addSummary("Application URL", BASE + "/#/dr-appointment-calendar");
        addSummary("Doctor", DOCTOR);
        addSummary("Calendar slot", slot);
        addSummary("Book Appointment tab", tab.url());
        addSummary("Report tab", report == null ? "not captured" : report.url());
    }
}
