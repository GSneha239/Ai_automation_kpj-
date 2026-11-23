package com.kpj.tests.SystemConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.SystemConfiguration_page.UserRolePage;

/**
 * System Configuration &gt; <b>User Role</b> ({@code #/UserRole}).
 *
 * <ol>
 *   <li>Open <b>System Configuration</b> → <b>User Role</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Role*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message — any other message fails the step.</li>
 * </ol>
 */
public class UserRole extends DevHisBase {

    public UserRole() { super("SystemConfig_UserRole"); }

    public static void main(String[] args) {
        UserRole t = new UserRole();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("System Configuration - User Role", "System Configuration > User Role",
                "Add a user role: click Add, enter Role + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        UserRolePage ur = new UserRolePage(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = ur.navigateViaMenu();
        String landed = ur.currentScreen();
        step(page, "Open User Role screen", "System Configuration -> User Role",
                "The User Role list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected User Role but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = ur.clickAdd() || ur.onAddForm();
        step(page, "Click Add", "Click Add to open the user role entry form",
                "The User Role entry form is shown",
                addOk ? "Add form opened - " + ur.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + ur.currentScreen()
                        + " || " + ur.describeForm(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form"); return; }

        String details = ur.fillDetails();
        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Role and Remark", "Enter Role* (unique) and Remark*",
                "Role and Remark are entered",
                details + (detOk ? "" : " || form offered: " + ur.describeForm()), detOk ? "PASS" : "FAIL");

        // Tick ONE right in the menu tree. "Select All" only toggles itself (1 checkbox), granting nothing.
        String rights = ur.tickOneRight();
        step(page, "Select a checkbox", "Tick one right checkbox in the role's menu tree",
                "One right checkbox is ticked", rights,
                rights.contains("(ticked") ? "PASS" : "FAIL");

        String toast = ur.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        if (ur.toastPng != null && ur.toastPng.length > 0) {
            step(ur.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Role", ur.lastRole);
        addSummary("Remark", ur.lastRemark);
        addSummary("Route", "#/UserRole");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
