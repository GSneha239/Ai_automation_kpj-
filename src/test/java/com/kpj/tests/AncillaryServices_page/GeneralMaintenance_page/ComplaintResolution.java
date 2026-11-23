package com.kpj.tests.AncillaryServices_page.GeneralMaintenance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ComplaintResolution (and so is the Equipment/Asset test) — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; General Maintenance &gt; <b>Complaint Resolution</b>.
 *
 * <ol>
 *   <li>In the <b>Complaint</b> table click one row (the newest complaint that is still unresolved) and verify the <b>Complaint Details</b> table is populated with it.</li>
 *   <li>Click the view (eye) symbol: the Complaint Resolution Detail form opens.</li>
 *   <li>Select <b>Completed By</b>, <b>Verified By</b>, <b>Resolved Status</b> and <b>Reported By</b>; enter the <b>Amount Incurred</b>; select the <b>Employee</b>; enter the <b>Down Date</b> and <b>Down Time</b>
 *       (the Down box that holds them is ticked).</li>
 *   <li>Click <b>Save</b>, verify the success toast, and that the complaint now shows its Resolved Status.</li>
 * </ol>
 *
 * <p>Work Done and Remarks are optional and left empty; the Complaint Resolution Date stays at today. Override the amount with {@code -Dresolution.amount=}.</p>
 *
 * <p>This test SAVES A REAL COMPLAINT RESOLUTION on every run; the complaint it resolves (created by the Complaint Details test) is changed for good.</p>
 */
public class ComplaintResolution extends DevHisBase {

    public ComplaintResolution() { super("AncillaryServices_GeneralMaintenance_ComplaintResolution"); }

    public static void main(String[] args) {
        ComplaintResolution t = new ComplaintResolution();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - General Maintenance - Complaint Resolution", "Ancillary Services > General Maintenance > Complaint Resolution",
                "Click a complaint row; verify its details are shown; click the eye; select completed by, verified by, resolved status, reported by; enter the amount incurred; select the employee; enter the down date and time; Save; verify the success toast. SAVES A REAL COMPLAINT RESOLUTION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintResolution cr = new com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintResolution(page);

        boolean on = cr.navigateViaMenu();
        step("Open Complaint Resolution", "Click Ancillary Services -> General Maintenance -> Complaint Resolution", "The General Maintenance Complaint Resolution screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Complaint Resolution not reached"); return; }

        List<String[]> complaints = cr.awaitComplaints();
        step("Read the complaints in the table", "Read the Complaint table", "The complaints are listed",
                complaints.isEmpty() ? "The Complaint table has no rows" : complaints.size() + " complaint(s): " + complaints.stream().map(c -> c[0] + " (" + c[3] + ", " + c[1] + ")").toList(),
                complaints.isEmpty() ? "FAIL" : "PASS");
        if (complaints.isEmpty()) { addSummary("Result", "FAILED — no complaint to resolve (run the Complaint Details test first)"); return; }

        StringBuilder looked = new StringBuilder();
        List<String[]> details = cr.pickAndClickComplaint(complaints, looked);
        String no = cr.lastComplaintNo;
        boolean rowOk = cr.problems.isEmpty() && !no.isEmpty();
        boolean wasUnresolved = !details.isEmpty() && details.get(0)[4].isEmpty();
        step("Click on a row of data in the Complaint table", "Click one row of the Complaint table (the newest unresolved complaint)", "The row is selected",
                rowOk ? "Clicked " + no + " (looked at: " + looked + "); row highlighted: " + cr.rowSelected(no)
                        + (wasUnresolved ? "" : "; EVERY complaint was already resolved, so the newest is reopened (its form offers Modify instead of Save) - run the Complaint Details test first for a new one")
                        : "Problem — " + cr.problems, rowOk ? "PASS" : "FAIL");
        if (!rowOk) { addSummary("Result", "FAILED — row: " + cr.problems); return; }

        boolean populated = !details.isEmpty() && !details.get(0)[0].isEmpty() && !details.get(0)[5].isEmpty();
        String[] d = populated ? details.get(0) : new String[6];
        step("Verify the selected row is populated in Complaint Details", "Read the Complaint Details table", "The details of the selected complaint are shown",
                populated ? "Complaint Details shows " + details.size() + " row(s) for " + no + ": type \"" + d[0] + "\", problem date " + d[1] + ", reported date " + d[2] + ", reported by \"" + d[3]
                        + "\", resolved status \"" + (d[4].isEmpty() ? "(none yet)" : d[4]) + "\", details \"" + d[5] + "\"" : "The Complaint Details table is empty after clicking " + no, populated ? "PASS" : "FAIL");
        if (!populated) { addSummary("Result", "FAILED — the Complaint Details table did not fill"); return; }

