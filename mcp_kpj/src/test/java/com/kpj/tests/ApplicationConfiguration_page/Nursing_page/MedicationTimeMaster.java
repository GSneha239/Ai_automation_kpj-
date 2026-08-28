package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MedicationTimeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Medication Time Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Medication Time Master</b> (clicking
 *       <b>Add</b> first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again. Any OTHER (non-success) toast is a hard FAIL — it is never
 *       treated as a pass.</li>
 * </ol>
 */
public class MedicationTimeMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public MedicationTimeMaster() { super("ApplicationConfig_Nursing_MedicationTimeMaster"); }

    public static void main(String[] args) {
        MedicationTimeMaster t = new MedicationTimeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Medication Time Master",
                "Application Configuration > Nursing > Medication Time Master",
                "Click Add, enter Code + Remark, click Submit; wait for the success toast. On 'already exists', "
                        + "change the details and Submit again. Any other toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MedicationTimeMaster mt =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MedicationTimeMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = mt.navigateViaMenu();
        String landed = mt.currentScreen();
        step(page, "Open Medication Time Master screen", "Application Configuration -> Nursing -> Medication Time Master",
                "The Medication Time Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Medication Time Master but the app opened: " + landed + "\n" + mt.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = mt.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists"; any OTHER
        // (non-success) toast stops the loop and FAILs the step naming the exact message returned.
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = mt.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("MedicationTimeMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = mt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message — stop either way
            System.out.println("MedicationTimeMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=MT") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        // Wrong/unexpected toast text is always reported as a FAIL, never MANUAL — the user must see exactly
        // what the app actually returned when it is not a genuine success message.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + mt.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + mt.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (mt.toastPng != null && mt.toastPng.length > 0) {
            step(mt.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Medication Time Code", mt.lastCode);
        addSummary("Remark", mt.lastRemark);
        addSummary("Field models", mt.lastCodeModel + " / " + mt.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
