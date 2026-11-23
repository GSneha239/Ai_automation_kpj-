package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FallRiskChecklist — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Fall Risk Checklist Master</b>
 * ({@code #/FallRiskIntervention}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Fall Risk Checklist Master</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Description*</b> (the checklist item).</li>
 *   <li>Click <b>Submit</b> ({@code fnSubmit}); wait for the success toast (screenshotted while visible). If the
 *       toast says the code / description already exists, change the details and Submit again.</li>
 * </ol>
 */
public class FallRiskChecklist extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public FallRiskChecklist() { super("ApplicationConfig_Patient_FallRiskChecklist"); }

    public static void main(String[] args) {
        FallRiskChecklist t = new FallRiskChecklist();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Fall Risk Checklist Master",
                "Application Configuration > Patient > Fall Risk Checklist Master",
                "Add a fall-risk checklist item (inline-add): enter Code + Description, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.FallRiskChecklist fr =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.FallRiskChecklist(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = fr.navigateViaMenu();
        String landed = fr.currentScreen();
        step(page, "Open Fall Risk Checklist Master screen",
                "Application Configuration -> Patient -> Fall Risk Checklist Master",
                "The Fall Risk Checklist Master screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Fall Risk Checklist Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = fr.fillDetails(attempt);
            used++;
            toast = fr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("FallRiskChecklist: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=FC") && !details.contains("Description=(no)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code and Description", "Enter Code* (unique) and Description* (the checklist item)",
                "Code and Description are entered", detActual, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (fr.toastPng != null && fr.toastPng.length > 0) {
            step(fr.toastPng, "Click Submit & success toast", "Click Submit (fnSubmit); wait for the success toast",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnSubmit); wait for the success toast",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Checklist Code", fr.lastCode);
        addSummary("Checklist Item", fr.lastQuestion);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/FallRiskIntervention (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
