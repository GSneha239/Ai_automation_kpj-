package com.kpj.tests.Telemedicine_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Telemedicine_page.TelemedicineAppointmentPage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * Telemedicine &gt; <b>Appointment</b> — the SAME form/flow as OP &gt; Book Appointment ({@code #/New}), reached via
 * the Telemedicine menu. Fill details → Add → Save → confirm → the appointment report opens in a new tab.
 */
public class TelemedicineAppointment extends DevHisBase {

    public TelemedicineAppointment() { super("Telemedicine_Appointment"); }

    public static void main(String[] args) {
        TelemedicineAppointment t = new TelemedicineAppointment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Telemedicine - Appointment", "Telemedicine > Appointment",
                "Telemedicine Appointment (same as Book Appointment, #/New): fill details, Add, Save, confirm; the appointment report opens in a new tab.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        TelemedicineAppointmentPage appt = new TelemedicineAppointmentPage(page);

        appt.navigateTo(BASE);
        step(page, "Open Telemedicine > Appointment", "Telemedicine > Appointment (#/New)",
                "The appointment form + master data loaded", "Opened " + page.url(), "PASS");

        appt.fillMandatoryDetails();
        step(page, "Fill mandatory details",
                "Title, Name, NRIC, Nationality, Gender, DOB, Age, E-mail, Mobile, Payor Type (Self), Booking Type (KPJ), Appointment Type (Department), Department (Cardiology), Doctor (Demo Doctor)",
                "All mandatory fields accepted", "Mandatory details filled", "PASS");

        String slot = appt.selectFreeSlot();
        step(page, "Select appointment slot",
                "Pick a free (enabled, not-booked) slot from the Next Schedule; set From/To time",
                "A slot is selected and From/To populated",
                slot == null ? "No free slot found" : "Slot selected: " + slot,
                slot == null ? "FAIL" : "PASS");

        int listSize = appt.clickAdd();
        step(page, "Add appointment to list", "Click 'Add' (fnAddToMultipleApptList) before Save",
                "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)",
                listSize > 0 ? "PASS" : "FAIL");

        Page reportTab = appt.saveAndCaptureReport();
        List<String> alerts = appt.capturedAlerts();
        String alertMsg = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        step(page, "Save & accept alert",
                "Click Save; confirm 'Do You Want To Save'; accept the slot/booking alert",
                "Appointment saved; success/booking alert", alertMsg, "PASS");

        if (reportTab != null) {
            step(reportTab, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page is displayed", "Report opened: " + reportTab.url(), "PASS");
        } else {
            step(page, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page opens with a valid Appointment ID",
                    "Save accepted but the report was not generated (no report tab opened)", "FAIL");
        }

        try { if (reportTab != null && reportTab != page && !reportTab.isClosed()) reportTab.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Application URL", BASE + "/#/New (via Telemedicine)");
        addSummary("Result", reportTab != null ? "Appointment saved; report opened" : "Not confirmed");
    }
}
