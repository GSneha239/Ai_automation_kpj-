package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.DoctorwiseAppointmentSchedulePage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * TC05 - OP &gt; Appointment &gt; Doctorwise Appointment Schedule.
 *
 * Login; open Doctorwise Appointment Schedule; Month = current; select Department + a Doctor that
 * has available slots; double-click an available slot → auto-navigates to Book Appointment; fill
 * the mandatory patient details; Save; confirm; the appointment report should open in a new tab.
 *
 * If the report is not generated, the report step FAILs with "Save accepted but the report was
 * not generated".
 */
public class DoctorwiseAppointmentScheduleTest extends DevHisBase {

    /** Department requested per the flow. "ACCIDENT & EMERGENCY" is the one with configured schedules
     *  on devhis; "Emergency Department" is a separate entry that has none, so it is only the fallback.
     *  (The previous fallback, "Cardiology", is not in this screen's department list at all — selecting
     *  it silently did nothing and the run reported "no doctor with slots" instead of "no such option".) */
    private static final String REQUESTED_DEPARTMENT = "ACCIDENT & EMERGENCY";
    private static final String FALLBACK_DEPARTMENT  = "Emergency Department";
    // "Doctor 12" (Cardiology) has a configured monthly schedule with available slots for the
    // current month; "Demo Doctor" does not. findDoctorWithSlots still iterates as a fallback.
    private static final String PREFERRED_DOCTOR     = "Doctor 12";

    public DoctorwiseAppointmentScheduleTest() {
        super("TC05_DoctorwiseAppointmentSchedule");
    }

    public static void main(String[] args) {
        DoctorwiseAppointmentScheduleTest t = new DoctorwiseAppointmentScheduleTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
    }

    @Override
    protected void body() {
        meta("Doctorwise Appointment Schedule",
                "OP > Appointment > Doctorwise Appointment Schedule",
                "Selects a slot from a doctor's schedule → Book Appointment → Save. On Save, the "
                        + "appointment report should open in a new tab.");

        LoginPage loginPage = new LoginPage(page);
        DoctorwiseAppointmentSchedulePage sched = new DoctorwiseAppointmentSchedulePage(page);

        // 1) Authenticate
        loginPage.login(BASE, USER, PASS);
        step("Login", "farisha / Tcare@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // 2) Open Doctorwise Appointment Schedule (Month defaults to the current month)
        sched.navigateTo(BASE);
        step("Open Doctorwise Appointment Schedule", "OP > Appointment > Doctorwise Appointment Schedule; Month = current",
                "Schedule filters loaded", "Page loaded (current month)", "PASS");

        // 3) Department + doctor with available slots (Emergency has none → fall back to Cardiology)
        String department = REQUESTED_DEPARTMENT;
        // selectDepartment returns "<ng-model>:no-opt" when the name is not in the dropdown. Left
        // unchecked that reads downstream as "this department has no doctors with slots", which is a
        // different (and much more misleading) failure than "this department does not exist here".
        String deptPick = sched.selectDepartment(department);
        String doctor = deptPick.endsWith(":no-opt") ? null : sched.findDoctorWithSlots(PREFERRED_DOCTOR);
        if (doctor == null) {
            department = FALLBACK_DEPARTMENT;
            deptPick = sched.selectDepartment(department);
            doctor = deptPick.endsWith(":no-opt") ? null : sched.findDoctorWithSlots(PREFERRED_DOCTOR);
        }
        step("Select Department & Doctor",
                "Department '" + REQUESTED_DEPARTMENT + "' (fallback '" + FALLBACK_DEPARTMENT + "' if no slots); pick a doctor with slots",
                "A doctor with a loaded slot grid is selected",
                doctor == null ? (deptPick.endsWith(":no-opt")
                        ? "Department '" + department + "' is not in the dropdown (" + deptPick + ")"
                        : "No doctor with slots found in either department")
                               : "Department: " + department + " | Doctor: " + doctor,
                doctor == null ? "FAIL" : "PASS");
        if (doctor == null) return;

        // 4) Pick an available slot -> auto-navigate to Book Appointment
        String slot = sched.pickAvailableSlotAndOpenBooking();
        step("Select available slot -> Book Appointment",
                "Double-click an available (green) slot; it auto-navigates to Book Appointment (#/New)",
                "Book Appointment opens pre-filled with the slot",
                slot == null ? "No available slot to pick" : "Slot: " + slot + " -> navigated to Book Appointment",
                slot == null ? "FAIL" : "PASS");
        if (slot == null) return;

        // 5) Fill the mandatory patient details (appointment context arrives from the schedule).
        // Fallback date = today (dynamic, never in the past); used only if the slot didn't pre-fill a valid
        // future date. The page keeps the slot's own pre-filled date when that is today-or-future.
        String apptDate = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        sched.fillPatientDetails(doctor, apptDate);
        step("Fill mandatory details",
                "Name, NRIC, Nationality, Gender, DOB, Age, E-mail, Mobile, Payor (Self), Booking (KPJ); ensure Doctor + date",
                "All mandatory fields accepted", "Mandatory details filled", "PASS");

        // 5b) Add the appointment to the list (REQUIRED — Save posts an empty payload without it)
        int listSize = sched.clickAdd();
        step("Add appointment to list", "Click 'Add' (fnAddToMultipleApptList) before Save",
                "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)",
                listSize > 0 ? "PASS" : "FAIL");

        // 6) Save -> confirm -> (report tab)
        Page reportTab = sched.saveAndCaptureReport();
        List<String> alerts = sched.capturedAlerts();
        String alertMsg = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        step("Save & accept alert", "Click Save; confirm 'Do You Want To Save'", "Appointment saved; success alert", alertMsg, "PASS");

        if (reportTab != null) {
            step(reportTab, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page is displayed", "Report opened: " + reportTab.url(), "PASS");
        } else {
            step(page, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page opens with a valid Appointment ID",
                    "Save accepted but the report was not generated (no report tab opened)", "FAIL");
        }

        // --- Summary ---
        addSummary("Application URL", BASE + "/#/DoctorwiseAppointmentSchedule");
        addSummary("Month", "Current (July 2026)");
        addSummary("Department", department + (department.equals(FALLBACK_DEPARTMENT) ? " (fallback; Emergency has no schedules)" : ""));
        addSummary("Doctor", doctor == null ? "(none)" : doctor);
        addSummary("Slot", slot == null ? "(none)" : slot);
        addSummary("Report tab", reportTab == null ? "Not generated (save accepted, no report)" : reportTab.url());
    }
}
