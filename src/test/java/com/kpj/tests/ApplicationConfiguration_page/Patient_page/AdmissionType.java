package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AdmissionType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Admission Type</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Admission Type</b>; click <b>Add</b>.</li>
 *   <li>Enter Code + Admission Type; select Location = KPJ.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDAdmissionType}); wait for the success toast. If the toast says the code /
 *       description already exists, change the details and Submit again.</li>
 * </ol>
 */
public class AdmissionType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public AdmissionType() { super("ApplicationConfig_Patient_AdmissionType"); }

    public static void main(String[] args) {
        AdmissionType t = new AdmissionType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Admission Type", "Application Configuration > Patient > Admission Type",
                "Add an Admission Type: enter Code + Admission Type, select Location KPJ, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.AdmissionType at =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.AdmissionType(page);

        at.navigateViaMenu();
        step(page, "Open Admission Type screen", "Application Configuration -> Patient -> Admission Type",
                "The Admission Type screen is shown", at.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                at.onScreen() ? "PASS" : "FAIL");
        if (!at.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = at.clickAdd();
        step(page, "Click Add", "Click Add (AddAdmissionType) -> #/add-admissionType",
                "The add form is shown", added ? "Add form opened (" + page.url() + ")" : "Add form did not open",
                added ? "PASS" : "FAIL");

        String fill = "", toast = "";
        boolean fillOk = false, ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = at.fillDetails(attempt);
            used++;
            fillOk = fill.contains("Code=AT") && !fill.contains("Location=(no");
            // Screenshot the FILLED form here — before Submit, which returns to the list and shows a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("AdmissionType: filled-form screenshot failed - " + e.getMessage()); }
            toast = at.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("AdmissionType: attempt " + used + " (" + fill + ") already exists — changing the details");
        }
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Admission Type & select Location KPJ", "Enter Code + Admission Type; select Location = KPJ",
                    "The Admission Type + Location are set", fillActual, fillOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Admission Type & select Location KPJ", "Enter Code + Admission Type; select Location = KPJ",
                    "The Admission Type + Location are set", fillActual, fillOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed — server returned: \"" + toast + "\""));
        // Use the screenshot taken while the toast was on screen (toastr fades before a later screenshot lands).
        if (at.toastPng != null && at.toastPng.length > 0) {
            step(at.toastPng, "Click Submit & success toast", "Click Submit (fnIUDAdmissionType); wait for the success toast",
                    "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDAdmissionType); wait for the success toast",
                    "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Admission Type Code", at.lastCode);
        addSummary("Admission Type", at.lastDescription);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
