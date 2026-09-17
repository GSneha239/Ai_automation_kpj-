package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SubDepartment — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Sub Department</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Sub Department</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> and <b>Sub Department</b>, select <b>Department</b>, <b>Default HOD</b>,
 *       <b>Time Slot(Min)</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class SubDepartment extends DevHisBase {

    public SubDepartment() { super("ApplicationConfig_Location_SubDepartment"); }

    public static void main(String[] args) {
        SubDepartment t = new SubDepartment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Sub Department",
                "Application Configuration > Location > Sub Department",
                "Add a sub department: Code, Sub Department, Department, Default HOD, Time Slot(Min), Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.SubDepartment sd =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.SubDepartment(page);

        boolean onScreen = sd.navigateViaMenu();
        step(page, "Open Sub Department screen",
                "Click Application Configuration -> Location -> Sub Department",
                "The Sub Department screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + sd.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("sub department links => " + sd.findSubDepartmentLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + sd.describeForm());

        boolean added = sd.clickAdd() && sd.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + sd.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + sd.describeForm());

        // Code and Sub Department
        String codeAndName = sd.fillCodeAndName(0);
        boolean codeOk = !sd.lastCode.isEmpty() && !sd.lastSubDepartment.isEmpty();
        step(page, "Enter Code and Sub Department", "Type the Code and the Sub Department",
                "Both are entered", codeAndName, codeOk ? "PASS" : "FAIL");

        // Department, Default HOD, Time Slot(Min)
        String dept = sd.selectDepartment(0);
        String hod = sd.selectDefaultHod(0);
        boolean selOk = !dept.isEmpty() && !hod.isEmpty() && !sd.lastTimeSlot.isEmpty();
        step(page, "Select Department, Default HOD, Time Slot(Min)", "Choose Department and Default HOD",
                "All three are set", "Department=" + dept + " | Default HOD=" + hod + " | Time Slot=" + sd.lastTimeSlot,
                selOk ? "PASS" : "FAIL");

        // Submit — retry with different Code/Sub Department if this one already exists.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            toast = sd.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(sd.lastCode).append('/').append(sd.lastSubDepartment).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            if (!sd.clickBack()) break;
            if (!(sd.clickAdd() && sd.addFormOpen())) break;
            String refilled = sd.fillAll(attempt + 1);
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
        }
        step(page, "Click Submit", "Click Submit", "The sub department is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (sd.lastSaveApi.isEmpty() ? "" : "  [" + sd.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(sd.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Sub Department Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Code", sd.lastCode);
        addSummary("Sub Department", sd.lastSubDepartment);
        addSummary("Department", sd.lastDepartment);
        addSummary("Default HOD", sd.lastHod);
        addSummary("Time Slot(Min)", sd.lastTimeSlot);
        addSummary("Save API", sd.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
