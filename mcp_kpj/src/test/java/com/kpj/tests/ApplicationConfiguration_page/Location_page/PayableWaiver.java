package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PayableWaiver — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Payable Waiver</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Payable Waiver</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Department</b>, <b>Doctor</b>, <b>Visit Type</b>, <b>Pricing Policy</b>,
 *       <b>Service</b> and enter <b>Waiver Days</b>, <b>Service Rate</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class PayableWaiver extends DevHisBase {

    public PayableWaiver() { super("ApplicationConfig_Location_PayableWaiver"); }

    public static void main(String[] args) {
        PayableWaiver t = new PayableWaiver();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Payable Waiver",
                "Application Configuration > Location > Payable Waiver",
                "Add a payable waiver: Location, Department, Doctor, Visit Type, Pricing Policy, Service, Waiver Days, Service Rate, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableWaiver pw =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableWaiver(page);

        boolean onScreen = pw.navigateViaMenu();
        step(page, "Open Payable Waiver screen",
                "Click Application Configuration -> Location -> Payable Waiver",
                "The Payable Waiver screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + pw.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("links => " + pw.findPayableWaiverLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + pw.describeForm());

        boolean added = pw.clickAdd() && pw.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + pw.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + pw.describeForm());

        // Location, Department, Doctor, Visit Type, Pricing Policy, Service
        String sel = pw.selectAll(0, 0);
        boolean selOk = !pw.lastLocation.isEmpty() && !pw.lastDepartment.isEmpty() && !pw.lastDoctor.isEmpty()
                && !pw.lastVisitType.isEmpty() && !pw.lastPricingPolicy.isEmpty() && !pw.lastService.isEmpty();
        step(page, "Select Location, Department, Doctor, Visit Type, Pricing Policy, Service",
                "Choose Location, Department, Doctor, Visit Type, Pricing Policy and Service",
                "All six are selected", sel, selOk ? "PASS" : "FAIL");

        // Waiver Days, Service Rate
        String rates = pw.fillRates("7", "10");
        boolean ratesOk = !pw.lastWaiverDays.isEmpty() && !pw.lastServiceRate.isEmpty();
        step(page, "Enter Waiver Days and Service Rate", "Type the Waiver Days and the Service Rate",
                "Both are entered", rates, ratesOk ? "PASS" : "FAIL");

        // Submit — retry with the next Doctor (then next Department once Doctors are exhausted) when the combo
        // already has a waiver.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int deptIndex = 0, doctorIndex = 0;
        for (int attempt = 0; attempt < 6 && !ok; attempt++) {
            toast = pw.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(pw.lastDepartment).append('/').append(pw.lastDoctor).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            if (!pw.clickBack()) break;
            if (!(pw.clickAdd() && pw.addFormOpen())) break;
            doctorIndex++;
            String refilled = pw.fillAll(deptIndex, doctorIndex, "7", "10");
            while (pw.lastDoctor.isEmpty() && deptIndex < 40) {
                doctorIndex = 0; deptIndex++;
                refilled = pw.fillAll(deptIndex, doctorIndex, "7", "10");
            }
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (pw.lastDoctor.isEmpty()) break;   // combinations exhausted
        }
        step(page, "Click Submit", "Click Submit", "The payable waiver is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (pw.lastSaveApi.isEmpty() ? "" : "  [" + pw.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(pw.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Location", pw.lastLocation);
        addSummary("Department", pw.lastDepartment);
        addSummary("Doctor", pw.lastDoctor);
        addSummary("Visit Type", pw.lastVisitType);
        addSummary("Pricing Policy", pw.lastPricingPolicy);
        addSummary("Service", pw.lastService);
        addSummary("Waiver Days / Service Rate", pw.lastWaiverDays + " / " + pw.lastServiceRate);
        addSummary("Save API", pw.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
