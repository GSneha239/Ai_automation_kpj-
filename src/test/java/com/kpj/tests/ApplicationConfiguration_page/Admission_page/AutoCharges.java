package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AutoCharges — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Auto Charges</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Auto Charges</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select a <b>Service</b> — if it already exists, pick a different one.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class AutoCharges extends DevHisBase {

    public AutoCharges() { super("ApplicationConfig_Admission_AutoCharges"); }

    public static void main(String[] args) {
        AutoCharges t = new AutoCharges();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Auto Charges", "Application Configuration > Admission > Auto Charges",
                "Add an Auto Charge: Add, select a Service (a different one if it already exists), Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.AutoCharges ac =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.AutoCharges(page);

        // 1) Navigate
        ac.navigateViaMenu();
        boolean onScreen = ac.onScreen();
        step(page, "Open Auto Charges screen", "Click Application Configuration -> Auto Charges",
                "The Auto Charges screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = ac.clickAdd();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Select the Location (cascade parent) so the Service dropdown populates.
        String loc = ac.selectLocationFirst();

        // 3+4) Select a service; if it already exists, pick a different one and re-submit.
        java.util.List<String> services = ac.serviceOptions();
        int total = services.size();
        String chosen = null, toast = null;
        boolean ok = false, exists = false;
        StringBuilder tried = new StringBuilder();
        int max = Math.min(total, 8);   // cap attempts
        byte[] addedPng = null;
        String addResult = "";
        boolean addedOk = false;
        for (int i = 0; i < max; i++) {
            chosen = ac.selectServiceByIndex(i);
            if (chosen == null || chosen.isEmpty()) continue;
            // Click Add (beside Services) — it puts the chosen service into the Services grid; Submit saves that grid.
            addResult = ac.clickInnerAdd();
            addedOk = ac.serviceAdded();
            // Screenshot the form with Location + Service entered and added, BEFORE Submit (Submit leaves the form).
            try { addedPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("AutoCharges: filled-form screenshot failed - " + e.getMessage()); }
            // Add silently does nothing for a service that already has an Auto Charge: choose a different one.
            if (!addedOk && i < max - 1) { tried.append(chosen).append("(Add did not land→next) "); continue; }
            toast = ac.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("already") || tl.contains("exist");
            tried.append(chosen).append(ok ? "(SAVED) " : exists ? "(exists→next) " : "(fail) ");
            if (ok) break;
            if (!exists) break;   // a non-duplicate failure won't be fixed by a different service
        }

        step(page, "Select the Location and a Service", "Select the Location, then a Service; if it already exists, pick a different one",
                "Location = " + (loc == null || loc.isEmpty() ? "(none)" : loc) + "; " + total + " service option(s); tried: " + tried,
                chosen == null ? "no service selected" : "using: " + chosen,
                (chosen != null && !chosen.isEmpty()) ? "PASS" : "FAIL");

        if (addedPng != null && addedPng.length > 0) {
            step(addedPng, "Click Add (adds the service to the Services list)", "Click Add beside the Services dropdown",
                    "The chosen service is added to the Services grid", addResult, addedOk ? "PASS" : "FAIL");
        } else {
            step(page, "Click Add (adds the service to the Services list)", "Click Add beside the Services dropdown",
                    "The chosen service is added to the Services grid", addResult, addedOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Not saved — server returned: \"" + toast + "\"" + (ac.lastSaveHttp.isEmpty() ? "" : "  ||  HTTP: " + ac.lastSaveHttp));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // Verify the saved record in the list table (back on the list after a successful Submit).
        if (ok) {
            String verify = ac.verifyRecordInList(chosen);
            step(page, "Verify the record in the list table", "Choose the Location, click Search, look for the saved service in the grid",
                    "The saved service \"" + chosen + "\" is listed in the table",
                    (ac.recordInList ? "PASSES: " : "FAILS: ") + verify, ac.recordInList ? "PASS" : "FAIL");
        }

        addSummary("Service", chosen == null ? "-" : chosen);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