        boolean eye = cr.clickEye();
        step("Click the view (eye) symbol", "Click the eye symbol of the detail row", "The Complaint Resolution Detail form opens",
                eye ? "Form opened (" + cr.lastFormUrl + "): Resolve No. " + cr.lastResolveNo + ", Complaint Resolution Date " + cr.lastResolutionDate + ", buttons: " + cr.lastSaveButton : "Problem — " + cr.problems,
                eye ? "PASS" : "FAIL");
        if (!eye) { addSummary("Result", "FAILED — eye: " + cr.problems); return; }

        String completed = cr.selectCompletedBy();
        step("Select Completed By", "Select Completed By", "A person is selected", cr.ok() ? "Completed By \"" + completed + "\" selected (the person the complaint was reported to)" : "Problem — " + cr.problems, cr.ok() ? "PASS" : "FAIL");
        if (!cr.ok()) { addSummary("Result", "FAILED — completed by: " + cr.problems); return; }

        String verified = cr.selectVerifiedBy();
        step("Select Verified By", "Select Verified By", "A person is selected", cr.ok() ? "Verified By \"" + verified + "\" selected" : "Problem — " + cr.problems, cr.ok() ? "PASS" : "FAIL");
        if (!cr.ok()) { addSummary("Result", "FAILED — verified by: " + cr.problems); return; }

        String status = cr.selectResolvedStatus();
        step("Select Resolved Status", "Select Resolved Status", "A status is selected",
                cr.ok() ? "Resolved Status \"" + status + "\" selected (all 30 statuses are generated \"Auto complaint status …\" names, so the highest-numbered one is used; it differs from the initial status the complaint was created with)"
                        : "Problem — " + cr.problems, cr.ok() ? "PASS" : "FAIL");
        if (!cr.ok()) { addSummary("Result", "FAILED — resolved status: " + cr.problems); return; }

        String by = cr.selectReportedBy();
        step("Select Reported By", "Select Reported By", "A person is selected", cr.ok() ? "Reported By \"" + by + "\" selected (the reporter of the complaint)" : "Problem — " + cr.problems, cr.ok() ? "PASS" : "FAIL");
        if (!cr.ok()) { addSummary("Result", "FAILED — reported by: " + cr.problems); return; }

        String amount = System.getProperty("resolution.amount", "350");
        boolean amt = cr.enterAmountIncurred(amount);
        step("Enter Amount Incurred", "Enter the Amount Incurred", "The amount is accepted", amt ? "Amount Incurred \"" + amount + "\" entered (a plausible repair cost for a ceiling leak)" : "Problem — " + cr.problems, amt ? "PASS" : "FAIL");
        if (!amt) { addSummary("Result", "FAILED — amount: " + cr.problems); return; }

        String emp = cr.selectEmployee();
        step("Select employee", "Select the Employee", "A person is selected", cr.ok() ? "Employee \"" + emp + "\" selected (In house allocation left as it was; the same person who completed the work)" : "Problem — " + cr.problems, cr.ok() ? "PASS" : "FAIL");
        if (!cr.ok()) { addSummary("Result", "FAILED — employee: " + cr.problems); return; }

        String downDate = cr.todayForDownDate();
        boolean dd = !downDate.isEmpty() && cr.enterDownDate(downDate);
        step("Enter Down date", "Tick Down and enter the Down Date", "The date is accepted",
                dd ? "Down ticked; Down Date \"" + downDate + "\" picked in the calendar (today; the box's placeholder says yyyy-MM-dd but its picker really uses dd/MM/yyyy and discards typed text)" : "Problem — " + (downDate.isEmpty() ? "the form's resolution date \"" + cr.lastResolutionDate + "\" could not be converted" : cr.problems), dd ? "PASS" : "FAIL");
        if (!dd) { addSummary("Result", "FAILED — down date: " + cr.problems); return; }

        String downTime = cr.nowAsTime();
        boolean dt = cr.enterDownTime(downTime);
        step("Enter down time", "Enter the Down Time", "The time is accepted", dt ? "Down Time \"" + downTime + "\" entered (the current time)" : "Problem — " + cr.problems, dt ? "PASS" : "FAIL");
        if (!dt) { addSummary("Result", "FAILED — down time: " + cr.problems); return; }

