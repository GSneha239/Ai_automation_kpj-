package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named IntakeOutputMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Intake Output Master</b> ({@code #/IntakeOutput}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Intake Output Master</b>.</li>
 *   <li>Click <b>Add</b> ({@code addIntakeOutput}).</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Intake Output Unit</b> and select <b>Intake</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class IntakeOutputMaster extends DevHisBase {

    public IntakeOutputMaster() { super("ApplicationConfig_Nursing_IntakeOutputMaster"); }

    public static void main(String[] args) {
        IntakeOutputMaster t = new IntakeOutputMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Intake Output Master",
                "Application Configuration > Nursing > Intake Output Master",
                "Add an Intake Output: click Add, enter Code + Remark + Intake Output Unit, select Intake, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.IntakeOutputMaster io =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.IntakeOutputMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = io.navigateViaMenu();
        String landed = io.currentScreen();
        step(page, "Open Intake Output Master screen",
                "Application Configuration -> Nursing -> Intake Output Master",
                "The Intake Output Master list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Intake Output Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = io.clickAdd() || io.onAddForm();
        step(page, "Click Add", "Click Add (addIntakeOutput) to open the entry form",
                "The Intake Output entry form is shown",
                addOk ? "Add form opened - " + io.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + io.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + io.currentScreen() + ")"); return; }

        String details = io.fillDetails();
        boolean detOk = details.contains("Code=IO") && !details.contains("Remark=(no)")
                && !details.contains("Unit=(no)") && !details.contains("Intake=(not found)");
        step(page, "Enter Code, Remark, Intake Output Unit and select Intake",
                "Enter Code (unique), Remark and Intake Output Unit, then select Intake",
                "Code, Remark and Intake Output Unit are entered and Intake is selected",
                details, detOk ? "PASS" : "FAIL");

        String toast = io.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes. This screen has been seen answering a
        // save with "Message Not Found." (a server-side message-key lookup failure) — that is a defect in the
        // message the app returns, so it FAILS even though the row does reach the grid.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        // Not part of the verdict — just says whether the row landed, so a wrong-message failure is diagnosable.
        String persisted = ok ? "" : (io.recordExists(io.lastCode)
                ? " (the record " + io.lastCode + " DID reach the grid - the message is wrong, not the save)"
                : " (and the record " + io.lastCode + " is not in the grid either)");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared" + persisted
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"" + persisted);
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (io.toastPng != null && io.toastPng.length > 0) {
            step(io.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", io.lastCode);
        addSummary("Remark", io.lastRemark);
        addSummary("Intake Output Unit", io.lastUnit);
        addSummary("Intake", io.lastIntake);
        addSummary("Route", "#/IntakeOutput");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\"") + persisted);
    }
}
