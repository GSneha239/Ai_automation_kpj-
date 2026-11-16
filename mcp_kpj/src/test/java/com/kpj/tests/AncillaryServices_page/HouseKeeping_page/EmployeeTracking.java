package com.kpj.tests.AncillaryServices_page.HouseKeeping_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named EmployeeTracking — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; House Keeping &gt; <b>Employee Tracking</b> ({@code #/EmployeeTracking}).
 *
 * <ol>
 *   <li>Select an <b>Employee Name</b>. The screen fetches that employee's schedule at once; most employees have none, so the employees are tried in a LOOP - the dropdown in its order, then a read-only scan
 *       for those that have rows - until the selected one shows rows in <b>Activities Assigned</b> that can be edited.</li>
 *   <li>Verify the tasks appear in Activities Assigned.</li>
 *   <li>In one of the rows (the first editable one) select a <b>Status</b> and enter a <b>Remark</b>.</li>
 *   <li>Click <b>OK</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES a real status and remark on one of the employee's scheduled activities on every run (a status that does not complete the activity is chosen, "In Progress" where offered, so the row
 * stays editable for the next run).</p>
 */
public class EmployeeTracking extends DevHisBase {

    public EmployeeTracking() { super("AncillaryServices_HouseKeeping_EmployeeTracking"); }

    public static void main(String[] args) {
        EmployeeTracking t = new EmployeeTracking();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - House Keeping - Employee Tracking", "Ancillary Services > House Keeping > Employee Tracking",
                "Select an employee (looping through employees until one has editable rows); verify the tasks appear in Activities Assigned; select a status and enter a remark on one row; click OK; verify the success toast. SAVES A REAL STATUS AND REMARK.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.HouseKeeping_page.EmployeeTracking et = new com.kpj.pages.AncillaryServices_page.HouseKeeping_page.EmployeeTracking(page);

        boolean on = et.navigateViaMenu();
        step("Open Employee Tracking", "Click Ancillary Services -> House Keeping -> Employee Tracking", "The Employee Tracking screen is shown",
                on ? "Opened " + page.url() + " (" + et.employeeOptions().size() + " employees in the dropdown; dates " + et.dateBoxes() + ")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Employee Tracking not reached"); return; }

        // 1) select an employee - loop until the selected one has rows that can be edited
        boolean found = et.selectEmployeeWithEditableRows(5, 90000);
        String empName = et.lastEmployee, empId = et.lastEmployeeId;
        int rowCount = et.rowCount();
        int editable = found ? et.editableRows() : 0;
        step("Select employee name", "Select an Employee Name; if its Activities Assigned has no editable rows, select a different one (loop)", "An employee whose Activities Assigned has editable rows is selected",
                found ? "Selected \"" + empName + "\" after " + et.tried.size() + " attempt(s): " + et.tried
                      : "No employee with editable rows was found - " + et.tried + (et.problems.isEmpty() ? "" : " - " + et.problems),
                found ? "PASS" : "FAIL");

        // 2) the tasks must appear
        List<String[]> rows = found ? et.rows() : List.of();
        step("Verify tasks or data appear in Activities Assigned", "Read the Activities Assigned table of the selected employee", "Rows (tasks) are listed and can be edited",
                found ? "PASSES because Activities Assigned lists " + rowCount + " row(s) for \"" + empName + "\", " + editable + " of them editable (Status, Remark and Completed controls enabled); first rows: "
                        + rows.stream().limit(3).map(r -> r[0] + " [" + r[1] + "], status \"" + (r[2].isEmpty() ? "-Select-" : r[2]) + "\"").toList()
                      : "FAILS because none of the employees tried had rows that can be edited: " + et.tried,
                found ? "PASS" : "FAIL");
        if (!found) { addSummary("Employees tried", String.valueOf(et.tried)); addSummary("Result", "FAILED — no employee with editable rows"); return; }

        int idx = et.firstEditableRow();
        String status = et.selectStatus(idx, List.of("In Progress", "Pending"));
        step("Select status", "In row " + (idx + 1) + " (" + et.lastRowActivity + ") select a Status", "A status is selected in the row",
                !status.isEmpty() ? "Status \"" + status + "\" selected in row " + (idx + 1) + " - \"" + et.lastRowActivity + "\" of template \"" + et.lastRowTemplate + "\" (of " + et.statusOptions(idx) + "); the row's data holds it"
                                  : "Problem — " + et.problems,
                !status.isEmpty() ? "PASS" : "FAIL");
        if (status.isEmpty()) { addSummary("Result", "FAILED — status: " + et.problems); return; }

        String remark = "QA auto remark - Employee Tracking " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean remarkOk = et.enterRemark(idx, remark);
        step("Enter remark", "Enter a unique Remark in the same row (" + remark + ")", "The remark is accepted in the row",
                remarkOk ? "Remark \"" + remark + "\" entered in row " + (idx + 1) + "; the row's data holds it" : "Problem — " + et.problems, remarkOk ? "PASS" : "FAIL");
        if (!remarkOk) { addSummary("Result", "FAILED — remark: " + et.problems); return; }

        String rowActivity = et.lastRowActivity, rowTemplate = et.lastRowTemplate, statusId = et.lastStatusId, dates = et.dateBoxes();
        String toast = et.clickOkAndGetToast();
        boolean clicked = et.problems.isEmpty();
        step("Click OK", "Click OK", "The changes are saved",
                clicked ? "OK clicked" + (et.lastDialog.isEmpty() ? "" : " (confirmation answered: " + et.lastDialog + ")") + "; request " + (et.lastSaveRequestInfo.isEmpty() ? "(not seen)" : et.lastSaveRequestInfo) + "; dates sent " + dates
                        : "Problem — " + et.problems,
                clicked ? "PASS" : "FAIL");

        boolean ok = clicked && et.saveSucceeded();
        boolean savedByServer = et.savedByServer();
        // read back what the server holds (not a step of the spec; reported in the summary)
        String[] saved = (ok || savedByServer) ? et.savedRow(empId, idx, rowActivity, rowTemplate, remark) : null;
        boolean held = saved != null && remark.equals(saved[1]) && statusId.equals(saved[0]) && "true".equals(saved[4]);
        String readBack = saved == null ? "(could not be read back)"
                : held ? "yes - read back through the screen's own fetch, row " + (idx + 1) + " (" + rowActivity + ", " + rowTemplate + ") holds status \"" + status + "\" (value " + saved[0] + ") and the remark; " + saved[3] + " row(s) of the list carry this remark"
                : "NO - read back through the screen's own fetch, row " + (idx + 1) + " (same activity and template: " + saved[4] + ") answers status value \"" + saved[0] + "\" (expected \"" + statusId + "\"), remark \"" + saved[1]
                    + "\" (expected \"" + remark + "\"); " + saved[3] + " row(s) of the list carry this remark";

        step("Verify success toast message", "Read the toast shown after OK", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (et.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + et.lastSaveMessage.trim() + "\"")
                        + (saved == null || held ? "" : "; NOTE: the saved status and remark are NOT held when read back - " + readBack)
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the change (ResultStatus 1)"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + et.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + et.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        boolean cleared = ok && et.employeeCleared(8000);
        addSummary("Employee", empName + " (id " + empId + ")");
        addSummary("Employees tried", String.valueOf(et.tried));
        addSummary("Rows / editable", rowCount + " / " + editable);
        addSummary("Row changed", "row " + (idx + 1) + " - " + rowActivity + " (template " + rowTemplate + ")");
        addSummary("Status / Remark", status + " / " + remark);
        addSummary("Save request", et.lastSaveRequestInfo.isEmpty() ? "(not seen)" : et.lastSaveRequestInfo);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + et.lastSaveMessage.trim() + "\")" : "(no save confirmed" + (et.lastSaveMessage.isEmpty() ? "" : ": " + et.lastSaveMessage) + ")");
        addSummary("Form cleared afterwards", ok ? (cleared ? "yes - the employee is cleared and the table is empty (the screen's onclear())" : "no - the employee / table are still shown") : "n/a");
        addSummary("Stored by the server", readBack);
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
