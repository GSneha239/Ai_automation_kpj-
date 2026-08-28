package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FallRiskAssessmentType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Fall Risk Assessment Type</b>
 * ({@code #/FRATypeMaster} — the menu label is the abbreviation <b>FRAType</b>).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>FRAType</b> (Fall Risk Assessment Type).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b> and <b>Interpretation*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class FallRiskAssessmentType extends DevHisBase {

    public FallRiskAssessmentType() { super("ApplicationConfig_Nursing_FallRiskAssessmentType"); }

    public static void main(String[] args) {
        FallRiskAssessmentType t = new FallRiskAssessmentType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Fall Risk Assessment Type",
                "Application Configuration > Nursing > Fall Risk Assessment Type",
                "Add a Fall Risk Assessment Type: enter Code + Remark + Interpretation, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.FallRiskAssessmentType fat =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.FallRiskAssessmentType(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = fat.navigateViaMenu();
        String landed = fat.currentScreen();
        step(page, "Open Fall Risk Assessment Type screen",
                "Application Configuration -> Nursing -> FRAType (Fall Risk Assessment Type)",
                "The Fall Risk Assessment Type list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Fall Risk Assessment Type but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = fat.clickAdd() || fat.onAddForm();
        step(page, "Click Add", "Click Add (AddFRAType) to open the entry form",
                "The Fall Risk Assessment Type entry form is shown",
                addOk ? "Add form opened - " + fat.currentScreen() : "Add form did NOT open - " + fat.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add form did not open"); return; }

        String details = fat.fillDetails();
        boolean detOk = details.contains("Code=FA") && !details.contains("Remark=(no)")
                && details.contains("Interpretation=CKEDITOR") && !details.contains("model=\"\"");
        step(page, "Enter Code, Remark and Interpretation",
                "Enter Code* (unique), Remark* and Interpretation* (a CKEditor rich-text field)",
                "Code, Remark and Interpretation are entered", details, detOk ? "PASS" : "FAIL");

        String toast = fat.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (fat.toastPng != null && fat.toastPng.length > 0) {
            step(fat.toastPng, "Click Submit & success toast", "Click Submit (fnIUD); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUD); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", fat.lastCode);
        addSummary("Remark", fat.lastRemark);
        addSummary("Interpretation", fat.lastInterpretation);
        addSummary("Route", "#/FRATypeMaster (menu label: FRAType)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
