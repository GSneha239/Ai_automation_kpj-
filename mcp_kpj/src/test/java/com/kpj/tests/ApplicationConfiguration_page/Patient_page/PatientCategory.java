package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientCategory — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Patient Category</b> ({@code #/CaseCategory}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Patient Category</b> (no Add button — the form
 *       is on the screen).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCaseCategory}); wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class PatientCategory extends DevHisBase {

    public PatientCategory() { super("ApplicationConfig_Patient_PatientCategory"); }

    public static void main(String[] args) {
        PatientCategory t = new PatientCategory();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); }
    }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Patient Category",
                "Application Configuration > Patient > Patient Category",
                "Add a Patient Category (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.PatientCategory pc =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.PatientCategory(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = pc.navigateViaMenu();
        String landed = pc.currentScreen();
        step(page, "Open Patient Category screen", "Application Configuration -> Patient -> Patient Category",
                "The Patient Category screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Patient Category but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String details = pc.fillDetails();
        boolean detOk = details.contains("Code=PC") && !details.contains("Remark=(no)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", details, detOk ? "PASS" : "FAIL");

        String toast = pc.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (pc.toastPng != null && pc.toastPng.length > 0) {
            step(pc.toastPng, "Click Submit & success toast", "Click Submit (fnIUDCaseCategory); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDCaseCategory); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Patient Category Code", pc.lastCode);
        addSummary("Remark", pc.lastRemark);
        addSummary("Route", "#/CaseCategory (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
