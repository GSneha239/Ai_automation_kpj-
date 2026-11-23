package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BiohazardCategoryMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Biohazard Category Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Biohazard Category Master</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddDBiohazardCategory}) → {@code #/add-BiohazardCategoryMaster}.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Select <b>Applicability</b> (radio).</li>
 *   <li>Enter <b>Diagnosis Code</b> + <b>Diagnosis Description</b> and click the inner <b>Add</b>
 *       ({@code AddDiagnosis}).</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDBiohazardCategoryMaster}); wait for the success toast.</li>
 * </ol>
 */
public class BiohazardCategoryMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BiohazardCategoryMaster() { super("ApplicationConfig_Nursing_BiohazardCategoryMaster"); }

    public static void main(String[] args) {
        BiohazardCategoryMaster t = new BiohazardCategoryMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Biohazard Category Master",
                "Application Configuration > Nursing > Biohazard Category Master",
                "Add a Biohazard Category: Add, enter Code + Remark, select Applicability, enter a Diagnosis and Add it, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.BiohazardCategoryMaster bh =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.BiohazardCategoryMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = bh.navigateViaMenu();
        String landed = bh.currentScreen();
        step(page, "Open Biohazard Category Master screen",
                "Application Configuration -> Nursing -> Biohazard Category Master",
                "The Biohazard Category Master list screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Biohazard Category Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean added = bh.clickAdd();
        step(page, "Click Add", "Click Add (AddDBiohazardCategory) -> #/add-BiohazardCategoryMaster",
                "The Biohazard Category add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        // Enter details, select Applicability, add the Diagnosis row and Submit — retry with different
        // Code/Remark on "already exists". Applicability is re-asserted and the Diagnosis row only added
        // once (attempt 0) since it already sits in the grid for later attempts.
        String codeRemark = "", appl = "", diag = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            codeRemark = bh.fillCodeAndRemark(attempt);
            appl = bh.selectApplicability();
            if (attempt == 0) diag = bh.enterDiagnosisAndAdd();
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("BiohazardCategoryMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = bh.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BiohazardCategoryMaster: attempt " + used + " (" + codeRemark + ") already exists — changing the details");
        }

        boolean crOk = codeRemark.contains("Code=BH") && !codeRemark.contains("Remark=(no)");
        String crActual = codeRemark + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", crActual, crOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", crActual, crOk ? "PASS" : "FAIL");
        }

        boolean applOk = appl.contains("selected=true");
        step(page, "Select Applicability", "Select the Applicability radio (BiohazardCategoryMaster.applicability)",
                "An Applicability option is selected", appl, applOk ? "PASS" : "FAIL");

        boolean diagOk = diag.contains("->") && !diag.endsWith("-> 0") && !diag.contains("=(no)");
        step(page, "Enter Diagnosis & click Add",
                "Enter Diagnosis Code + Diagnosis Description, then click Add (AddDiagnosis)",
                "The diagnosis row is added to the grid", diag, diagOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (bh.toastPng != null && bh.toastPng.length > 0) {
            step(bh.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUDBiohazardCategoryMaster); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUDBiohazardCategoryMaster); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Biohazard Code", bh.lastCode);
        addSummary("Remark", bh.lastRemark);
        addSummary("Applicability", bh.lastApplicability);
        addSummary("Diagnosis", bh.lastDiagnosis);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/BiohazardCategoryMasterList -> #/add-BiohazardCategoryMaster");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
