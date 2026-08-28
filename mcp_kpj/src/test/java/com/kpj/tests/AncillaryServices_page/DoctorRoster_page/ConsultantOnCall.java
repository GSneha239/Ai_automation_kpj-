package com.kpj.tests.AncillaryServices_page.DoctorRoster_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ConsultantOnCall — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Doctor Roster &gt; <b>Consultant On Call</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Doctor Roster</b> → <b>ConsultantOnCall</b>.</li>
 *   <li>Click <b>New Roster</b>.</li>
 *   <li>Select <b>From Date</b>, <b>To Date</b> and <b>Department</b>.</li>
 *   <li>Select <b>Doctor</b>, <b>Duty</b> and <b>Consultation Room</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Click <b>Save</b> → verify the popup and the success toast.</li>
 * </ol>
 *
 * <p>Dates default to today → today + 7 days; override with {@code -DfromDate=} / {@code -DtoDate=}
 * ({@code yyyy-MM-dd}).</p>
 *
 * <p>&#9888; A successful run CREATES a real consultant on-call roster in the target environment.</p>
 */
public class ConsultantOnCall extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ConsultantOnCall() { super("AncillaryServices_DoctorRoster_ConsultantOnCall"); }

    public static void main(String[] args) {
        ConsultantOnCall t = new ConsultantOnCall();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Consultant On Call", "Ancillary Services > Doctor Roster > Consultant On Call",
                "&#9888; Creates a REAL consultant on-call roster: New Roster, pick dates + department, "
                        + "pick doctor + duty + consultation room, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        // DevHIS rejects a roster whose date/doctor/duty combination already exists ("One or more roster
        // rows already exist..."), so a fixed window makes this test pass once and fail on every re-run.
        // Default to a FUTURE window that shifts with the time of day, giving each run its own free slot.
        // Pin it with -DfromDate=/-DtoDate= (yyyy-MM-dd) when you want a specific range.
        java.time.LocalDate base = java.time.LocalDate.now()
                .plusDays(30 + (java.time.LocalTime.now().toSecondOfDay() % 90));
        String fromDate = System.getProperty("fromDate", base.toString());
        String toDate = System.getProperty("toDate", base.plusDays(6).toString());

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.DoctorRoster_page.ConsultantOnCall roster =
                new com.kpj.pages.AncillaryServices_page.DoctorRoster_page.ConsultantOnCall(page);

        // 1) Navigate
        boolean onScreen = roster.navigateViaMenu();
        step(page, "Open Consultant On Call screen",
                "Click Ancillary Services -> Doctor Roster -> ConsultantOnCall",
                "The Consultant On Call screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New Roster
        boolean formOpen = roster.clickNewRoster();
        step(page, "Click New Roster", "Click New Roster (fnGoToNewRoster)",
                "The new roster form opens",
                formOpen ? "Roster form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — roster form not reached"); return; }

        roster.describeControls();   // diagnostics only

        // 3) From Date / To Date / Department
        String dd = roster.selectDatesAndDepartment(fromDate, toDate);
        boolean ddOk = roster.datesAndDepartmentSet();
        step(page, "Select From Date, To Date and Department",
                "Set From Date " + fromDate + ", To Date " + toDate + ", then select a Department",
                "Both dates and a Department are set", dd, ddOk ? "PASS" : "FAIL");

        // 4) Doctor / Duty / Consultation Room
        String ddr = roster.selectDoctorDutyAndRoom();
        boolean ddrOk = roster.doctorDutyRoomSelected();
        step(page, "Select Doctor, Duty and Consultation Room",
                "Select a Doctor, a Duty (ui-select) and a Consultation Room",
                "Doctor, Duty and Consultation Room are all selected", ddr, ddrOk ? "PASS" : "FAIL");

        // 5) Add the row
        String added = roster.clickAdd();
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        step(page, "Click Add", "Click Add (fnAddRow) to add the roster line",
                "A roster row is added to the grid", added, addOk ? "PASS" : "FAIL");

        // 6) Save -> popup + toast
        String toast = roster.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String popup = roster.lastPopup;
        String actual = (popup == null || popup.isEmpty() ? "No popup captured" : "Popup: \"" + popup + "\"")
                + " -> " + (toast == null || toast.isEmpty() ? "No toast appeared" : toast);
        step(page, "Click Save & verify popup + success toast",
                "Click Save (fnSaveRoster); confirm the popup; wait for the success toast",
                "A confirmation popup is shown and a '... saved successfully' toast follows",
                ok ? actual : "Save not confirmed — " + actual,
                ok ? "PASS" : "FAIL");

        addSummary("From Date / To Date", fromDate + " -> " + toDate);
        addSummary("Department", roster.lastDepartment);
        addSummary("Doctor", roster.lastDoctor);
        addSummary("Duty", roster.lastDuty);
        addSummary("Consultation Room", roster.lastRoom);
        addSummary("Popup", popup == null || popup.isEmpty() ? "(none captured)" : popup);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
