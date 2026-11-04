package com.kpj.tests.AncillaryServices_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named PatientBlackList — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; MRD &gt; <b>Patient Black List</b> ({@code #/PatientBlackList}).
 *
 * <ol>
 *   <li>Enter <b>From Date</b> and <b>To Date</b> (the same day, today); enter the <b>MRN</b> (default 1100339490, pin another with {@code -Dpatientblacklist.mrn=}) and click its search button.</li>
 *   <li>Select a <b>Blacklist Reason</b> and the <b>Location</b>; enter a unique <b>Unblocked Reason</b> and <b>Blacklist Remark</b>; click <b>Submit</b> and verify the success toast.</li>
 *   <li>Verify the record just added is populated in the table - if the table stays blank, or does not show it, this step (and the test) FAILS.</li>
 * </ol>
 *
 * <p>The "Patient Blacklist" checkbox is left alone as the steps do not mention it (ticking it would disable Unblocked Reason, unticking it would disable Blacklist Reason and Remark).</p>
 *
 * <p>This test SAVES A REAL BLACK-LIST RECORD for the MRN on every run; it stays.</p>
 */
public class PatientBlackList extends DevHisBase {

    public PatientBlackList() { super("AncillaryServices_MRD_PatientBlackList"); }

    public static void main(String[] args) {
        PatientBlackList t = new PatientBlackList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - MRD - Patient Black List", "Ancillary Services > MRD > Patient Black List",
                "Enter From/To (same day) and the MRN + search; select blacklist reason and location; enter a unique unblocked reason and a blacklist remark; Submit; verify the success toast; verify the record is populated in the table (blank = FAIL). SAVES A REAL RECORD.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.MRD_page.PatientBlackList bl = new com.kpj.pages.AncillaryServices_page.MRD_page.PatientBlackList(page);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String mrn = System.getProperty("patientblacklist.mrn", "1100339490");

        boolean on = bl.navigateViaMenu();
        step("Open Patient Black List", "Click Ancillary Services -> MRD -> Patient Black List", "The Patient Black List screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Patient Black List not reached"); return; }

        LocalDate day = LocalDate.now();
        boolean fromOk = bl.enterFromDate(day);
        step("Enter from date", "Enter From Date (today, " + day.format(dmy) + ")", "The from date is accepted",
                fromOk ? "From Date " + bl.lastFrom + " entered through the calendar; the box and the screen's model hold it" : "Problem — " + bl.problems, fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary("Result", "FAILED — from date: " + bl.problems); return; }

        boolean toOk = bl.enterToDate(day);
        step("Enter to date", "Enter To Date (the same day, " + day.format(dmy) + ")", "The to date is accepted and equals the from date",
                toOk ? "To Date " + bl.lastTo + " entered through the calendar (the same day as From Date " + bl.lastFrom + "); the box and the screen's model hold it" : "Problem — " + bl.problems, toOk ? "PASS" : "FAIL");
        if (!toOk) { addSummary("Result", "FAILED — to date: " + bl.problems); return; }

        boolean found = bl.searchMrn(mrn);
        List<String[]> history = found ? bl.serverRows(mrn) : null;
        step("Enter MRN and click search", "Enter MRN " + mrn + " and click the search button beside it", "The patient's name and NRIC / Passport are filled in",
                found ? "MRN " + mrn + " searched: Patient Name \"" + bl.lastPatient + "\", NRIC / Passport \"" + bl.lastNric + "\" filled in" + (history == null ? "" : "; the server already holds " + history.size() + " black-list record(s) for this MRN")
                        : "Problem — " + bl.problems, found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — MRN: " + bl.problems); return; }
        if (history == null) history = List.of();

        String reason = bl.selectBlacklistReason(List.of("Disruptive Behavior", "Legal Issues", "Chronic Non-payment"));
        step("Select blacklist reason", "Select the Blacklist Reason", "A blacklist reason is selected",
                !reason.isEmpty() ? "Blacklist Reason \"" + reason + "\" selected (of " + bl.options("PatientData.blacklistid").size() + ")" : "Problem — " + bl.problems, !reason.isEmpty() ? "PASS" : "FAIL");
        if (reason.isEmpty()) { addSummary("Result", "FAILED — blacklist reason: " + bl.problems); return; }

        String location = bl.selectLocation(List.of("DAMANSARA"));
        step("Enter location", "Select the Location (the screen empties it when the patient is loaded)", "A location is selected",
                !location.isEmpty() ? "Location \"" + location + "\" selected (of " + bl.options("PatientData.locationId").size() + ")" : "Problem — " + bl.problems, !location.isEmpty() ? "PASS" : "FAIL");
        if (location.isEmpty()) { addSummary("Result", "FAILED — location: " + bl.problems); return; }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        String unblocked = "Dues settled, cleared by MRD review " + stamp;
        String remark = "Disruptive behaviour at the counter, logged by MRD " + stamp;
        for (String[] h : history) if (h[4].equalsIgnoreCase(unblocked) || h[5].equalsIgnoreCase(remark)) { unblocked += " B"; remark += " B"; break; }
        boolean unOk = bl.enterUnblockedReason(unblocked);
        step("Enter unblocked reason", "Enter a unique Unblocked Reason (" + unblocked + ")", "The unblocked reason is accepted",
                unOk ? "Unblocked Reason \"" + unblocked + "\" entered (stamped with the time, so no earlier record of the MRN's " + history.size() + " has it)" : "Problem — " + bl.problems, unOk ? "PASS" : "FAIL");
        if (!unOk) { addSummary("Result", "FAILED — unblocked reason: " + bl.problems); return; }

        boolean remOk = bl.enterRemark(remark);
        step("Enter blacklist remark", "Enter the Blacklist Remark (" + remark + ")", "The blacklist remark is accepted",
                remOk ? "Blacklist Remark \"" + remark + "\" entered; " + bl.checkboxState() : "Problem — " + bl.problems, remOk ? "PASS" : "FAIL");
        if (!remOk) { addSummary("Result", "FAILED — blacklist remark: " + bl.problems); return; }

        String toast = bl.submitAndGetToast();
        boolean ok = bl.ok() && bl.saveSucceeded();
        step("Click Submit", "Click Submit", "The record is saved",
                bl.problems.isEmpty() ? "Submit clicked" + (bl.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + bl.lastDialog + "\"") : "Problem — " + bl.problems, bl.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = bl.savedByServer();
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (bl.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + bl.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the record (ResultStatus 1, \"" + bl.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + bl.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + bl.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // RULE (the user's): if the data added is not populated in the table and it stays blank, this step - and so the test - is a FAIL.
        String[] row = (ok || savedByServer) ? bl.awaitInTable(mrn, unblocked, remark, 15000) : null;
        String[] held = (ok || savedByServer) ? bl.serverHolds(mrn, unblocked, remark) : null;
        List<String[]> tableNow = bl.tableRows();
        step("Verify the data added is populated in the table", "Look for the new record (MRN, unblocked reason, remark) in the table", "The new record is listed in the table",
                row != null ? "PASSES because the table shows MRN " + row[0] + " / " + row[1] + " / Blacklisted \"" + row[3] + "\" / reason \"" + row[4] + "\" / unblocked reason \"" + row[9] + "\" / remark \"" + row[10] + "\" / " + row[11]
                    : "FAILS because the table " + (tableNow.isEmpty() ? "stays BLANK" : "does not show the new record (it shows " + tableNow.size() + " row(s), pager \"" + bl.pagerText() + "\")")
                      + " - the screen's own list holds " + bl.scopeRows() + " row(s); "
                      + (held != null ? "the SERVER DOES hold the record (id " + held[0] + ", blacklisted " + held[2] + ", reason \"" + held[3] + "\") - the table is not populated with it" : "the server does not hold it either"),
                row != null ? "PASS" : "FAIL");

        // not a step: when the table stayed blank, press the screen's own Search by hand to tell "not refreshed" from "never shown"
        String manual = "(not needed - the table already shows the record)";
        if (row == null && (ok || savedByServer)) {
            int n = bl.clickSearch();
            String[] again = bl.findInTable(mrn, unblocked, remark);
            manual = n < 0 ? "(the Search button could not be clicked)"
                    : "Search pressed by hand (the form fields stay filled after saving and the dates stay " + bl.lastFrom + " - " + bl.lastTo + "): the table then shows " + n + " row(s) (pager \"" + bl.pagerText() + "\") and the new record "
                      + (again != null ? "IS among them" : "is NOT among them");
        }

        // not a step: the same search asked of the server with and without the dates, to show why the table stays blank
        String why = "(not checked)";
        if (row == null && (ok || savedByServer)) {
            List<String[]> withDates = bl.serverRows(mrn, bl.lastFrom, bl.lastTo), noDates = bl.serverRows(mrn);
            String[] rec = held != null ? held : (noDates != null && !noDates.isEmpty() ? noDates.get(0) : null);
            why = "the grid's own search for MRN " + mrn + " WITH the dates " + bl.lastFrom + " - " + bl.lastTo + " returns " + (withDates == null ? "(unreadable)" : withDates.size() + " row(s)")
                    + ", WITHOUT the dates " + (noDates == null ? "(unreadable)" : noDates.size() + " row(s)")
                    + (rec != null ? "; the record (id " + rec[0] + ") has Blocked Date/Time \"" + (rec[6].isEmpty() ? "(empty)" : rec[6]) + "\" and blacklisted=" + rec[2] + " - the server's date filter looks at the Blocked Date/Time only, so a record saved as an unblock never matches a date range" : "");
        }

        addSummary("After pressing Search by hand", manual);
        addSummary("Why the table is blank", why);
        addSummary("From / To", bl.lastFrom + " / " + bl.lastTo + " (the same day)");
        addSummary("MRN / Patient", mrn + " / " + bl.lastPatient + " (" + bl.lastNric + ")");
        addSummary("Blacklist reason / Location", bl.lastReason + " / " + bl.lastLocation);
        addSummary("Unblocked reason / Blacklist remark", unblocked + " / " + remark);
        addSummary("Patient Blacklist box", bl.checkboxState());
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + bl.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("On the server afterwards", held != null ? "yes - id " + held[0] + ", blacklisted " + held[2] + ", reason " + held[3] : "(not found)");
        addSummary("Table", row != null ? "shows the new record" : (tableNow.isEmpty() ? "BLANK - the new data is not populated" : "does not show the new record"));
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok && row != null ? toast : (row == null ? "FAIL - the data is not populated in the table" + (tableNow.isEmpty() ? " (the table stays blank)" : "") + (ok ? "; the save itself worked (\"" + toast + "\")" : "")
                : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast))));
    }
}
