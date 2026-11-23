package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DiscardReasonMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Discard Reason Master</b> ({@code #/DiscardReasonMaster}) —
 * INLINE-ADD.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>Discard Reason Master</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message — any other message fails the step.</li>
 * </ol>
 */
public class DiscardReasonMaster extends DevHisBase {

    public DiscardReasonMaster() { super("ApplicationConfig_MRD_DiscardReasonMaster"); }

    public static void main(String[] args) {
        DiscardReasonMaster t = new DiscardReasonMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Discard Reason Master",
                "Application Configuration > MRD > Discard Reason Master",
                "Add a Discard Reason (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.DiscardReasonMaster dr =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.DiscardReasonMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = dr.navigateViaMenu();
        String landed = dr.currentScreen();
        String why = dr.showsCodeStoreLeftover()
                ? " - the app served the leftover generic commonmaster form (Code* + Store*) with NO Remark field"
                : "";
        step(page, "Open Discard Reason Master screen",
                "Application Configuration -> MRD -> Discard Reason Master",
                "The Discard Reason Master screen (Code + Remark) is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Discard Reason Master but the app opened: " + landed + why
                     + " || " + dr.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - the page is wrong: " + landed + why); return; }

        String details = dr.fillDetails();
        boolean detOk = details.contains("Code=DR") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + (detOk ? "" : " || form offered: " + dr.describeForm()), detOk ? "PASS" : "FAIL");

        String toast = dr.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (dr.toastPng != null && dr.toastPng.length > 0) {
            step(dr.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", dr.lastCode);
        addSummary("Remark", dr.lastRemark);
        addSummary("Route", "#/DiscardReasonMaster (inline-add)");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
