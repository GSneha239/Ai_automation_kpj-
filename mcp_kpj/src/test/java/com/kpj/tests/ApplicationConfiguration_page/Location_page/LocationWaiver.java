package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LocationWaiver — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Location Waiver</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Location Waiver</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Pricing Policy</b>, <b>Service</b> and enter <b>Waiver Days</b>,
 *       <b>Service Rate</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class LocationWaiver extends DevHisBase {

    public LocationWaiver() { super("ApplicationConfig_Location_LocationWaiver"); }

    public static void main(String[] args) {
        LocationWaiver t = new LocationWaiver();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location Waiver",
                "Application Configuration > Location > Location Waiver",
                "Add a location waiver: Location, Pricing Policy, Service, Waiver Days, Service Rate, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.LocationWaiver lw =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.LocationWaiver(page);

        boolean onScreen = lw.navigateViaMenu();
        step(page, "Open Location Waiver screen",
                "Click Application Configuration -> Location -> Location Waiver",
                "The Location Waiver screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + lw.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("waiver links => " + lw.findWaiverLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + lw.describeForm());

        boolean added = lw.clickAdd() && lw.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + lw.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + lw.describeForm());

        // Location, Pricing Policy, Service
        String sel = lw.selectAll(0, 0);
        boolean selOk = !lw.lastLocation.isEmpty() && !lw.lastPricingPolicy.isEmpty() && !lw.lastService.isEmpty();
        step(page, "Select Location, Pricing Policy, Service", "Choose Location, Pricing Policy and Service",
                "All three are selected", sel, selOk ? "PASS" : "FAIL");

        // Waiver Days, Service Rate
        String rates = lw.fillRates("7", "10");
        boolean ratesOk = !lw.lastWaiverDays.isEmpty() && !lw.lastServiceRate.isEmpty();
        step(page, "Enter Waiver Days and Service Rate", "Type the Waiver Days and the Service Rate",
                "Both are entered", rates, ratesOk ? "PASS" : "FAIL");

        // Submit — retry across Pricing Policy/Service combinations when the combo already has a waiver.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int ppIndex = 0, svcIndex = 0;
        for (int attempt = 0; attempt < 8 && !ok; attempt++) {
            toast = lw.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(lw.lastPricingPolicy).append('/').append(lw.lastService).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            // A duplicate rejection (ResultStatus 2) leaves the add form OPEN with the rejected values still in
            // it — Submit only navigates back to the list on success. Always cycle Back -> Add so the retry lands
            // on a genuinely fresh form. clickBack() is a harmless no-op if Submit already left us on the list —
            // onScreen()'s text fallback matches the add form's own header too ("Location Waiver"), so it cannot
            // tell list and form apart; clickBack()/clickAdd() succeeding or no-op'ing is the reliable signal.
            lw.clickBack();
            if (!(lw.clickAdd() && lw.addFormOpen())) break;
            // Keep advancing Service (then Pricing Policy once Service is exhausted) until a combination actually
            // FILLS — resubmitting on a Service that came back empty just trades "already exist" for
            // "Please Select Service!" over a form that only looks complete.
            svcIndex++;
            String refilled = lw.fillAll(ppIndex, svcIndex, "7", "10");
            while (lw.lastService.isEmpty() && ppIndex < 40) {
                svcIndex = 0; ppIndex++;
                refilled = lw.fillAll(ppIndex, svcIndex, "7", "10");
            }
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
            if (lw.lastService.isEmpty()) break;   // combinations exhausted
        }
        step(page, "Click Submit", "Click Submit", "The location waiver is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (lw.lastSaveApi.isEmpty() ? "" : "  [" + lw.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(lw.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'... Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Location", lw.lastLocation);
        addSummary("Pricing Policy", lw.lastPricingPolicy);
        addSummary("Service", lw.lastService);
        addSummary("Waiver Days / Service Rate", lw.lastWaiverDays + " / " + lw.lastServiceRate);
        addSummary("Save API", lw.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
