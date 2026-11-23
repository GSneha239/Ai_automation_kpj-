package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BodySymptoms — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Body Symptoms</b> ({@code #/BodySymptoms}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Body Symptoms</b>.</li>
 *   <li>Select <b>Catogory*</b>, enter <b>Code*</b> and <b>Symptom*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class BodySymptoms extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / symptom already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BodySymptoms() { super("ApplicationConfig_Nursing_BodySymptoms"); }

    public static void main(String[] args) {
        BodySymptoms t = new BodySymptoms();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Body Symptoms",
                "Application Configuration > Nursing > Body Symptoms",
                "Add a Body Symptom (inline-add): select Catogory, enter Code + Symptom, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.BodySymptoms bs =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.BodySymptoms(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = bs.navigateViaMenu();
        String landed = bs.currentScreen();
        step(page, "Open Body Symptoms screen", "Application Configuration -> Nursing -> Body Symptoms",
                "The Body Symptoms screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Body Symptoms but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Select Catogory, enter Code and Symptom, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = bs.fillDetails(attempt);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("BodySymptoms: filled-form screenshot failed - " + e.getMessage()); }
            toast = bs.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BodySymptoms: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=BS") && !details.contains("Category=(no") && !details.contains("Symptom=(no)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Select Catogory, enter Code and Symptom",
                    "Select Catogory* (Symptoms.categoryid), enter Code* and Symptom*",
                    "Catogory, Code and Symptom are set", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Catogory, enter Code and Symptom",
                    "Select Catogory* (Symptoms.categoryid), enter Code* and Symptom*",
                    "Catogory, Code and Symptom are set", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (bs.toastPng != null && bs.toastPng.length > 0) {
            step(bs.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUDState); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUDState); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Symptom Code", bs.lastCode);
        addSummary("Symptom", bs.lastSymptom);
        addSummary("Catogory", bs.lastCategory);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/BodySymptoms (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
