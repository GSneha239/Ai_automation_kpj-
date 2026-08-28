package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named EatingAbility — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Eating Ability</b>
 * ({@code #/EatingAbility}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Eating Ability</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b> (located by label — the ng-model prefix varies per screen).</li>
 *   <li>Click <b>Submit</b>; wait for the success toast (screenshotted while visible). If the toast says the
 *       code/remark already exists, change the details and Submit again.</li>
 * </ol>
 */
public class EatingAbility extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public EatingAbility() { super("ApplicationConfig_Nursing_EatingAbility"); }

    public static void main(String[] args) {
        EatingAbility t = new EatingAbility();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Eating Ability",
                "Application Configuration > Nursing > Eating Ability",
                "Add a Eating Ability: enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.EatingAbility dc =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.EatingAbility(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = dc.navigateViaMenu();
        String landed = dc.currentScreen();
        step(page, "Open Eating Ability screen",
                "Application Configuration -> Nursing -> Eating Ability",
                "The Eating Ability screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Eating Ability but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Some of these screens are inline-add, others open a form via Add — handle both.
        String addInfo = dc.clickAddIfPresent();
        System.out.println("EatingAbility: " + addInfo);

        // Enter Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = dc.fillDetails(attempt);
            used++;
            toast = dc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("EatingAbility: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=EA") && !details.contains("Remark=(no)");
        String detActual = details + " | " + addInfo + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");

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

        addSummary("Eating Ability Code", dc.lastCode);
        addSummary("Remark", dc.lastRemark);
        addSummary("Route", "#/EatingAbility");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
