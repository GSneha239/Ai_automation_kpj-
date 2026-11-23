package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PayableRoster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Payable Roster</b>.
 *
 * <ol>
 *   <li>Select <b>Location</b> and <b>Department</b> (if the results table is empty after Search for the
 *       current Department, try the next Department until data loads).</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>Tick the checkbox of a row in the results table.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. Any OTHER (non-success) toast is a hard FAIL.</li>
 * </ol>
 */
public class PayableRoster extends DevHisBase {

    /** How many Departments to try (in order) looking for one whose Search actually returns rows. */
    private static final int MAX_DEPARTMENTS_TO_TRY = 20;

    public PayableRoster() { super("ApplicationConfig_Location_PayableRoster"); }

    public static void main(String[] args) {
        PayableRoster t = new PayableRoster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Payable Roster",
                "Application Configuration > Location > Payable Roster",
                "Select Location + Department, click Search (try other Departments if no data loads), tick a "
                        + "row's checkbox, click Submit; wait for the success toast. Any other toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableRoster pr =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableRoster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = pr.navigateViaMenu();
        String landed = pr.currentScreen();
        step(page, "Open Payable Roster screen", "Application Configuration -> Location -> Payable Roster",
                "The Payable Roster screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Payable Roster but the app opened: " + landed + "\n" + pr.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Select Location, then walk Departments (0, 1, 2, ...) until Search returns rows.
        String location = pr.selectLocation(0);
        int rows = 0;
        String department = "";
        int deptCount = pr.departmentOptionCount();
        int tries = Math.min(Math.max(deptCount, 1), MAX_DEPARTMENTS_TO_TRY);
        StringBuilder deptsTried = new StringBuilder();
        for (int i = 0; i < tries; i++) {
            department = pr.selectDepartment(i);
            if (department.isEmpty()) break;   // ran out of real options
            pr.clickSearch();
            rows = pr.resultRowCount();
            deptsTried.append(department).append("(").append(rows).append(" row(s)) ");
            if (rows > 0) break;
        }
        String selDetail = "Location=" + (location.isEmpty() ? "(not set)" : location)
                + " | Department tried: " + (deptsTried.length() == 0 ? "(none)" : deptsTried.toString().trim());
        boolean selOk = !location.isEmpty() && !department.isEmpty();
        step(page, "Select Location, Department & Search", "Select Location + Department, click Search (try other Departments if no data loads)",
                "The results table has data", selDetail + " -> " + rows + " row(s) in the results table",
                selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED - could not select Location/Department: " + pr.describeForm()); return; }
        if (rows == 0) {
            addSummary("Result", "No Department produced data in the results table (tried: " + deptsTried + ")");
            return;
        }

        // 3) Tick a row's checkbox.
        boolean ticked = pr.selectFirstRow();
        step(page, "Click the checkbox for the data in table", "Tick the checkbox of a row in the results table",
                "A row is selected", ticked ? "Row selected" : "No row/checkbox found to select",
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("Result", "FAILED - could not select a grid row"); return; }

        // 4) + 5) Submit — any OTHER (non-success) toast is a hard FAIL, never MANUAL.
        String toast = pr.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + pr.describeForm()
                : (ok ? toast : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + pr.lastSaveHttp);
        if (pr.toastPng != null && pr.toastPng.length > 0) {
            step(pr.toastPng, "Click Submit & success toast", "Click Submit",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Location", location);
        addSummary("Department", department);
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
