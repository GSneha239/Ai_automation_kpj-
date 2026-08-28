package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ScreeningTestMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Screening Test Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Screening Test Master</b> (clicking
 *       <b>Add</b> first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b> and <b>Comment</b>.</li>
 *   <li>Select <b>Default Result</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class ScreeningTestMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ScreeningTestMaster() { super("ApplicationConfig_BloodBank_ScreeningTestMaster"); }

    public static void main(String[] args) {
        ScreeningTestMaster t = new ScreeningTestMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Screening Test Master",
                "Application Configuration > Blood Bank > Screening Test Master",
                "Add a Screening Test: enter Code + Remark + Comment, select Default Result, Submit; wait for "
                        + "the success toast. On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.ScreeningTestMaster stm =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.ScreeningTestMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = stm.navigateViaMenu();
        String landed = stm.currentScreen();
        step(page, "Open Screening Test Master screen", "Application Configuration -> Blood Bank -> Screening Test Master",
                "The Screening Test Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Screening Test Master but the app opened: " + landed + "\n" + stm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = stm.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark / Comment form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code / Remark / Comment, Default Result, Submit — retry with different details on "already exists".
        String details = "", result = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null, resultPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = stm.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("ScreeningTestMaster: filled-form screenshot failed - " + e.getMessage()); }
            result = stm.selectDefaultResult();
            try { resultPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("ScreeningTestMaster: result screenshot failed - " + e.getMessage()); }
            toast = stm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("ScreeningTestMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=ST") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code, Remark and Comment", "Enter Code* (unique), Remark* and Comment",
                    "Code, Remark and Comment are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code, Remark and Comment", "Enter Code* (unique), Remark* and Comment",
                    "Code, Remark and Comment are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        boolean resultOk = !result.startsWith("(not found)") && !result.startsWith("(no-options)");
        String resultActual = "Default Result=" + result + (resultOk ? "" : "\n" + stm.describeForm());
        if (resultPng != null && resultPng.length > 0) {
            step(resultPng, "Select Default Result", "Select the Default Result dropdown",
                    "A Default Result is selected", resultActual, resultOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Default Result", "Select the Default Result dropdown",
                    "A Default Result is selected", resultActual, resultOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + stm.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + stm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (stm.toastPng != null && stm.toastPng.length > 0) {
            step(stm.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Screening Test Code", stm.lastCode);
        addSummary("Remark", stm.lastRemark);
        addSummary("Comment", stm.lastComment);
        addSummary("Default Result", result);
        addSummary("Field models", stm.lastCodeModel + " / " + stm.lastRemarkModel + " / " + stm.lastCommentModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
