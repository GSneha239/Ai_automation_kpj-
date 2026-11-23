package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AllocationLocationMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Allocation Location Master</b>
 * ({@code #/AllocationLocationMaster}) — INLINE-ADD.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>Allocation Location Master</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message — any other message fails the step.</li>
 * </ol>
 */
public class AllocationLocationMaster extends DevHisBase {

    public AllocationLocationMaster() { super("ApplicationConfig_MRD_AllocationLocationMaster"); }

    public static void main(String[] args) {
        AllocationLocationMaster t = new AllocationLocationMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Allocation Location Master",
                "Application Configuration > MRD > Allocation Location Master",
                "Add an Allocation Location (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.AllocationLocationMaster al =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.AllocationLocationMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = al.navigateViaMenu();
        String landed = al.currentScreen();
        String why = al.showsCodeStoreLeftover()
                ? " - the app served the leftover generic commonmaster form (Code* + Store*) with NO Remark field"
                : "";
        step(page, "Open Allocation Location Master screen",
                "Application Configuration -> MRD -> Allocation Location Master",
                "The Allocation Location Master screen (Code + Remark) is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Allocation Location Master but the app opened: " + landed + why
                     + " || " + al.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - the page is wrong: " + landed + why); return; }

        String details = al.fillDetails();
        boolean detOk = details.contains("Code=AL") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + (detOk ? "" : " || form offered: " + al.describeForm()), detOk ? "PASS" : "FAIL");

        String toast = al.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (al.toastPng != null && al.toastPng.length > 0) {
            step(al.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", al.lastCode);
        addSummary("Remark", al.lastRemark);
        addSummary("Route", "#/AllocationLocationMaster (inline-add)");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
