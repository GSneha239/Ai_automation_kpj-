package com.kpj.tests.Op_Page.Appointment;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.Appointment.BookAppointmentPage;
import com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage;
import com.kpj.pages.Op_page.Appointment.SelfArrivalPage;
import com.microsoft.playwright.Page;

import java.util.ArrayList;
import java.util.List;

/**
 * OP &gt; <b>Self Arrival</b>: pick a patient (PR No = MRN), Search, check the appointment is listed in the table, click <b>Mark Arrival</b> and verify the success toast.
 *
 * <p>The screen lists only appointments that are due TODAY (a Search with PR No / Name / Mobile No all empty lists every one of them). The test therefore first looks at that list and
 * uses the MRN of a listed appointment; when NOTHING is due it calls the <b>OP &gt; Appointment &gt; Existing</b> booking flow ({@link ExistingPatientAppointmentPage} - the same page object
 * the Existing appointment test uses) to book one for TODAY, then comes back and uses that appointment. Pin a patient with {@code -Dmrn=}; force the booking even when appointments are
 * due with {@code -Dselfarrival.book=always}; the patient booked for is {@code -Dexisting.mrn=} (default 1100338959).</p>
 *
 * <p>&#9888; Mark Arrival changes a real appointment's status. It acts on the SELECTED row (the last one rendered unless another is clicked), so the test clicks the intended row first and
 * never presses Mark Arrival when nothing is listed (the selection would then be a stale one from an earlier search).</p>
 */
public class SelfArrivalTest extends DevHisBase {

    private static final int MAX_CANDIDATES = Integer.getInteger("selfarrival.candidates", 40);
    /** -Dselfarrival.book=always books an appointment first even when some are already due (default: only when none is due). */
    private static final boolean ALWAYS_BOOK = "always".equalsIgnoreCase(System.getProperty("selfarrival.book", ""));
    /** The registered patient the booking step books for. */
    private static final String BOOK_MRN = System.getProperty("existing.mrn", "1100338959").trim();

    /** Appointment id (the report URL's Appointmentid) of the appointment this run booked, "" when none. */
    private String bookedId = "";

    public SelfArrivalTest() { super("op_appointment_SelfArrival"); }

