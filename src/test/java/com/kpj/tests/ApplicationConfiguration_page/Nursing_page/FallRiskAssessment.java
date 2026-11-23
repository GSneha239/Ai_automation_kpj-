package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FallRiskAssessment — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Fall Risk Assessment</b> ({@code #/FallRiskAssessment}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Fall Risk Assessment</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddFallRiskAssessment} → {@code #/add-FallRiskAssessment}).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b> and select <b>FRAType*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUD}); wait for the success toast. If the toast says the code/remark
 *       already exists, change the details and Submit again.</li>
 * </ol>
 */
public class FallRiskAssessment extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public FallRiskAssessment() { super("ApplicationConfig_Nursing_FallRiskAssessment"); }

    public static void main(String[] args) {
        FallRiskAssessment t = new FallRiskAssessment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Fall Risk Assessment",
                "Application Configuration > Nursing > Fall Risk Assessment",
                "Add a Fall Risk Assessment: click Add, enter Code + Remark + FRAType, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.FallRiskAssessment fra =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.FallRiskAssessment(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = fra.navigateViaMenu();
        String landed = fra.currentScreen();
        step(page, "Open Fall Risk Assessment screen",
                "Application Configuration -> Nursing -> Fall Risk Assessment",
                "The Fall Risk Assessment list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Fall Risk Assessment but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = fra.clickAdd() || fra.onAddForm();
        step(page, "Click Add", "Click Add (AddFallRiskAssessment) to open the entry form",
                "The Fall Risk Assessment entry form is shown",
                addOk ? "Add form opened - " + fra.currentScreen() : "Add form did NOT open - " + fra.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add form did not open"); return; }

        // Enter Code / Remark / FRAType, Submit — retry with different Code/Remark on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = fra.fillDetails(attempt);
            used++;
            toast = fra.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("FallRiskAssessment: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=FR") && !details.contains("Remark=(no)")
                && !details.contains("FRAType=(no-opt)") && !details.contains("model=?");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark and FRAType",
                "Enter Code* (unique), Remark* and select FRAType* (the Fall Risk Assessment Type master)",
                "Code, Remark and FRAType are entered", detActual, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (fra.toastPng != null && fra.toastPng.length > 0) {
            step(fra.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUD); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUD); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", fra.lastCode);
        addSummary("Remark", fra.lastRemark);
        addSummary("FRAType", fra.lastFraType);
        addSummary("Route", "#/FallRiskAssessment -> #/add-FallRiskAssessment");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
