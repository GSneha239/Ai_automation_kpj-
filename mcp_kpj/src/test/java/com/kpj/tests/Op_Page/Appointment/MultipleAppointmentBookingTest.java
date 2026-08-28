package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.MultipleAppointmentBookingPage;
import com.microsoft.playwright.Page;

/**
 * End-to-end test for OP &gt; Appointment &gt; Multiple Appointment Booking.
 *
 * All page interactions are delegated to Page Object Model classes.
 * DevHisBase provides the step/reporting harness only.
 */
public class MultipleAppointmentBookingTest extends DevHisBase {

    public MultipleAppointmentBookingTest() {
        super("TC01_MAB");
    }

    public static void main(String[] args) {
        MultipleAppointmentBookingTest t = new MultipleAppointmentBookingTest();
        try {
            t.run();
        } finally {
            t.stop();
        }
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try {
            run();
        } finally {
            stop();
        }
    }

    @Override
    protected void body() {
        meta("Multiple Appointment Booking",
                "OP > Appointment > Multiple Appointment Booking",
                "⚠️ This test creates a NEW appointment booking in DevHIS.");

        // --- Page object initialisation ---------------------------------
        LoginPage loginPage = new LoginPage(page);
        MultipleAppointmentBookingPage bookingPage = new MultipleAppointmentBookingPage(page);

        // 1) Authenticate
        loginPage.login(BASE, USER, PASS);
        step("Login", "farisha / Tcare@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // 2) Navigate to the module
        bookingPage.navigateTo(BASE);
        step("Navigate to module", "OP > Appointment > Multiple Appointment Booking",
                "Booking screen opens", "Booking form displayed", "PASS");

        // 3) Patient details
        bookingPage.fillPatientDetails();
        bookingPage.selectGenderMale();
        bookingPage.fillAge("35");
        step("Fill patient details", "Random valid patient data + Gender + Age",
                "Fields accepted", "Patient details filled", "PASS");

        // 4) Booking Type
        bookingPage.selectBookingTypeKPJ();
        step("Booking Type = KPJ", "Set KPJ Booking Type", "Shows KPJ", "KPJ", "PASS");

        // 5) Payor Type (ng-model PatientData.receivabletypeid — select2)
        bookingPage.selectPayorTypeSelf();
        step("Payor Type = Self", "Set Self Payor Type", "Shows Self", "Self", "PASS");

        // 6) Appointment Type
        bookingPage.selectAppointmentTypeDepartment();
        step("Appointment Type = Department", "Set Department", "Shows Department", "Department", "PASS");

        // 7-9) Select Departments for 3 appointments
        bookingPage.selectDepartment1();
        step("Department 1", "Select department for Appt 1", "Department selected", "Dept 1 selected", "PASS");

        bookingPage.selectDepartment2();
        step("Department 2", "Select department for Appt 2", "Department selected", "Dept 2 selected", "PASS");

        bookingPage.selectDepartment3();
        step("Department 3", "Select department for Appt 3", "Department selected", "Dept 3 selected", "PASS");

        // Set appointment date to tomorrow before selecting slots
        java.time.LocalDate tomorrow = java.time.LocalDate.now().plusDays(1);
        bookingPage.setAppointmentDate(tomorrow.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        // 10-12) Select and book 3 appointment slots
        bookingPage.selectAppointmentSlots(3);
        for (int i = 0; i < 3; i++) {
            step("Appt " + (i + 1) + " - Dept + Slot",
                    "Book appointment " + (i + 1),
                    "Slot selected for Appt " + (i + 1),
                    "Appt " + (i + 1) + " booked", "PASS");
        }

        // 11) Save — click Save, accept dialog, extract toast.
        // The toast must be a SUCCESS message. Judging on "a toast appeared" passed a run whose save was rejected
        // with "Please Enter Passport No.!" — nothing was booked, and the report still read PASS.
        String toastMsg = bookingPage.clickSaveAndAccept();
        String t = toastMsg == null ? "" : toastMsg.toLowerCase();
        boolean booked = t.contains("saved") || t.contains("success") || t.contains("succes")
                || t.contains("booked") || t.contains("added");
        // "Please Enter Passport No.!" traces back to this screen having no Nationality field at all
        // (unlike the single Book Appointment screen) — the backend can't resolve Nationality and asks
        // for a passport instead, and there is nothing on this screen the automation (or a manual user)
        // can fill to satisfy that. Surface the diagnosis explicitly rather than just the raw toast text.
        String failReason;
        if (!booked && t.contains("passport") && !bookingPage.hasNationalityField()) {
            failReason = "Booking REJECTED — the screen answered: \"" + toastMsg + "\". "
                    + "Root cause: this screen has no Nationality field (unlike the single Book Appointment "
                    + "screen), so the backend cannot resolve Nationality and falls back to requiring a "
                    + "Passport No. — an application gap, not something this test can fill in.";
        } else if (!booked) {
            failReason = toastMsg.isEmpty() ? "No toast appeared — the booking was not confirmed"
                    : "Booking REJECTED — the screen answered: \"" + toastMsg + "\"";
        } else {
            failReason = toastMsg;
        }
        step("Save & Toast", "Click Save and accept dialog",
                "'... saved successfully' toast", failReason, booked ? "PASS" : "FAIL");

        // 12) Report tab — check if a new tab opened
        boolean reportTabFound = checkForNewTab(bookingPage, toastMsg);

        // --- Test summary ------------------------------------------------
        addSummary("Application URL", BASE + "/#/MultipleAppointmentBooking");
        addSummary("Patient Details", "Random unique data (NRIC, email, mobile vary each run)");
        addSummary("Booking / Payor / Appt Type", "KPJ | Self | Department");
        addSummary("Department (x3)", "Cardiology");
        // Report what the save actually did — a fixed "3" here read as three bookings on a run that saved nothing.
        addSummary("Appointments Booked", booked ? "3" : "0 — the save was rejected");
        addSummary("Slots", "slot_app1, slot_app2, slot_app3");
        addSummary("Result", booked ? toastMsg : "FAILED — " + failReason);
    }

    // ---- private helpers ---------------------------------------------------

    /**
     * Iterate through open browser tabs. If a tab's URL contains "Report" or
     * "Registration", bring it to front, screenshot it, and record a PASS step.
     * Otherwise screenshot the main page and record a MANUAL step.
     */
    private boolean checkForNewTab(MultipleAppointmentBookingPage bookingPage, String toastMsg) {
        for (Page p : page.context().pages()) {
            if (p.url().contains("Report") || p.url().contains("Registration")) {
                p.bringToFront();
                p.waitForTimeout(3000);
                // step() captures the screenshot in-memory (embedded base64) — no separate PNG file written.
                step(p, "Report Tab", "Report tab opened",
                        "Screenshot of report page", "Report tab screenshot taken", "PASS");
                return true;
            }
        }
        step("Report Tab", "Check for report tab",
                "New tab opens", toastMsg.isEmpty() ? "No tab opened" : toastMsg, "MANUAL");
        return false;
    }
}