    public static void main(String[] args) {
        SelfArrivalTest t = new SelfArrivalTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OP - Self Arrival", "OP > Self Arrival",
                "&#9888; Marks a REAL appointment as arrived: find a patient with an appointment due today (booking one through OP > Appointment > Existing when none is due), "
                        + "enter the PR No (MRN), Search, check the table, click Mark Arrival, verify the success toast.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        SelfArrivalPage sa = new SelfArrivalPage(page);
        boolean on = sa.navigateViaMenu();
        step(page, "Open Self Arrival screen", "Click OP -> Self Arrival",
                "The Self Arrival screen (PR No, Name, Mobile No, Search, Mark Arrival) is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        // 1) what is due today? (a pinned patient skips this)
        String pinned = System.getProperty("mrn", "").trim();
        List<String> dueMrns = new ArrayList<>();
        if (pinned.isEmpty()) {
            int dueCount = sa.searchAllDue();
            List<String[]> rows = sa.dueRows();
            dueMrns = sa.dueMrns();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(rows.size(), 5); i++) {
                String[] r = rows.get(i);
                sb.append(i == 0 ? "" : "; ").append(r[1].isEmpty() ? "(no MRN)" : r[1]).append(" ").append(r[2]).append(" ").append(r[3]).append(" ").append(r[4]).append(" ").append(r[5]);
            }
            step(page, "Check the appointments due today", "Leave PR No / Name / Mobile No empty and click Search",
                    "The appointments due today are listed",
                    dueCount > 0 ? dueCount + " appointment(s) due today" + (dueMrns.isEmpty() ? ", none with an MRN" : " (" + dueMrns.size() + " with an MRN)") + ": " + sb
                                 : "No appointment is due today - one is booked below (OP > Appointment > Existing)",
                    dueMrns.isEmpty() ? "MANUAL" : "PASS");
        }

        // 2) none due (or forced): book one for TODAY with the OP > Appointment > Existing flow, then return here
        String bookedMrn = "";
        if (pinned.isEmpty() && (dueMrns.isEmpty() || ALWAYS_BOOK)) {
            bookedMrn = bookAppointmentForToday();
            if (!bookedMrn.isEmpty()) {
                boolean back = sa.navigateViaMenu();
                step(page, "Back to the Self Arrival screen", "Click OP -> Self Arrival", "The Self Arrival screen is shown",
                        back ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", back ? "PASS" : "FAIL");
                if (!back) { addSummary("Result", "FAILED - Self Arrival not reached after booking"); return; }
            }
        }

        // 3) the PR No (MRN) to use
        List<String> mrns = new ArrayList<>();
        String source;
        if (!pinned.isEmpty()) { mrns.add(pinned); source = "pinned with -Dmrn"; }
        else if (!bookedMrn.isEmpty()) { mrns.add(bookedMrn); source = "the appointment just booked (id " + bookedId + ")"; }
        else if (!dueMrns.isEmpty()) { mrns.addAll(dueMrns); source = "the MRNs in today's due list"; }
        else { mrns.addAll(sa.candidateMrns(MAX_CANDIDATES)); source = "the Patient Search popup (no appointment is due and none could be booked)"; }
        step(page, "Choose the PR No (MRN)", "Take the PR No (MRN) of a patient with an appointment due today",
                "A patient (PR No) is chosen", mrns.isEmpty() ? "No patient to try" : mrns.size() + " patient(s) to try from " + source + ", first " + mrns.get(0),
                mrns.isEmpty() ? "FAIL" : "PASS");
        if (mrns.isEmpty()) { addSummary("Result", "FAILED - no patient"); return; }

        // 4) enter the PR No and Search - next patient until one has an appointment listed
        String used = "";
        int tried = 0, rows = 0;
        for (String m : mrns) {
            tried++;
            sa.enterMrn(m);
            sa.clickSearch();
            rows = sa.rowCount();
            used = m;
            if (rows > 0) break;
        }
        boolean entered = sa.mrnBoxValue().equals(used);
        step(page, "Enter PR No and click Search", "Enter the PR No (MRN) chosen above and click Search",
                "The PR No is entered and the search runs",
                "PR No = " + used + (entered ? "" : " (box shows '" + sa.mrnBoxValue() + "')") + "; " + tried + " patient(s) searched", entered ? "PASS" : "FAIL");

        // the row to mark: the appointment this run booked when it is listed, else the first row
        int markIdx = 0;
        if (!bookedId.isEmpty()) {
            List<String[]> listed = sa.dueRows();
            for (int i = 0; i < listed.size(); i++) if (listed.get(i)[0].equals(bookedId)) { markIdx = i; break; }
        }
        boolean data = rows > 0;
        String selected = data ? sa.selectRow(markIdx) : "";
        step(page, "Verify the data is displayed in the table", "Look at the appointment table after Search",
                "The patient's appointment is listed in the table",
                data ? rows + " row(s): " + sa.rowsText() + (selected.isEmpty() ? "" : " | selected for Mark Arrival: " + selected)
                     : "The table is EMPTY for all " + tried + " patient(s) tried (last " + used + ") - none has an appointment due on this screen",
                data ? "PASS" : "FAIL");
        addSummary("PR No (MRN)", used);
        addSummary("Patients searched", String.valueOf(tried));
        addSummary("Appointment source", bookedMrn.isEmpty() ? (pinned.isEmpty() ? "already due today" : "pinned patient") : "booked by this run (OP > Appointment > Existing), id " + bookedId);

        // 5) Mark Arrival - only when an appointment is listed AND selected
        if (!data) {
            step(page, "Click Mark Arrival and verify the success toast", "Click Mark Arrival; read the toast",
                    "A success toast (arrival marked) is shown",
                    "NOT clicked - no appointment is listed. Mark Arrival acts on the last selected row (a stale one from an earlier search when the table is empty), so clicking it would mark an unrelated appointment.",
                    "FAIL");
            addSummary("Result", "FAILED - no appointment listed; Mark Arrival not clicked");
            return;
        }
        String toast = sa.clickMarkArrival();
        String tl = toast.toLowerCase();
        boolean ok = !toast.isEmpty() && tl.matches(".*(success|saved|updated|arrived|marked).*") && !tl.matches(".*(please|fail|error|invalid|not ).*");
        String markActual = (toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Not a success message: \"" + toast + "\""))
                + (sa.lastMarkedRow.isEmpty() ? "" : " | marked: " + sa.lastMarkedRow);
        // the screenshot was taken while the toast was on screen, so the report shows the message itself
        if (sa.toastPng != null && sa.toastPng.length > 0) {
            step(sa.toastPng, "Click Mark Arrival and verify the success toast", "Click Mark Arrival; read the toast",
                    "A success toast (arrival marked) is shown", markActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Mark Arrival and verify the success toast", "Click Mark Arrival; read the toast",
                    "A success toast (arrival marked) is shown", markActual, ok ? "PASS" : "FAIL");
        }
        addSummary("Marked appointment", sa.lastMarkedRow.isEmpty() ? "(not read)" : sa.lastMarkedRow);
        addSummary("Result", toast.isEmpty() ? "No toast" : toast);
    }

