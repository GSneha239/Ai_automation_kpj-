package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Designation — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Designation</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Designation</b>.</li>
 *   <li>Enter <b>Code</b> and <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class Designation extends DevHisBase {

    public Designation() { super("ApplicationConfig_Location_Designation"); }

    public static void main(String[] args) {
        Designation t = new Designation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Designation", "Application Configuration > Location > Designation",
                "Add a Designation: enter Code and Remark, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.Designation dg =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.Designation(page);

        boolean onScreen = dg.navigateViaMenu();
        step(page, "Open Designation screen", "Click Application Configuration -> Location -> Designation",
                "The Designation screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + dg.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("designation links => " + dg.findDesignationLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }

        // The flow does not mention Add — this covers both shapes and says which one the screen is.
        String how = dg.clickAddIfPresent();
        boolean ready = dg.formReady();
        System.out.println("form: " + how);
        if (!ready) {
            System.out.println("--- SCREEN ---\n" + dg.describeForm());
            step(page, "Open the entry form", how, "The Code and Remark boxes are on screen",
                    "Neither box is on screen (" + how + ")", "FAIL");
            addSummary("Result", "FAILED — the entry form never appeared");
            return;
        }

        // This is the shared CommonMaster screen — the Form Name drop-down decides which master is written.
        String formName = dg.ensureFormName("Designation");
        boolean formOk = formName.toLowerCase().contains("designation");
        step(page, "Confirm the master being edited", "Check the Form Name is Designation",
                "Form Name = Designation", formName, formOk ? "PASS" : "FAIL");
        if (!formOk) { addSummary("Result", "FAILED — " + formName); return; }

        // 2) Code + Remark, retrying with different details if the code/name is taken.
        String fill = "", toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            fill = dg.fillCodeAndRemark(attempt);
            // One step per attempt: when the first name turns out to be taken, the report must show the details
            // that were actually saved, not only the ones that were rejected.
            boolean fillOk = fill.contains("Code=" + dg.lastCode) && !fill.contains("(no field)");
            step(page, attempt == 0 ? "Enter Code and Remark" : "Enter Code and Remark (retry " + attempt + ")",
                    "Enter the Code and the Remark", "Code and Remark are entered", fill, fillOk ? "PASS" : "FAIL");
            toast = dg.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("succes") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(dg.lastCode).append('/').append(dg.lastRemark)
                    .append(" -> \"").append(toast).append('"');
            if (ok || !tl.contains("exist")) break;
            System.out.println("Submit: already exists — retrying with a different Code/Remark");
        }

        step(page, "Click Submit", "Click Submit", "The Designation is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (dg.lastSaveApi.isEmpty() ? "" : "  [" + dg.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        step(dg.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"",
                ok ? "PASS" : "FAIL");

        addSummary("Code", dg.lastCode);
        addSummary("Remark", dg.lastRemark);
        addSummary("Save API", dg.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
