package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MachineMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Machine Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>CSSD Configuration</b> → <b>Machine Master</b>, click
 *       <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Machine Name</b>, select <b>Sterilization Type</b>, enter
 *       <b>IdealNumberOfTrays</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class MachineMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / machine name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public MachineMaster() { super("ApplicationConfig_CSSD_MachineMaster"); }

    public static void main(String[] args) {
        MachineMaster t = new MachineMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - CSSD Configuration - Machine Master",
                "Application Configuration > CSSD Configuration > Machine Master",
                "Add: Code, Machine Name, Sterilization Type, IdealNumberOfTrays, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.MachineMaster mm =
                new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.MachineMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = mm.navigateViaMenu();
        String landed = mm.currentScreen();
        step(page, "Open Machine Master screen", "Application Configuration -> CSSD Configuration -> Machine Master",
                "The Machine Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Machine Master but the app opened: " + landed + "\n" + mm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add.
        String addHow = mm.clickAdd();
        step(page, "Click Add", "Click Add to open the Machine Master form", "The form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code, Machine Name, Sterilization Type, IdealNumberOfTrays, Submit — retry with different
        // details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = mm.fillDetails(attempt);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("MachineMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = mm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("MachineMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=MC") && !details.contains("(not found)") && !details.contains("(no-options)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code, Machine Name, Sterilization Type, IdealNumberOfTrays",
                    "Enter Code, Machine Name, select Sterilization Type, enter IdealNumberOfTrays",
                    "All four fields are entered", detActual + (detOk ? "" : "\n" + mm.describeForm()), detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code, Machine Name, Sterilization Type, IdealNumberOfTrays",
                    "Enter Code, Machine Name, select Sterilization Type, enter IdealNumberOfTrays",
                    "All four fields are entered", detActual + (detOk ? "" : "\n" + mm.describeForm()), detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + mm.describeForm()
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + mm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (mm.toastPng != null && mm.toastPng.length > 0) {
            step(mm.toastPng, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", mm.lastCode);
        addSummary("Machine Name", mm.lastMachineName);
        addSummary("Sterilization Type", mm.lastSterilizationType);
        addSummary("IdealNumberOfTrays", mm.lastTrays);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