    /**
     * Book one appointment for TODAY through the OP &gt; Appointment &gt; Existing flow (same page object as the Existing appointment test, in this browser session) so Self Arrival has
     * something due. Returns the MRN booked for, or "" when the booking did not complete; {@link #bookedId} gets the appointment id from the report URL.
     */
    private String bookAppointmentForToday() {
        bookedId = "";
        ExistingPatientAppointmentPage appt = new ExistingPatientAppointmentPage(page);
        boolean onEx = appt.navigateViaMenu();
        step(page, "Book · Open OP > Appointment > Existing", "Click OP -> Appointment -> Existing",
                "The Existing appointment screen is shown", onEx ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", onEx ? "PASS" : "FAIL");
        if (!onEx) return "";

        String name = appt.searchExistingPatientByMrn(BOOK_MRN);
        step(page, "Book · Enter MRN, click Search", "Enter MRN " + BOOK_MRN + "; click Search (FindPatient)",
                "The existing patient's details load into the form",
                name.isEmpty() ? "No patient loaded for MRN " + BOOK_MRN : "Loaded: " + name + " (MRN " + BOOK_MRN + ")", name.isEmpty() ? "FAIL" : "PASS");
        if (name.isEmpty()) return "";

        String config = appt.fillAppointmentConfig();
        boolean configOk = !config.contains("no-opt") && !config.contains("no-select") && !config.contains("no-real-opt");
        step(page, "Book · Appointment Type, Department, Booking Type, Payor Type", "Select Appointment Type (Department), Department, Booking Type, Payor Type (and Doctor)",
                "All selects accept a real value", config, configOk ? "PASS" : "FAIL");

        String slot = appt.selectFreeSlotToday();
        step(page, "Book · Select a slot for TODAY", "Pick a free slot from Current Schedule (today); the Appointment Date becomes today; set From/To time",
                "A slot for today is selected and From/To populated", slot == null ? "No free slot found in today's Current Schedule" : "Slot selected: " + slot + " (today)",
                slot == null ? "FAIL" : "PASS");
        if (slot == null) return "";

        String mandatory = appt.checkAndFillMandatoryFields();
        boolean stillEmpty = mandatory.contains("STILL EMPTY");
        step(page, "Book · Check all mandatory fields are filled", "Re-check Payor/Booking/Appointment Type/Department/Doctor/Date/From/To; fill any still on \"-Select-\"",
                "No mandatory field is left unfilled", mandatory.isEmpty() ? "All mandatory fields were already filled" : mandatory, stillEmpty ? "FAIL" : "PASS");
        String mobileFix = appt.fixMobileNumberIfInvalid();
        if (!mobileFix.isEmpty()) {
            step(page, "Book · Enter a valid Mobile No", "Save refuses a mobile number whose length differs from the configured digit count; enter one of the right length",
                    "The Mobile No holds the configured number of digits", mobileFix, mobileFix.startsWith("(") ? "FAIL" : "PASS");
        }

        int listSize = appt.clickAdd();
        step(page, "Book · Click Add", "Click 'Add' (fnAddToMultipleApptList) before Save", "Appointment added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)", listSize > 0 ? "PASS" : "FAIL");

        Page reportTab = appt.saveAndCaptureReport();
        List<String> alerts = appt.capturedAlerts();
        String alertMsg = alerts.isEmpty() ? "(none captured)" : String.join(" | ", alerts);
        boolean saved = reportTab != null || BookAppointmentPage.saveSucceeded(appt.capturedMessages());
        String reportUrl = reportTab == null ? "" : reportTab.url();
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("Appointmentid=(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(reportUrl);
        if (mt.find()) bookedId = mt.group(1);
        step(page, "Book · Save, confirm, accept the alert", "Click Save; confirm 'Do You Want To Save'; accept the slot/booking alert",
                "Appointment saved; report opens in a new tab",
                (saved ? "" : "NOT saved - ") + alertMsg + (reportUrl.isEmpty() ? "" : " | report " + reportUrl), saved ? "PASS" : "FAIL");
        if (reportTab != null) {
            try { if (!reportTab.isClosed()) reportTab.close(); } catch (Exception ignore) { }
        }
        try { page.bringToFront(); } catch (Exception ignore) { }
        addSummary("Booked for", name + " (MRN " + BOOK_MRN + "), today, slot " + slot + (bookedId.isEmpty() ? "" : ", appointment id " + bookedId));
        return saved ? BOOK_MRN : "";
    }
}
