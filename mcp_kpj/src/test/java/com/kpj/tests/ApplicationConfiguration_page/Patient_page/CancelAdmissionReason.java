package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CancelAdmissionReason — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Cancel Admission Reason</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Cancel Admission Reason</b>.</li>
 *   <li>Enter Code, Remark.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class CancelAdmissionReason extends DevHisBase {

    public CancelAdmissionReason() { super("ApplicationConfig_Patient_CancelAdmissionReason"); }

    public static void main(String[] args) {
        CancelAdmissionReason t = new CancelAdmissionReason();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Cancel Admission Reason", "Application Configuration > Patient > Cancel Admission Reason",
                "Add a Cancel Admission Reason: enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelAdmissionReason car =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.CancelAdmissionReason(page);

        boolean onScreen = car.navigateViaMenu();
        System.out.println("CANCELADM url => " + car.route + " | " + page.url());
        step(page, "Open Cancel Admission Reason screen", "Application Configuration -> Patient -> Cancel Admission Reason",
                "The screen is shown", car.onScreen() ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                car.onScreen() ? "PASS" : "FAIL");
        if (!car.onScreen()) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Some of these masters are inline-add; others need Add. Click Add if a Code field isn't already present.
        boolean codePresent = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('input')].some(e=>e.offsetParent!==null && /\\.Code$/i.test(e.getAttribute('ng-model')||''))"));
        if (!codePresent) car.clickAddIfPresent();

        String fill = car.fillDetails();
        System.out.println("CANCELADM fill => " + fill);
        boolean fillOk = fill.contains("Code=CAR");
        step(page, "Enter Code, Remark", "Enter Code and Remark",
                "Code and Remark are entered", fill, fillOk ? "PASS" : "FAIL");

        String toast = car.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Cancel Admission Reason Code", car.lastCode);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
