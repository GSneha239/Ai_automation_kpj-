package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DepartmentWaiver — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Department Waiver</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Department Waiver</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Department</b>, <b>Pricing Policy</b>, <b>Service</b>.</li>
 *   <li>Enter <b>Waiver Days</b>, <b>Service Rate</b>, <b>Emergency Rate</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class DepartmentWaiver extends DevHisBase {

    public DepartmentWaiver() { super("ApplicationConfig_Location_DepartmentWaiver"); }

    public static void main(String[] args) {
        DepartmentWaiver t = new DepartmentWaiver();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Department Waiver",
                "Application Configuration > Location > Department Waiver",
                "Add a department waiver: Location, Department, Pricing Policy, Service, Waiver Days, Service Rate, Emergency Rate, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.DepartmentWaiver dw =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.DepartmentWaiver(page);

        boolean onScreen = dw.navigateViaMenu();
        step(page, "Open Department Waiver screen",
                "Click Application Configuration -> Location -> Department Waiver",
                "The Department Waiver screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + dw.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("waiver links => " + dw.findWaiverLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }

        // Read which Departments already have a waiver BEFORE Add is clicked (the list screen's own grid) —
        // every one of them will answer "Waiver already exist!" on Submit no matter which Service is tried,
        // so picking a Department NOT already in this list gives Submit its best shot at succeeding first try.
        java.util.Set<String> existingDepts = dw.existingDepartmentsInList();
        addSummary("Departments already in the list", existingDepts.isEmpty() ? "(none found)" : existingDepts.toString());

        boolean added = dw.clickAdd() && dw.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- ADD FORM ---\n" + dw.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }

        // 3) Location, Department, Pricing Policy, Service — Department avoids every one already in the list.
        String sel = dw.selectAll(existingDepts);
        boolean selOk = !sel.contains("(not set)");
        step(page, "Select Location, Department, Pricing Policy, Service",
                "Choose the Location, Department, Pricing Policy and Service",
                "All four are selected", sel, selOk ? "PASS" : "FAIL");

        // 4) Waiver Days, Service Rate, Emergency Rate
        String rates = dw.fillRates("7", "150", "200");
        boolean ratesOk = !rates.contains("(not set)");
        step(page, "Enter Waiver Days, Service Rate, Emergency Rate",
                "Enter the Waiver Days, the Service Rate and the Emergency Rate",
                "All three values are entered", rates, ratesOk ? "PASS" : "FAIL");

        // 5) Submit. This Department/Service may already have a waiver — the server answers "Waiver already exist"
        // and writes nothing; the retry then takes a fresh form and the next Department/Service combination.
        String toast = "", tl = "";
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            toast = dw.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(dw.lastDepartment).append('/').append(dw.lastService)
                    .append(" -> \"").append(toast).append('"');
            if (ok || !tl.contains("already")) break;
            if (!dw.clickBack()) break;
            if (!(dw.clickAdd() && dw.addFormOpen())) break;
            String moved = dw.nextCombination();
            if (moved.isEmpty()) { System.out.println("Submit: no untouched Department/Service combination left"); break; }
            System.out.println("Submit retry " + (attempt + 1) + ": " + moved + " | " + dw.fillRates("7", "150", "200"));
        }
        step(page, "Click Submit", "Click Submit", "The department waiver is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (dw.lastSaveApi.isEmpty() ? "" : "  [" + dw.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // 6) Success toast
        step(dw.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... saved successfully' toast",
                toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"",
                ok ? "PASS" : "FAIL");

        addSummary("Location", dw.lastLocation);
        addSummary("Department", dw.lastDepartment);
        addSummary("Pricing Policy", dw.lastPricingPolicy);
        addSummary("Service", dw.lastService);
        addSummary("Waiver Days / Service Rate / Emergency Rate",
                dw.lastWaiverDays + " / " + dw.lastServiceRate + " / " + dw.lastEmergencyRate);
        addSummary("Save API", dw.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