        // Attempt 1 is what a user does: the date picked in the calendar (dd/MM/yyyy) and the time as the box shows it. If the SERVER refuses with a date-conversion error, the same form is saved again with
        // the date as the box's placeholder asks (yyyy-MM-dd) and/or a 24-hour time — nothing is written by a refused save, and each attempt is listed in the report.
        String isoDate = com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintResolution.iso(downDate);
        String time24 = cr.nowAs24h();
        String[][] combos = {{"picker", "12h"}, {"iso", "12h"}, {"iso", "24h"}, {"picker", "24h"}};
        StringBuilder attempts = new StringBuilder();
        String toast = "";
        boolean ok = false;
        String usedDate = downDate, usedTime = downTime;
        for (int i = 0; i < combos.length && !ok; i++) {
            if (i > 0) {
                boolean d2 = combos[i][0].equals("iso") ? cr.setDownDateModel(isoDate) : cr.enterDownDate(downDate);
                boolean t2 = d2 && cr.enterDownTime(combos[i][1].equals("12h") ? downTime : time24);
                if (!t2) { attempts.append(" | could not set combination ").append(i + 1).append(": ").append(cr.problems); break; }
                usedDate = combos[i][0].equals("iso") ? isoDate : downDate;
                usedTime = combos[i][1].equals("12h") ? downTime : time24;
            }
            toast = cr.saveAndGetToast();
            ok = cr.ok() && cr.saveSucceeded();
            attempts.append(attempts.length() == 0 ? "" : " | ").append("attempt ").append(i + 1).append(": date ").append(usedDate).append(", time ").append(usedTime).append(" -> ")
                    .append(ok ? "saved (\"" + toast + "\")" : cr.refusedDateConversion() ? "refused by the server with a date conversion error (HTTP 500, \"" + toast + "\")" : "not saved (\"" + toast + "\")");
            if (!ok && !cr.refusedDateConversion()) break;                         // some other refusal: more date formats will not help
        }
        step("Click Save", "Click Save", "The resolution is saved",
                cr.problems.isEmpty() ? "\"" + cr.lastSaveButton + "\" clicked" + (cr.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + cr.lastDialog + "\"") + "; " + attempts : "Problem — " + cr.problems,
                cr.problems.isEmpty() ? "PASS" : "FAIL");

        // after a save the screen returns to the list: the complaint should now show the resolved status
        String after = "";
        boolean back = false;
        if (ok) {
            back = cr.returnToList();
            List<String[]> again = back ? cr.clickComplaint(no) : List.of();
            after = again.isEmpty() ? "" : again.get(0)[4];
        }
        boolean statusShown = !after.isEmpty() && after.equalsIgnoreCase(status);
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (cr.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + cr.lastSaveMessage + "\"")
                        + (back ? "; back on the list" : "; the list was not reached")
                        + "; " + no + " now shows Resolved Status \"" + (after.isEmpty() ? "(none)" : after) + "\"" + (statusShown ? " (as selected)" : " (not the one selected)")
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + cr.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + cr.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Complaint resolved", no + " (" + d[0] + ": " + d[5] + ")");
        addSummary("Resolve No.", cr.lastResolveNo);
        addSummary("Completed By / Verified By / Reported By / Employee", completed + " / " + verified + " / " + by + " / " + emp);
        addSummary("Resolved Status", status);
        addSummary("Amount Incurred", amount);
        addSummary("Down from (as saved)", usedDate + " " + usedTime);
        addSummary("Save attempts", attempts.toString());
        addSummary("Mode", wasUnresolved ? "new resolution of an unresolved complaint (" + cr.lastSaveButton + ")" : "an already resolved complaint was reopened (" + cr.lastSaveButton + ")");
        if (ok && attempts.indexOf("attempt 2") >= 0 && attempts.indexOf("refused by the server") >= 0)
            addSummary("Application defect seen", "The Down Date picked in the calendar is sent as dd/MM/yyyy and the server refuses the save with HTTP 500 (\"conversion of a nvarchar data type to a smalldatetime data type resulted in an out-of-range value\"); "
                    + "the same screen saves when the Down Date is sent as yyyy-MM-dd, the format its placeholder asks for but its picker discards when typed.");
        addSummary("Resolved Status shown afterwards", after.isEmpty() ? "(not read)" : after);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
