package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AppetiteChange — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Appetite Change</b> ({@code #/AppetiteChange}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Appetite Change</b> (no Add button — the form
 *       is on the screen).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCommonMaster}); wait for the success toast. If the toast says the Code or
 *       Description already exists, change BOTH details and Submit again.</li>
 * </ol>
 */
public class AppetiteChange extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public AppetiteChange() { super("ApplicationConfig_Nursing_AppetiteChange"); }

    public static void main(String[] args) {
        AppetiteChange t = new AppetiteChange();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Appetite Change",
                "Application Configuration > Nursing > Appetite Change",
                "Add an Appetite Change (inline-add): enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists', change the Code and Remark and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.AppetiteChange ac =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.AppetiteChange(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = ac.navigateViaMenu();
        String landed = ac.currentScreen();
        step(page, "Open Appetite Change screen", "Application Configuration -> Nursing -> Appetite Change",
                "The Appetite Change screen (inline-add form) is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Appetite Change but the app opened: " + landed
                             + " | fields on screen: " + ac.visibleModels(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Enter Code + Remark, Submit, and on "already exists" enter DIFFERENT details and Submit again.
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ac.fillDetails(attempt);
            used++;
            toast = ac.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("AppetiteChange: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=AC") && !details.contains("Remark=(no)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (ac.toastPng != null && ac.toastPng.length > 0) {
            step(ac.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUDCommonMaster); on 'already exists' change the Code/Remark and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUDCommonMaster); on 'already exists' change the Code/Remark and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Appetite Change Code", ac.lastCode);
        addSummary("Remark", ac.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/AppetiteChange (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
