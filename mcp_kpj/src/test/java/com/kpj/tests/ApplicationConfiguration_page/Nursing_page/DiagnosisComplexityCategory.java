package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DiagnosisComplexityCategory — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Diagnosis Complexity Category</b>
 * ({@code #/DiagnosisComplexityCategory}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Diagnosis Complexity Category</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b> (located by label — the ng-model prefix varies per screen).</li>
 *   <li>Click <b>Submit</b>; wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class DiagnosisComplexityCategory extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DiagnosisComplexityCategory() { super("ApplicationConfig_Nursing_DiagnosisComplexityCategory"); }

    public static void main(String[] args) {
        DiagnosisComplexityCategory t = new DiagnosisComplexityCategory();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Diagnosis Complexity Category",
                "Application Configuration > Nursing > Diagnosis Complexity Category",
                "Add a Diagnosis Complexity Category: enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DiagnosisComplexityCategory dc =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DiagnosisComplexityCategory(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = dc.navigateViaMenu();
        String landed = dc.currentScreen();
        step(page, "Open Diagnosis Complexity Category screen",
                "Application Configuration -> Nursing -> Diagnosis Complexity Category",
                "The Diagnosis Complexity Category screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Diagnosis Complexity Category but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Some of these screens are inline-add, others open a form via Add — handle both.
        String addInfo = dc.clickAddIfPresent();
        System.out.println("DiagnosisComplexityCategory: " + addInfo);

        // Enter Code and Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = dc.fillDetails(attempt);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("DiagnosisComplexityCategory: filled-form screenshot failed - " + e.getMessage()); }
            toast = dc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("DiagnosisComplexityCategory: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=DC") && !details.contains("Remark=(no)");
        String detActual = details + " | " + addInfo + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (dc.toastPng != null && dc.toastPng.length > 0) {
            step(dc.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Category Code", dc.lastCode);
        addSummary("Remark", dc.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/DiagnosisComplexityCategory");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
