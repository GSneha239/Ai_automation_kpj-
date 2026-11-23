package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named TitleMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Title Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Title</b> ({@code #/title}).</li>
 *   <li>Click <b>Add</b> ({@code AddTitle}) → {@code #/add-title}.</li>
 *   <li>Select <b>Gender</b>, enter <b>Code*</b> and <b>Title*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDTitle}); wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class TitleMaster extends DevHisBase {

    public TitleMaster() { super("ApplicationConfig_Patient_TitleMaster"); }

    public static void main(String[] args) {
        TitleMaster t = new TitleMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Title Master",
                "Application Configuration > Patient > Title Master",
                "Add a Title: Add, select Gender, enter Code + Title, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.TitleMaster tm =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.TitleMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = tm.navigateViaMenu();
        String landed = tm.currentScreen();
        step(page, "Open Title Master screen", "Application Configuration -> Patient -> Title",
                "The Title Master list screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Title Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean added = tm.clickAdd();
        step(page, "Click Add", "Click Add (AddTitle) -> #/add-title", "The Title add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String details = tm.fillDetails();
        boolean detOk = details.contains("Code=TI") && !details.contains("Gender=(no") && !details.contains("Title=(no)");
        step(page, "Enter Gender, Code, Title", "Select Gender and enter Code* and Title*",
                "Gender, Code and Title are set", details, detOk ? "PASS" : "FAIL");

        String toast = tm.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Submit not confirmed - server returned: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (tm.toastPng != null && tm.toastPng.length > 0) {
            step(tm.toastPng, "Click Submit & success toast", "Click Submit (fnIUDTitle); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDTitle); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Title Code", tm.lastCode);
        addSummary("Title", tm.lastTitle);
        addSummary("Gender", tm.lastGender);
        addSummary("Route", "#/title -> #/add-title");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
