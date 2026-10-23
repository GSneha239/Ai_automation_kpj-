package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Icd11ExtensionCodeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Icd11ExtensionCodeMaster</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Icd11ExtensionCodeMaster</b> (clicking <b>Add</b>
 *       first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Icd11ExtensionCodeMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Icd11ExtensionCodeMaster() { super("ApplicationConfig_Nursing_ICD11ExtensionCodeMaster"); }

    public static void main(String[] args) {
        Icd11ExtensionCodeMaster t = new Icd11ExtensionCodeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - ICD11 Extension Code Master",
                "Application Configuration > Nursing > ICD11 Extension Code Master",
                "Add a Icd11ExtensionCodeMaster: enter Code + Remark, Submit; wait for the success toast. On "
                        + "'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Icd11ExtensionCodeMaster py =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Icd11ExtensionCodeMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = py.navigateViaMenu();
        String landed = py.currentScreen();
        step(page, "Open ICD11 Extension Code Master screen", "Application Configuration -> Nursing -> ICD11 Extension Code Master",
                "The Icd11ExtensionCodeMaster screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Icd11ExtensionCodeMaster but the app opened: " + landed + "\n" + py.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = py.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = py.fillDetails(attempt) + " | " + py.selectAxisType();
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Icd11ExtensionCodeMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = py.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Icd11ExtensionCodeMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=IX") && !details.contains("(no field)") && details.contains("Axis Type=") && !details.contains("(no dropdown)") && !details.contains("(no options)") && !details.contains("(select failed)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Extension Code, Description and Axis Type", "Enter Extension Code* (unique), Description* and select Axis Type*",
                    "Extension Code, Description and Axis Type are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Extension Code, Description and Axis Type", "Enter Extension Code* (unique), Description* and select Axis Type*",
                    "Extension Code, Description and Axis Type are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + py.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + py.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (py.toastPng != null && py.toastPng.length > 0) {
            step(py.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Icd11ExtensionCodeMaster Code", py.lastCode);
        addSummary("Remark", py.lastRemark);
        addSummary("Field models", py.lastCodeModel + " / " + py.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
