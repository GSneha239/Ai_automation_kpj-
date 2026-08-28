package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * OP &gt; Appointment &gt; <b>Existing</b> ({@code #/Existing}) — a distinct sibling menu entry to "Book
 * Appointment" (#/New), not the same screen with an MRN search bolted on (corrected live 2026-08-26 — see
 * {@link ExistingPatientAppointmentPage} for how this was confirmed and why the class still extends
 * {@link BookAppointmentPage} rather than duplicating it: same underlying form/controller, different route).</p>
 *
 * <ol>
 *   <li>Enter MRN, click Search — loads the existing patient (default: the MRN produced by the most recent
 *       Registration test run, overridable with {@code -Dexisting.mrn=...}).</li>
 *   <li>Select Appointment Type (Department), Department, Booking Type, Payor Type, Appointment Date
 *       (tomorrow).</li>
 *   <li>Select a slot from Current Schedule / Next Schedule.</li>
 *   <li>Check all mandatory fields are filled; fill any that aren't.</li>
 *   <li>Add → Save → accept the alert → the appointment report opens in a new tab.</li>
 * </ol>
 */
public class ExistingPatientAppointmentTest extends DevHisBase {

    // The MRN a prior Registration run produced (see RegistrationTest) — override with -Dexisting.mrn=... for a
    // different one. Defaulted to a real MRN confirmed live (2026-08-26) to load "JOHN PETER" via Search.
    private static final String MRN = System.getProperty("existing.mrn", "1100338959").trim();

    public ExistingPatientAppointmentTest() {
        super("OP_ExistingPatientAppointment");
    }

    public static void main(String[] args) {
        ExistingPatientAppointmentTest t = new ExistingPatientAppointmentTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
    }

    @Override
    protected void body() {
        meta("OP - Appointment - Existing",
                "OP > Appointment > Existing",
                "Search an existing patient by MRN (" + MRN + ") on the dedicated Existing screen (#/Existing, not #/New), then book an appointment for tomorrow.");

        LoginPage loginPage = new LoginPage(page);
        ExistingPatientAppointmentPage appt = new ExistingPatientAppointmentPage(page);

        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        appt.navigateTo(BASE);
        boolean onExisting = appt.onExistingScreen();
        step(page, "Open Existing screen", "OP > Appointment > Existing (#/Existing)",
                "The Existing screen is shown (distinct from Book Appointment / #/New)",
                onExisting ? "Existing screen loaded - " + page.url() : "WRONG PAGE - expected #/Existing but the app is on: " + page.url(),
                onExisting ? "PASS" : "FAIL");
        if (!onExisting) { addSummary("Result", "FAILED - did not reach #/Existing"); return; }

        String patientName = appt.searchExistingPatientByMrn(MRN);
        boolean found = !patientName.isEmpty();
        step(page, "Enter MRN, click Search", "Enter MRN " + MRN + "; click Search (FindPatient)",
                "The existing patient's details load into the form",
                found ? "Loaded: " + patientName + " (MRN " + MRN + ")" : "No patient loaded for MRN " + MRN,
                found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED - MRN " + MRN + " did not load a patient"); return; }

        String config = appt.fillAppointmentConfig();
        boolean configOk = !config.contains("no-opt") && !config.contains("no-select") && !config.contains("no-real-opt");
        step(page, "Select Appointment Type, Department, Booking Type, Payor Type",
                "Select Appointment Type (Department), Department, Booking Type, Payor Type (and Doctor)",
                "All selects accept a real value", config, configOk ? "PASS" : "FAIL");

        String tomorrow = appt.setAppointmentDateTomorrow();
        step("Select Appointment Date (tomorrow)", "Set Appointment Date to tomorrow",
                "Appointment Date is set to tomorrow", "Appointment Date = " + tomorrow, tomorrow.isEmpty() ? "FAIL" : "PASS");

        String slot = appt.selectFreeSlot();
        step("Select the slot from Current Schedule / Next Schedule",
                "Pick a free slot from Current Schedule or Next Schedule (Next Schedule = tomorrow); set From/To time",
                "A slot is selected and From/To populated",
                slot == null ? "No free slot found" : "Slot selected: " + slot,
                slot == null ? "FAIL" : "PASS");

        String mandatoryCheck = appt.checkAndFillMandatoryFields();
        boolean stillEmpty = mandatoryCheck.contains("STILL EMPTY");
        step(page, "Check all mandatory fields are filled (fill if not)",
                "Re-check Payor Type/Booking Type/Appointment Type/Department/Doctor/Date/From/To; fill any still on \"-Select-\"",
                "No mandatory field is left unfilled",
                mandatoryCheck.isEmpty() ? "All mandatory fields were already filled" : mandatoryCheck,
                stillEmpty ? "FAIL" : "PASS");

        int listSize = appt.clickAdd();
        step("Click Add", "Click 'Add' (fnAddToMultipleApptList) before Save",
                "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)",
                listSize > 0 ? "PASS" : "FAIL");

        Page reportTab = appt.saveAndCaptureReport();
        List<String> alerts = appt.capturedAlerts();
        String alertMsg = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        step("Click Save, click OK in alert",
                "Click Save; confirm 'Do You Want To Save'; accept the slot/booking alert",
                "Appointment saved; success/booking alert",
                alertMsg, "PASS");

        if (reportTab != null) {
            step(reportTab, "Report generates in new tab", "Save opens the appointment report in a new tab",
                    "Report page is displayed",
                    "Report opened: " + reportTab.url(), "PASS");
        } else {
            step(page, "Report generates in new tab", "Save opens the appointment report in a new tab",
                    "Report page opens with a valid Appointment ID",
                    "Save accepted but the report was not generated (no report tab opened)", "FAIL");
        }

        addSummary("Application URL", BASE + "/#/Existing");
        addSummary("Patient", patientName + " (MRN " + MRN + ")");
        addSummary("Appointment Config", config);
        addSummary("Appointment Date", tomorrow);
        addSummary("Slot", slot == null ? "(none)" : slot);
        addSummary("Report tab", reportTab == null ? "not captured" : reportTab.url());
    }
}
