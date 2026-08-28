package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ResultEntryComment — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Result Entry Comment</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Result Entry Comment</b> (clicking
 *       <b>Add</b> first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class ResultEntryComment extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ResultEntryComment() { super("ApplicationConfig_Investigation_ResultEntryComment"); }

    public static void main(String[] args) {
        ResultEntryComment t = new ResultEntryComment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Result Entry Comment",
                "Application Configuration > Investigation > Result Entry Comment",
                "Add a Result Entry Comment: enter Code + Remark, Submit; wait for the success toast. On "
                        + "'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.ResultEntryComment rec =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.ResultEntryComment(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = rec.navigateViaMenu();
        String landed = rec.currentScreen();
        step(page, "Open Result Entry Comment screen", "Application Configuration -> Investigation -> Result Entry Comment",
                "The Result Entry Comment screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Result Entry Comment but the app opened: " + landed + "\n" + rec.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = rec.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = rec.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("ResultEntryComment: filled-form screenshot failed - " + e.getMessage()); }
            toast = rec.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("ResultEntryComment: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=RE") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + rec.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + rec.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (rec.toastPng != null && rec.toastPng.length > 0) {
            step(rec.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Result Entry Comment Code", rec.lastCode);
        addSummary("Remark", rec.lastRemark);
        addSummary("Field models", rec.lastCodeModel + " / " + rec.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
