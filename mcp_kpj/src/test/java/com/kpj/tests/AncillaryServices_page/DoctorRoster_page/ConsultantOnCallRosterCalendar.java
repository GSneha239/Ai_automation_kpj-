package com.kpj.tests.AncillaryServices_page.DoctorRoster_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.AncillaryServices_page.DoctorRoster_page.ConsultantOnCall;

/**
 * Ancillary Services &gt; Doctor Roster &gt; <b>Consultant On Call</b> — Roster Calendar / Print.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>ConsultantOnCall</b>.</li>
 *   <li>Click <b>Roster Calendar</b>.</li>
 *   <li>Click the <b>print</b> symbol.</li>
 *   <li>Verify the print popup appeared.</li>
 *   <li>Click <b>Cancel</b>.</li>
 *   <li>Verify the print popup closed.</li>
 *   <li>Click <b>Back</b>.</li>
 * </ol>
 *
 * <p>Read-only: this flow prints and navigates, it creates no records — unlike the sibling
 * {@code ConsultantOnCall} flow, which creates a roster.</p>
 */
public class ConsultantOnCallRosterCalendar extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ConsultantOnCallRosterCalendar() { super("AncillaryServices_DoctorRoster_ConsultantOnCallRosterCalendar"); }

    public static void main(String[] args) {
        ConsultantOnCallRosterCalendar t = new ConsultantOnCallRosterCalendar();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        // The breadcrumb names the Roster Calendar explicitly. Reports are named after this path, and
        // stopping at "Consultant On Call" would give this flow the same file name as the sibling
        // ConsultantOnCall test — one would overwrite the other.
        meta("Ancillary Services - Doctor Roster - Consultant On Call - Roster Calendar",
                "Ancillary Services > Doctor Roster > Consultant On Call > Roster Calendar",
                "Open the Roster Calendar, print it, confirm the print popup, cancel it, and go Back. "
                        + "Read-only — nothing is saved.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        ConsultantOnCall roster = new ConsultantOnCall(page);

        // 1) Navigate
        boolean onScreen = roster.navigateViaMenu();
        step(page, "Open Consultant On Call screen",
                "Click Ancillary Services -> Doctor Roster -> ConsultantOnCall",
                "The Consultant On Call screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Roster Calendar
        boolean onCal = roster.clickRosterCalendar();
        step(page, "Click Roster Calendar", "Click Roster Calendar (fnGoToCalendar)",
                "The roster calendar is shown",
                onCal ? "Calendar opened (" + page.url() + ")" : "Calendar did NOT open (" + page.url() + ")",
                onCal ? "PASS" : "FAIL");
        if (!onCal) { addSummary("Result", "FAILED — calendar not reached"); return; }

        // Populate the calendar so there is something to print.
        String dept = roster.selectCalendarDepartment();
        roster.describeCalendarControls();   // diagnostics only

        // 3) Print
        String printed = roster.clickPrint();
        boolean printOk = printed != null && !printed.isEmpty();
        step(page, "Click print symbol", "Click the print symbol on the roster calendar",
                "The print action is triggered",
                printOk ? "Print clicked -> " + printed : "No print control found on the calendar",
                printOk ? "PASS" : "FAIL");

        // 4) Verify the popup
        String popup = roster.capturePrintPopup();
        boolean popupOk = popup != null && !popup.isEmpty();
        step(page, "Verify print popup", "Wait for the print popup / report to appear",
                "A print popup (in-page dialog or report tab) is shown",
                popupOk ? popup
                        : "No in-page popup and no report tab appeared — a NATIVE Chromium print dialog "
                          + "cannot be observed by Playwright (browser chrome, not DOM)",
                popupOk ? "PASS" : "FAIL");

        // 5) Cancel
        String cancelled = roster.cancelPrintPopup();
        boolean cancelOk = cancelled != null && !cancelled.isEmpty();
        step(page, "Click cancel", "Dismiss the print popup via Cancel",
                "The popup is dismissed",
                cancelOk ? "Cancelled -> " + cancelled : "No Cancel control found",
                cancelOk ? "PASS" : "FAIL");

        // 6) Verify it closed
        boolean closed = roster.printPopupClosed();
        step(page, "Verify print popup closed", "Confirm no popup or report tab remains",
                "The print popup is closed",
                closed ? "No popup or report tab remains" : "A popup / report tab is STILL open",
                closed ? "PASS" : "FAIL");

        // 7) Back
        boolean back = roster.clickBack();
        step(page, "Click Back", "Click Back (fnBack) to leave the calendar",
                "The calendar is left",
                back ? "Back clicked -> " + page.url() : "Still on the calendar (" + page.url() + ")",
                back ? "PASS" : "FAIL");

        addSummary("Calendar Department", dept);
        addSummary("Print control", printOk ? printed : "(not found)");
        addSummary("Print popup", popupOk ? popup : "(none observed)");
        addSummary("Popup closed", closed ? "yes" : "no");
    }
}
