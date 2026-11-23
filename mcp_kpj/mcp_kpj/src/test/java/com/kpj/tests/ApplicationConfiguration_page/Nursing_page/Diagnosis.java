package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Diagnosis — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Diagnosis</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Diagnosis</b> (clicking <b>Add</b> first
 *       if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Save again.</li>
 * </ol>
 */
public class Diagnosis extends DevHisBase {

    /**
     * How many times to re-enter fresh details when the server says the code / remark already exists.
     */
    private static final int MAX_ATTEMPTS = 40;

    public Diagnosis() { super("ApplicationConfig_Nursing_Diagnosis"); }

    public static void main(String[] args) {
        Diagnosis t = new Diagnosis();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Diagnosis",
                "Application Configuration > Nursing > Diagnosis",
                "Click Add, enter Code + Remark, click Save; wait for the success toast. On "
                        + "'already exists', change the details and Save again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Diagnosis dg =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Diagnosis(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = dg.navigateViaMenu();
        String landed = dg.currentScreen();
        step(page, "Open Diagnosis screen", "Application Configuration -> Nursing -> Diagnosis",
                "The Diagnosis screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Diagnosis but the app opened: " + landed + "\n" + dg.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = dg.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Save — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = dg.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Save, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Diagnosis: filled-form screenshot failed - " + e.getMessage()); }
            toast = dg.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Diagnosis: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=DG") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + dg.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + dg.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (dg.toastPng != null && dg.toastPng.length > 0) {
            step(dg.toastPng, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Diagnosis Code", dg.lastCode);
        addSummary("Remark", dg.lastRemark);
        addSummary("Field models", dg.lastCodeModel + " / " + dg.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
