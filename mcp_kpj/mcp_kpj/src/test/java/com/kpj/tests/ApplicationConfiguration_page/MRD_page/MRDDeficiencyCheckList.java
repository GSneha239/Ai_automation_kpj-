package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MRDDeficiencyCheckList — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>MRD Deficiency CheckList</b> ({@code #/MRDdeficiencyCheckList}) —
 * INLINE-ADD commonmaster screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>MRD Deficiency CheckList</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b> (the mandatory Form Name* select arrives pre-selected).</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCommonMaster}); the toast must be a SUCCESS message — any other message
 *       fails the step.</li>
 * </ol>
 */
public class MRDDeficiencyCheckList extends DevHisBase {

    public MRDDeficiencyCheckList() { super("ApplicationConfig_MRD_MRDDeficiencyCheckList"); }

    public static void main(String[] args) {
        MRDDeficiencyCheckList t = new MRDDeficiencyCheckList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - MRD Deficiency CheckList",
                "Application Configuration > MRD > MRD Deficiency CheckList",
                "Add an MRD Deficiency CheckList entry (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.MRDDeficiencyCheckList dc =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.MRDDeficiencyCheckList(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = dc.navigateViaMenu();
        String landed = dc.currentScreen();
        step(page, "Open MRD Deficiency CheckList screen",
                "Application Configuration -> MRD -> MRD Deficiency CheckList",
                "The MRD Deficiency CheckList screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected MRD Deficiency CheckList but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - the page is wrong: " + landed); return; }

        String formName = dc.ensureFormName();
        System.out.println("MRDDeficiencyCheckList: Form Name -> " + formName);

        String details = dc.fillDetails();
        boolean detOk = details.contains("Code=DC") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", details + " | Form Name: " + formName, detOk ? "PASS" : "FAIL");

        String toast = dc.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (dc.toastPng != null && dc.toastPng.length > 0) {
            step(dc.toastPng, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", dc.lastCode);
        addSummary("Remark", dc.lastRemark);
        addSummary("Form Name", formName);
        addSummary("Route", "#/MRDdeficiencyCheckList (inline-add)");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
