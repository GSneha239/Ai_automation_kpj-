package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Patient Type</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Patient Type</b> (clicking <b>Add</b>
 *       first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>, and select <b>Concession Template</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class PatientType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PatientType() { super("ApplicationConfig_Patient_PatientType"); }

    public static void main(String[] args) {
        PatientType t = new PatientType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Patient Type",
                "Application Configuration > Patient > Patient Type",
                "Add a Patient Type: enter Code + Remark, select Concession Template, Submit; wait for the "
                        + "success toast. On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.PatientType pt =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.PatientType(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = pt.navigateViaMenu();
        String landed = pt.currentScreen();
        step(page, "Open Patient Type screen", "Application Configuration -> Patient -> Patient Type",
                "The Patient Type screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Patient Type but the app opened: " + landed + "\n" + pt.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = pt.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Concession Template, Submit — retry with different details on "already exists".
        String details = "", concession = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null, concessionPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = pt.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("PatientType: filled-form screenshot failed - " + e.getMessage()); }
            concession = pt.selectConcessionTemplate();
            try { concessionPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("PatientType: concession screenshot failed - " + e.getMessage()); }
            toast = pt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("PatientType: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=PT") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        boolean concOk = !concession.startsWith("(");
        String concActual = "Concession Template=" + concession + (concOk ? "" : "\n" + pt.describeForm());
        if (concessionPng != null && concessionPng.length > 0) {
            step(concessionPng, "Select Concession Template", "Select the Concession Template dropdown",
                    "A Concession Template is selected", concActual, concOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Concession Template", "Select the Concession Template dropdown",
                    "A Concession Template is selected", concActual, concOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + pt.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + pt.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (pt.toastPng != null && pt.toastPng.length > 0) {
            step(pt.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Patient Type Code", pt.lastCode);
        addSummary("Remark", pt.lastRemark);
        addSummary("Concession Template", concession);
        addSummary("Field models", pt.lastCodeModel + " / " + pt.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
