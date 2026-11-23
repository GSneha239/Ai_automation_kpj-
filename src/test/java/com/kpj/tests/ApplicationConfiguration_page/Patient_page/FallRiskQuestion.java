package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FallRiskQuestion — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Questions for Fall Risk Master</b>
 * ({@code #/FallRiskChecklistQuestion}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Questions for Fall Risk Master</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Description*</b> (the fall-risk question).</li>
 *   <li>Click <b>Submit</b> ({@code fnSubmit}); wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class FallRiskQuestion extends DevHisBase {

    public FallRiskQuestion() { super("ApplicationConfig_Patient_FallRiskQuestion"); }

    public static void main(String[] args) {
        FallRiskQuestion t = new FallRiskQuestion();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Questions for Fall Risk Master",
                "Application Configuration > Patient > Questions for Fall Risk Master",
                "Add a fall-risk question (inline-add): enter Code + Description, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.FallRiskQuestion fr =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.FallRiskQuestion(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = fr.navigateViaMenu();
        String landed = fr.currentScreen();
        step(page, "Open Questions for Fall Risk Master screen",
                "Application Configuration -> Patient -> Questions for Fall Risk Master",
                "The Questions for Fall Risk Master screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Questions for Fall Risk Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String details = fr.fillDetails();
        boolean detOk = details.contains("Code=FQ") && !details.contains("Description=(no)");
        step(page, "Enter Code and Description", "Enter Code* (unique) and Description* (the fall-risk question)",
                "Code and Description are entered", details, detOk ? "PASS" : "FAIL");

        String toast = fr.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (fr.toastPng != null && fr.toastPng.length > 0) {
            step(fr.toastPng, "Click Submit & success toast", "Click Submit (fnSubmit); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnSubmit); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Question Code", fr.lastCode);
        addSummary("Question", fr.lastQuestion);
        addSummary("Route", "#/FallRiskChecklistQuestion (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
