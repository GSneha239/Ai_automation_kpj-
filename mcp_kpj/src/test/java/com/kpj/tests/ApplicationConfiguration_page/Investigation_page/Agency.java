package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Agency — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Agency</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Agency</b> (clicking <b>Add</b> first
 *       if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Agency extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Agency() { super("ApplicationConfig_Investigation_Agency"); }

    public static void main(String[] args) {
        Agency t = new Agency();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Agency",
                "Application Configuration > Investigation > Agency",
                "Add an Agency: enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Agency ag =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Agency(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = ag.navigateViaMenu();
        String landed = ag.currentScreen();
        step(page, "Open Agency screen", "Application Configuration -> Investigation -> Agency",
                "The Agency screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Agency but the app opened: " + landed + "\n" + ag.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = ag.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ag.fillDetails(attempt);
            used++;
            toast = ag.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Agency: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=AG") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + ag.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + ag.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (ag.toastPng != null && ag.toastPng.length > 0) {
            step(ag.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Agency Code", ag.lastCode);
        addSummary("Remark", ag.lastRemark);
        addSummary("Field models", ag.lastCodeModel + " / " + ag.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
