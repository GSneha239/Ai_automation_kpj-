package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BloodDiscardReason — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Discard Reason</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Blood Discard Reason</b> (clicking
 *       <b>Add</b> first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class BloodDiscardReason extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BloodDiscardReason() { super("ApplicationConfig_BloodBank_BloodDiscardReason"); }

    public static void main(String[] args) {
        BloodDiscardReason t = new BloodDiscardReason();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Blood Discard Reason",
                "Application Configuration > Blood Bank > Blood Discard Reason",
                "Add a Blood Discard Reason: enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodDiscardReason bdr =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodDiscardReason(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = bdr.navigateViaMenu();
        String landed = bdr.currentScreen();
        step(page, "Open Blood Discard Reason screen", "Application Configuration -> Blood Bank -> Blood Discard Reason",
                "The Blood Discard Reason screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Blood Discard Reason but the app opened: " + landed + "\n" + bdr.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = bdr.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = bdr.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("BloodDiscardReason: filled-form screenshot failed - " + e.getMessage()); }
            toast = bdr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("BloodDiscardReason: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=BD") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + bdr.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + bdr.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (bdr.toastPng != null && bdr.toastPng.length > 0) {
            step(bdr.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Blood Discard Reason Code", bdr.lastCode);
        addSummary("Remark", bdr.lastRemark);
        addSummary("Field models", bdr.lastCodeModel + " / " + bdr.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
