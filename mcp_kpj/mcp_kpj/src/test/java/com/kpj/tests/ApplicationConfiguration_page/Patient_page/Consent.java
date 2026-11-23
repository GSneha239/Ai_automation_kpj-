package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Consent — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Consent</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Consent</b> ({@code #/Consent}).</li>
 *   <li>Click <b>Add</b> ({@code AddConsent}) → {@code #/addConsent}.</li>
 *   <li>Enter <b>Code*</b>, <b>Template Name*</b> and <b>Consent Type*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDConsent}); wait for the success toast. If the toast says the code /
 *       template name already exists, change the details and Submit again.</li>
 * </ol>
 */
public class Consent extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / template name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Consent() { super("ApplicationConfig_Patient_Consent"); }

    public static void main(String[] args) {
        Consent t = new Consent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Consent", "Application Configuration > Patient > Consent",
                "Add a Consent: Add, enter Code + Template Name + Consent Type, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.Consent cn =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.Consent(page);

        boolean on = cn.navigateViaMenu();
        step(page, "Open Consent screen", "Application Configuration -> Patient -> Consent",
                "The Consent list screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        boolean added = cn.clickAdd();
        step(page, "Click Add", "Click Add (AddConsent) -> #/addConsent", "The Consent add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = cn.fillDetails(attempt);
            used++;
            toast = cn.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Consent: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=CN") && !details.contains("TemplateName=(no")
                && !details.contains("ConsentType=(no");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Template Name, Consent Type",
                "Enter Code*, Template Name* and select Consent Type*",
                "Code, Template Name and Consent Type are set", detActual, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (fnIUDConsent); wait for the success toast",
                "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Consent Code", cn.lastCode);
        addSummary("Template Name", cn.lastTemplateName);
        addSummary("Consent Type", cn.lastConsentType);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/Consent -> #/addConsent");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
