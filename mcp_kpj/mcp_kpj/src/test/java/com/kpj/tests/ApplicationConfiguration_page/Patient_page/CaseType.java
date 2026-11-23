package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CaseType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>CaseType</b> ({@code #/CaseType}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>CaseType</b> (no Add button — the form is
 *       on the screen).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCommonMaster}); wait for the success toast. If the toast says the code /
 *       remark already exists, change the details and Submit again.</li>
 * </ol>
 */
public class CaseType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public CaseType() { super("ApplicationConfig_Patient_CaseType"); }

    public static void main(String[] args) {
        CaseType t = new CaseType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - CaseType", "Application Configuration > Patient > CaseType",
                "Add a Case Type (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.CaseType oc =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.CaseType(page);

        // If the app serves a different screen, FAIL and say so plainly — do not carry on against the wrong page
        // (its leftover markup can still look fillable and would produce a misleading later failure).
        boolean on = oc.navigateViaMenu();
        String landed = oc.currentScreen();
        step(page, "Open CaseType screen", "Application Configuration -> Patient -> CaseType",
                "The CaseType screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected CaseType but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) {
            addSummary("Result", "FAILED - wrong page opened: " + landed);
            return;
        }

        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = oc.fillDetails(attempt);
            used++;
            toast = oc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("CaseType: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=CT") && !details.contains("Code=(no)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark",
                "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");

        addSummary("CaseType Code", oc.lastCode);
        addSummary("Remark", oc.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/CaseType (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
