package com.kpj.tests.Investigation_page.Lab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named WorkOrderList — referenced by its fully-qualified name.

/**
 * Investigation &gt; Lab &gt; <b>Work Order List</b> ({@code #/DoctorWorkOrderL}).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Investigation</b> → <b>Lab</b> → <b>Work Order List</b>.</li>
 *   <li>Check what the screen opens with (Date Range ticked, Group Laboratory, Order Status Pending, the Accept / Reject / Print buttons).</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b> — a one-month range ending today — and click <b>Search</b>.</li>
 *   <li>Check the records in <b>Test Details</b> (columns, every order dated inside the range, Pending).</li>
 *   <li>Take the first record's MRN: <b>Find Patient</b> puts that patient's order in <b>List of Orders</b>; <b>Search</b> then lists only that patient's tests, as many as the range search showed for the MRN.</li>
 *   <li><b>Appointment</b>: tick that patient's test in Test Details, click <b>Appointment</b> — the Book Appointment form ({@code #/New}) opens with the patient and the ordered test loaded — fill it with the
 *       Book Appointment page methods (Payor Type, Booking Type, Appointment Type, Department, Doctor, tomorrow's date, a free slot), <b>Add</b>, <b>Save</b>, check the success toast and the appointment report.</li>
 * </ol>
 *
 * <p><b>Accept</b> / <b>Reject</b> are never clicked (the BillingOP / BillingIP flows accept a lab order they finalized themselves). The records are the Pending lab orders the environment holds for
 * the range; when there are none the checks fail saying so (data, not a defect). Pin another range with {@code -Dwol.months=} (months back, default 1).</p>
 *
 * <p>&#9888; The Appointment steps BOOK A REAL APPOINTMENT (tomorrow, first free slot) for the first patient of the list on every run; the earlier steps only read.</p>
 */
