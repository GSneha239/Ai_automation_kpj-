package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PostcoordinationValueSetMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Postcoordination Value Set Master</b>
 * ({@code #/PostcoordinationValueSetMasterList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Postcoordination Value Set Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter any 2 numbers in <b>ICD-11 Code*</b> and select a code from the autocomplete list.</li>
 *   <li>Select the <b>Axis Type*</b> and one of the <b>Allowed extension codes*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says it already exists, pick a different
 *       ICD-11 code / extension code and Submit again.</li>
 * </ol>
 */
public class PostcoordinationValueSetMaster extends DevHisBase {

    public PostcoordinationValueSetMaster() { super("ApplicationConfig_Nursing_PostcoordinationValueSetMaster"); }

    public static void main(String[] args) {
        PostcoordinationValueSetMaster t = new PostcoordinationValueSetMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Postcoordination Value Set Master",
                "Application Configuration > Nursing > Postcoordination Value Set Master",
                "Add a Postcoordination Value Set: Add, ICD-11 Code (2 digits -> pick from the list), Axis Type, "
                        + "one Allowed extension code, Submit; wait for the success toast. On 'already exists', "
                        + "pick a different ICD-11 code / extension code and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PostcoordinationValueSetMaster pv =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PostcoordinationValueSetMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = pv.navigateViaMenu();
        String landed = pv.currentScreen();
        step(page, "Open Postcoordination Value Set Master screen",
                "Application Configuration -> Nursing -> Postcoordination Value Set Master",
                "The Postcoordination Value Set Master list screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Postcoordination Value Set Master but the app opened: " + landed + "\n" + pv.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add
        String addHow = pv.clickAddIfPresent();
        boolean addOk = !addHow.startsWith("no Add button");
        step(page, "Click Add", "Click Add", "The Postcoordination Value Set add form is shown",
                addHow, addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - add form not opened"); return; }

        // 3) ICD-11 Code, Axis Type, Allowed extension code, Submit — on "already exists" use another code / extension.
        String icd = "", axis = "", ext = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < 60; attempt++) {
            // Any 2 numbers: 11, 12, 13 ... — a different pair (and extension code) each retry.
            String digits = String.valueOf(11 + (attempt % 80));
            icd = pv.enterIcd11Code(digits, 0);
            axis = pv.selectAxisType();
            ext = pv.selectAllowedExtensionCode(attempt);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("PostcoordinationValueSetMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = pv.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("PostcoordinationValueSetMaster: attempt " + used + " already exists — trying another code");
        }

        boolean icdOk = icd.contains("ICD-11 Code=") && !icd.contains("autocomplete failed") && !icd.matches(".*ICD-11 Code= \\|.*");
        String icdActual = icd + (used > 1 ? "  [changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter ICD-11 Code", "Enter any 2 numbers in ICD-11 Code* and select a code from the list",
                    "An ICD-11 code is selected from the list (code + short description filled)", icdActual, icdOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter ICD-11 Code", "Enter any 2 numbers in ICD-11 Code* and select a code from the list",
                    "An ICD-11 code is selected from the list (code + short description filled)", icdActual, icdOk ? "PASS" : "FAIL");
        }

        boolean axisOk = axis.startsWith("Axis Type=") && !axis.contains("(no ") && !axis.contains("(select failed)");
        step(page, "Select Axis Type", "Select the Axis Type*", "An Axis Type is selected", axis, axisOk ? "PASS" : "FAIL");

        boolean extOk = ext.contains("checked :") && !ext.contains("NOT checked");
        step(page, "Select Allowed extension code", "Select any 1 of the Allowed extension codes*",
                "One allowed extension code is selected", ext, extOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + pv.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s): \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + pv.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (pv.toastPng != null && pv.toastPng.length > 0) {
            step(pv.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' pick a different ICD-11 code / extension code and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' pick a different ICD-11 code / extension code and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("ICD-11 Code", icd);
        addSummary("Axis Type", axis);
        addSummary("Allowed extension code", ext);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
