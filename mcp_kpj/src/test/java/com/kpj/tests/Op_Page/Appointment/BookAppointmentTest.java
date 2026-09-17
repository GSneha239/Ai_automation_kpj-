package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.BookAppointmentPage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * TC04 - OP &gt; Appointment &gt; Book Appointment (#/New).
 *
 * Two-step login; open Book Appointment; fill the mandatory patient + appointment details;
 * pick a free schedule slot; Save; confirm the "Do You Want To Save" dialog; accept the optional
 * "slot already booked" prompt; the appointment report opens in a new tab.
 */
public class BookAppointmentTest extends DevHisBase {

    public BookAppointmentTest() {
        super("TC04_BookAppointment");
    }

    public static void main(String[] args) {
        BookAppointmentTest t = new BookAppointmentTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
    }

    @Override
    protected void body() {
        meta("OP - Appointment - Book Appointment",
                "OP > Appointment > Book Appointment",
                "⚠ This test creates a NEW appointment; on save the appointment report opens in another tab.");

        LoginPage loginPage = new LoginPage(page);
        BookAppointmentPage bookPage = new BookAppointmentPage(page);

        // 1) Authenticate
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        // 2) Navigate to Book Appointment
        bookPage.navigateTo(BASE);
        step("Open Book Appointment", "OP > Appointment > Book Appointment (#/New)",
                "Book Appointment form + master data loaded", "Book Appointment page loaded", "PASS");

        // 3) Fill mandatory details
        bookPage.fillMandatoryDetails();
        // Setting Department/Doctor scrolls the page down to the newly-loaded schedule tables — scroll back to
        // the top so this step's screenshot shows the filled details form (like Step 2), not the schedule.
        page.evaluate("() => window.scrollTo(0, 0)");
        step("Fill mandatory details",
                "Title, Name, NRIC, Nationality, Gender, DOB, Age, E-mail, Mobile, Payor Type (Patient), "
                        + "Booking Type (KPJ), Appointment Type (Department), Department (Cardiac Surgery)",
                "All mandatory fields accepted", "Mandatory details filled", "PASS");

        // 3b) Enter tomorrow's Appointment Date — this triggers the schedule grid to (re)load;
        // left unset, the Next Schedule table can still be mid-load when step 4 checks it.
        String tomorrow = bookPage.setAppointmentDateTomorrow();
        step("Enter tomorrow's Appointment Date", "Set Appointment Date to tomorrow",
                "Appointment Date is set to tomorrow; schedule grid reloads",
                "Appointment Date = " + tomorrow, tomorrow.isEmpty() ? "FAIL" : "PASS");

        // 4) Select a free schedule slot
        String slot = bookPage.selectFreeSlot();
        // The page is still scrolled to the top (from the Step 3 fix above) — bring the schedule table back
        // into view so this step's screenshot actually shows the slot grid, not the details form again.
        page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/Schedule/i.test((x.caption&&x.caption.textContent)||'')); if(t) t.scrollIntoView({block:'center'}); }");
        step("Select appointment slot",
                "Pick a free (enabled, not-booked) slot from the Next Schedule; set From/To time",
                "A slot is selected and From/To populated",
                slot == null ? "No free slot found" : "Slot selected: " + slot,
                slot == null ? "FAIL" : "PASS");

        // 4b) Add the appointment to the list (REQUIRED — Save posts an empty payload without it)
        int listSize = bookPage.clickAdd();
        step("Add appointment to list", "Click 'Add' (fnAddToMultipleApptList) before Save",
                "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)",
                listSize > 0 ? "PASS" : "FAIL");

        // 5) Save -> confirm -> accept alert -> report tab
        Page reportTab = bookPage.saveAndCaptureReport();
        List<String> alerts = bookPage.capturedAlerts();
        String alertMsg = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        step("Save & accept alert",
                "Click Save; confirm 'Do You Want To Save'; accept the slot/booking alert",
                "Appointment saved; success/booking alert",
                alertMsg, "PASS");

        // 6) Report tab
        if (reportTab != null) {
            step(reportTab, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page is displayed",
                    "Report opened: " + reportTab.url(), "PASS");
        } else {
            step(page, "Appointment report tab", "Save opens the appointment report in a new tab",
                    "Report page opens with a valid Appointment ID",
                    "Save accepted but the report was not generated (no report tab opened)", "FAIL");
        }

        // --- Summary ---
        addSummary("Application URL", BASE + "/#/New");
        addSummary("Patient", "Mr. AutoBook (Male, Malaysian)");
        addSummary("Booking / Payor / Appt Type", "KPJ | Patient | Department");
        addSummary("Department", "Cardiac Surgery");
        addSummary("Slot", slot == null ? "(none)" : slot);
        addSummary("Report tab", reportTab == null ? "not captured" : reportTab.url());
    }
}
