package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VisitTypeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Visit Type Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Visit Type</b> ({@code #/visitType}).</li>
 *   <li>Click <b>Add</b> ({@code AddVisitType}) → {@code #/add-visitType}.</li>
 *   <li>Enter <b>Code*</b>, <b>Visit Type*</b> and select <b>Location*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDVisitType}); wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class VisitTypeMaster extends DevHisBase {

    public VisitTypeMaster() { super("ApplicationConfig_Patient_VisitTypeMaster"); }

    public static void main(String[] args) {
        VisitTypeMaster t = new VisitTypeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Visit Type Master",
                "Application Configuration > Patient > Visit Type Master",
                "Add a Visit Type: Add, enter Code + Visit Type, select Location, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.VisitTypeMaster vt =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.VisitTypeMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = vt.navigateViaMenu();
        String landed = vt.currentScreen();
        step(page, "Open Visit Type Master screen", "Application Configuration -> Patient -> Visit Type",
                "The Visit Type Master list screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Visit Type Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean added = vt.clickAdd();
        step(page, "Click Add", "Click Add (AddVisitType) -> #/add-visitType", "The Visit Type add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String details = vt.fillDetails();
        boolean detOk = details.contains("Code=VT") && !details.contains("VisitType=(no)")
                && !details.contains("Location=(no)");
        step(page, "Enter Code, Visit Type, Location",
                "Enter Code* and Visit Type*, then select Location* (select2 multi-select)",
                "Code, Visit Type and Location are set", details, detOk ? "PASS" : "FAIL");

        String toast = vt.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (vt.toastPng != null && vt.toastPng.length > 0) {
            step(vt.toastPng, "Click Submit & success toast", "Click Submit (fnIUDVisitType); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDVisitType); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Visit Type Code", vt.lastCode);
        addSummary("Visit Type", vt.lastVisitType);
        addSummary("Location", vt.lastLocation);
        addSummary("Route", "#/visitType -> #/add-visitType");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
