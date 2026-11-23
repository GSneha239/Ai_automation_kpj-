package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SeverityMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Severity</b> ({@code #/Severity}) — INLINE-ADD screen.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Severity</b> (no Add button — the form
 *       is on the screen).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDCommonMaster}); wait for the success toast. Any non-success toast
 *       is a hard FAIL — it is never treated as a pass.</li>
 * </ol>
 */
public class SeverityMaster extends DevHisBase {

    public SeverityMaster() { super("ApplicationConfig_Nursing_Severity"); }

    public static void main(String[] args) {
        SeverityMaster t = new SeverityMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Severity",
                "Application Configuration > Nursing > Severity",
                "Add a Severity level (inline-add): enter Code + Remark, Submit; wait for the success toast. "
                        + "Any other toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.SeverityMaster sv =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.SeverityMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = sv.navigateViaMenu();
        String landed = sv.currentScreen();
        step(page, "Open Severity screen", "Application Configuration -> Nursing -> Severity",
                "The Severity screen (inline-add form) is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Severity but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String details = sv.fillDetails();
        boolean detOk = details.contains("Code=SV") && !details.contains("Remark=(no)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", details, detOk ? "PASS" : "FAIL");

        String toast = sv.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        // Wrong/unexpected toast text is always reported as a FAIL, never MANUAL — the user must see exactly
        // what the app actually returned when it is not a genuine success message.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + sv.lastSaveHttp);
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (sv.toastPng != null && sv.toastPng.length > 0) {
            step(sv.toastPng, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDCommonMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Severity Code", sv.lastCode);
        addSummary("Remark", sv.lastRemark);
        addSummary("Route", "#/Severity (inline-add)");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
