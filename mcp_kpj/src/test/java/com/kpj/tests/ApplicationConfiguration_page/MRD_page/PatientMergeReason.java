package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientMergeReason — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Patient Merge Reason</b> ({@code #/PatientMergeReason}) —
 * INLINE-ADD commonmaster screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>PatientMergeReason</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b> (the mandatory Form Name* select arrives pre-selected).</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCommonMaster}); the toast must be a SUCCESS message — any other message
 *       fails the step.</li>
 * </ol>
 */
public class PatientMergeReason extends DevHisBase {

    public PatientMergeReason() { super("ApplicationConfig_MRD_PatientMergeReason"); }

    public static void main(String[] args) {
        PatientMergeReason t = new PatientMergeReason();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Patient Merge Reason",
                "Application Configuration > MRD > Patient Merge Reason",
                "Add a Patient Merge Reason (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.PatientMergeReason pm =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.PatientMergeReason(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = pm.navigateViaMenu();
        String landed = pm.currentScreen();
        step(page, "Open Patient Merge Reason screen",
                "Application Configuration -> MRD -> PatientMergeReason",
                "The Patient Merge Reason screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Patient Merge Reason but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - the page is wrong: " + landed); return; }

        String formName = pm.ensureFormName();
        System.out.println("PatientMergeReason: Form Name -> " + formName);

        String details = pm.fillDetails();
        boolean detOk = details.contains("Code=PM") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", details + " | Form Name: " + formName, detOk ? "PASS" : "FAIL");

        String toast = pm.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"\nHTTP: " + pm.lastSaveHttp);
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (pm.toastPng != null && pm.toastPng.length > 0) {
            step(pm.toastPng, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", pm.lastCode);
        addSummary("Remark", pm.lastRemark);
        addSummary("Form Name", formName);
        addSummary("Route", "#/PatientMergeReason (inline-add)");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