public class WorkOrderList extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public WorkOrderList() { super("Investigation_Lab_WorkOrderList"); }

    public static void main(String[] args) {
        WorkOrderList t = new WorkOrderList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Investigation - Lab - Work Order List", "Investigation > Lab > Work Order List",
                "Open the screen, enter a one-month date range, Search, check the records in Test Details, find one patient by MRN, then select the patient and book an appointment from the Appointment button (creates a real appointment).");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int months = Integer.getInteger("wol.months", 1);
        String to = LocalDate.now().format(DMY), from = LocalDate.now().minusMonths(months).format(DMY);
        String tests = com.kpj.pages.Investigation_page.Lab_page.WorkOrderList.TESTS, orders = com.kpj.pages.Investigation_page.Lab_page.WorkOrderList.ORDERS;

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Investigation_page.Lab_page.WorkOrderList wl = new com.kpj.pages.Investigation_page.Lab_page.WorkOrderList(page);

        // 1) Navigate
        boolean on = wl.navigateViaMenu();
        step(page, "Open Work Order List screen", "Click Investigation -> Lab -> Work Order List", "The Work Order List screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Work Order List not reached"); return; }

        // 2) What the screen opens with
        boolean ticked = wl.dateRangeTicked();
        String group = wl.group(), status = wl.orderStatus();
        List<String> buttons = wl.actionButtons();
        int today = wl.rowCount(tests);
        boolean defaults = ticked && group.equalsIgnoreCase("Laboratory") && status.equalsIgnoreCase("Pending") && buttons.containsAll(List.of("Accept", "Reject", "Print"));
        step(page, "Check the screen's default filters", "Read the filters and buttons the screen opens with", "Date Range ticked, Group Laboratory, Order Status Pending; Accept, Reject and Print buttons shown",
                "Date Range " + (ticked ? "ticked" : "NOT ticked") + ", Group " + group + ", Order Status " + status + ", buttons " + buttons + "; Test Details already lists " + today + " test(s) for today",
                defaults ? "PASS" : "FAIL");

        // 3) Dates
        String dates = wl.enterDateRange(from, to);
        boolean datesOk = wl.ok();
        step(page, "Enter from date and to date", "Enter the From Date " + from + " and the To Date " + to + " (a " + months + "-month range)", "Both dates are entered",
                datesOk ? "From Date " + wl.lastFrom + ", To Date " + wl.lastTo + " (" + dates + ")" : "FAILED - " + wl.problems, datesOk ? "PASS" : "FAIL");
        if (!datesOk) { addSummary("Result", "FAILED - dates: " + wl.problems); return; }

        // 4) Search
        int n = wl.clickSearch();
        boolean searched = wl.lastSearchStatus > 0 && wl.lastSearchStatus < 400;
        step(page, "Click search", "Click Search", "The search is answered",
                searched ? "Search answered HTTP " + wl.lastSearchStatus : "FAILED - " + wl.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED - Search: " + wl.problems); return; }

        // 5) Test Details
        List<String> heads = wl.headers(tests);
        List<String> outside = wl.recordsOutsideRange(tests, from, to);
        List<String> statuses = wl.statuses(tests);
        boolean cols = heads.containsAll(List.of("Sample Type", "Order Date & Time", "Patient Name", "MRN No.", "Service"));
        boolean shown = n > 0 && cols && outside.isEmpty();
        step(page, "Verify records are displayed in Test Details", "Read Test Details after Search",
                "The pending lab tests are listed with their patient, service and order date, every one ordered inside the date range",
                shown ? n + " test(s) of " + wl.mrns(tests).size() + " patient(s), all ordered between " + from + " and " + to + "; order status " + statuses + "; first: " + wl.sample(tests)
                      : n == 0 ? "FAILED - Test Details shows 0 tests for " + from + " - " + to + " (no pending lab order in that window on this environment)"
                               : "FAILED - " + n + " test(s) listed but " + (!cols ? "columns are missing (found " + heads + ")" : "these lie outside " + from + " - " + to + ": " + outside),
                shown ? "PASS" : "FAIL");
        addSummary("Test Details", n + " test(s), " + from + " - " + to);
        if (n == 0) return;

        // 6) One patient: List of Orders
        String mrn = wl.mrns(tests).get(0);
        int inRange = wl.recordsOf(tests, mrn);
        int o = wl.findPatient(mrn);
        List<String> orderMrns = wl.orderMrns();
        int drawn = o > 0 ? wl.showOrders() : 0;
        boolean found = wl.ok() && o > 0 && drawn > 0 && orderMrns.stream().allMatch(mrn::equals);
        step(page, "Find the patient and verify List of Orders", "Enter the MRN " + mrn + " (first record of Test Details), click Find Patient and open List of Orders",
                "List of Orders shows that patient's order",
                found ? o + " order(s) listed for MRN " + mrn + "; columns " + wl.headers(orders) : "FAILED - " + (!wl.ok() ? wl.problems : o == 0 ? "List of Orders is empty for MRN " + mrn : drawn == 0 ? "List of Orders holds " + o + " order(s) but draws no row on screen" : "List of Orders holds other MRNs " + orderMrns),
                found ? "PASS" : "FAIL");
        addSummary("List of Orders", o + " order(s), MRN " + mrn);
        if (!found) return;

        // 7) One patient: Search lists only that patient's tests
        int m = wl.clickSearch();
        List<String> mine = wl.mrns(tests);
        boolean only = m > 0 && mine.size() == 1 && mine.get(0).equals(mrn) && m == inRange && wl.recordsOutsideRange(tests, from, to).isEmpty();
        step(page, "Search the patient's tests", "Click Search with MRN " + mrn, "Test Details lists only that patient's tests, as many as the range search showed for the MRN",
                only ? m + " test(s), all for MRN " + mrn + " (the range search showed " + inRange + "); first: " + wl.sample(tests)
                     : "FAILED - " + (!wl.ok() ? wl.problems : "Test Details holds " + m + " test(s) for MRN(s) " + mine + " while the range search showed " + inRange + " for MRN " + mrn),
                only ? "PASS" : "FAIL");
        addSummary("Patient search", m + " test(s), MRN " + mrn);
        if (!only) return;

        // 8) Appointment: select the patient
        int sel = wl.selectPatient(mrn);
        boolean selected = wl.ok() && sel > 0;
        step(page, "Select the patient", "Click the tick symbol of the patient's test in Test Details (MRN " + mrn + ")", "The patient's test is selected",
                selected ? sel + " test(s) of MRN " + mrn + " selected" : "FAILED - " + wl.problems, selected ? "PASS" : "FAIL");
        if (!selected) { addSummary("Result", "FAILED - patient not selected"); return; }

        // 9) Appointment: open the Book Appointment form
        String patient = wl.records(tests).get(0).get(2), service = wl.records(tests).get(0).get(3);
        boolean opened = wl.clickAppointment(mrn);
        String formMrn = opened ? wl.formValue("txtMRNo") : "", formName = opened ? wl.formValue("txtFirstName") : "", formService = opened ? wl.serviceName() : "";
        boolean loaded = opened && formMrn.equals(mrn) && formName.equalsIgnoreCase(patient) && !formService.isEmpty();
        step(page, "Click Appointment", "Click Appointment", "The Book Appointment form opens with the selected patient and the ordered test loaded",
                loaded ? "Book Appointment (" + page.url() + ") opened with MRN " + formMrn + ", Name " + formName + ", Service " + formService
                       : "FAILED - " + (!wl.ok() ? wl.problems : "the form holds MRN \"" + formMrn + "\", Name \"" + formName + "\", Service \"" + formService + "\" instead of " + mrn + " / " + patient + " / " + service),
                loaded ? "PASS" : "FAIL");
        if (!loaded) { addSummary("Result", "FAILED - Appointment: " + wl.problems); return; }

        // 10) Appointment: fill the page with the Book Appointment methods
        ExistingPatientAppointmentPage appt = new ExistingPatientAppointmentPage(page);
        appt.attachToOpenForm();
        String config = appt.fillAppointmentConfig();
        String nric = appt.fillNricIfEmpty();
        String tomorrow = appt.setAppointmentDateTomorrow();
        String slot = appt.selectFreeSlot();
        String mandatory = appt.checkAndFillMandatoryFields();
        boolean configOk = !config.contains("no-opt") && !config.contains("no-select") && !config.contains("no-real-opt");
        boolean filled = configOk && !tomorrow.isEmpty() && slot != null && !mandatory.contains("STILL EMPTY");
        page.evaluate("() => window.scrollTo(0, 0)");
        step(page, "Fill the appointment page", "Select Payor Type, Booking Type, Appointment Type, Department and Doctor; set the Appointment Date to tomorrow; pick a free slot; check the mandatory fields",
                "Every mandatory field holds a value and a free slot is selected",
                filled ? config + (nric.isEmpty() ? "" : " | NRIC " + nric + " (the patient had none and the form requires one)") + " | Appointment Date " + tomorrow + " | slot " + slot + (mandatory.isEmpty() ? "" : " | " + mandatory)
                       : "FAILED - " + (!configOk ? "a select took no value: " + config : tomorrow.isEmpty() ? "the Appointment Date was not set" : slot == null ? "no free slot in the schedule (" + config + ")" : mandatory),
                filled ? "PASS" : "FAIL");
        if (!filled) { addSummary("Result", "FAILED - appointment page not filled"); return; }

        // 11) Appointment: Add, Save and the success toast
        wl.watchMessages();
        int listSize = appt.clickAdd();
        int tabsBefore = page.context().pages().size();
        Page reportTab = listSize > 0 ? appt.saveAndCaptureReport() : null;
        List<String> alerts = appt.capturedMessages();
        List<String> seen = wl.messagesSeen();
        String said = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        List<String> successToasts = seen.stream().filter(s -> s.startsWith("success toast: ")).map(s -> s.substring("success toast: ".length())).collect(java.util.stream.Collectors.toList());
        List<String> errorToasts = seen.stream().filter(s -> s.startsWith("error toast: ")).map(s -> s.substring("error toast: ".length())).collect(java.util.stream.Collectors.toList());
        boolean toastShown = successToasts.stream().anyMatch(s -> s.toLowerCase().matches(".*(success|saved|booked).*"));
        boolean success = listSize > 0 && ExistingPatientAppointmentPage.saveSucceeded(alerts) && toastShown;
        step(page, "Save and verify the success toast", "Click Add, then Save and confirm \"Do You Want To Save\"", "The appointment is saved and a success toast message is shown",
                listSize == 0 ? "FAILED - Add did not register (the booking list is empty)"
                        : success ? "Success toast: \"" + String.join(" | ", successToasts) + "\""
                                + (errorToasts.isEmpty() ? "" : " | a red error toast \"" + String.join(" | ", errorToasts) + "\" was shown as well, with no detail")
                        : "FAILED - no success toast; the screen said: " + said + " | toasts: " + (seen.isEmpty() ? "(nothing recorded)" : seen),
                success ? "PASS" : "FAIL");
        if (!errorToasts.isEmpty()) addSummary("Also shown", "red toast \"" + String.join(" | ", errorToasts) + "\" beside the success toast");
        addSummary("Appointment toast", said);

        // 12) Appointment: the report
        com.kpj.pages.PdfReport.Result rep = com.kpj.pages.PdfReport.capture(page, tabsBefore, reportTab != null ? 8000 : 3000);
        boolean reportOk = !rep.blank;
        byte[] png = rep.png;
        if (png != null && png.length > 0) step(png, "Verify the report", "Read the appointment report the save opened in a new tab", "The appointment report is generated and carries content",
                (reportOk ? "" : "FAILED - ") + rep.diagnostics, reportOk ? "PASS" : "FAIL");
        else step(page, "Verify the report", "Read the appointment report the save opened in a new tab", "The appointment report is generated and carries content",
                (reportOk ? "" : "FAILED - ") + rep.diagnostics, reportOk ? "PASS" : "FAIL");
        addSummary("Appointment", "MRN " + mrn + ", " + tomorrow + ", slot " + slot);
    }
}
