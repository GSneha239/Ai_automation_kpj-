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
        meta("Application Configuration - Location - Payable Waiver",
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

        // Read which Departments already have waivers BEFORE Add is clicked (the list screen's own grid) —
        // some (like "(NAMA DR) MR C/N") already have waivers against many doctors, so cycling only the
        // Doctor within that same Department can exhaust the whole retry budget. Start elsewhere instead.
        java.util.Set<String> existingDepts = pw.existingDepartmentsInList();
        addSummary("Departments already in the list", existingDepts.isEmpty() ? "(none found)" : existingDepts.toString());

        boolean added = pw.clickAdd() && pw.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + pw.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + pw.describeForm());

        // Location, Department, Doctor, Visit Type, Pricing Policy, Service — start Department at the
        // first one not already in the list. Department stays EMPTY until Location commits (see
        // PayableWaiver.selectLocation's javadoc), so Location must be selected first or
        // firstDepartmentIndexAvoiding sees an empty list and silently falls back to index 0.
        pw.selectLocation();
        int deptIndex = pw.firstDepartmentIndexAvoiding(existingDepts);
        int doctorIndex = 0;
        String sel = pw.fillAll(deptIndex, doctorIndex, "7", "10");

        // Some Doctor entries (e.g. a placeholder-like "Doctor_Dr.M") never populate Visit Type — Submit
        // against an empty Visit Type always fails with "Please Select Visit Type!", wasting an attempt on
        // a guaranteed failure. Try the next Doctor instead, then the next Department once Doctors run out,
        // BEFORE ever attempting Submit.
        int vtGuard = 0;
        while (pw.lastVisitType.isEmpty() && vtGuard++ < 60) {
            System.out.println("PayableWaiver: Visit Type empty for " + pw.lastDepartment + "/" + pw.lastDoctor + " — trying the next Doctor");
            doctorIndex++;
            sel = pw.fillAll(deptIndex, doctorIndex, "7", "10");
            // A Department can have no Doctor at all (e.g. "Breast and Endocrine Surgery Unit 4"): keep moving to the next Department THAT HAS
            // NO WAIVER YET until one offers a Doctor — stepping to the next one only once gave up with ~40 still to try, and a plain
            // "next index" walked into CAFETARIA, which already has a waiver ("Waiver already exist!").
            while (pw.lastDoctor.isEmpty()) {
                int next = pw.nextDepartmentIndexAvoiding(existingDepts, deptIndex);
                if (next < 0) break;
                doctorIndex = 0; deptIndex = next;
                sel = pw.fillAll(deptIndex, doctorIndex, "7", "10");
            }
            if (pw.lastDoctor.isEmpty()) break;   // combinations exhausted
        }

        boolean selOk = !pw.lastLocation.isEmpty() && !pw.lastDepartment.isEmpty() && !pw.lastDoctor.isEmpty()
                && !pw.lastVisitType.isEmpty() && !pw.lastPricingPolicy.isEmpty() && !pw.lastService.isEmpty();
        step(page, "Select Location, Department, Doctor, Visit Type, Pricing Policy, Service",
                "Choose Location, Department, Doctor, Visit Type, Pricing Policy and Service",
                "All six are selected", sel, selOk ? "PASS" : "FAIL");

        // Waiver Days, Service Rate — already entered by fillAll above; re-assert and report.
        String rates = pw.fillRates("7", "10");
        boolean ratesOk = !pw.lastWaiverDays.isEmpty() && !pw.lastServiceRate.isEmpty();
        step(page, "Enter Waiver Days and Service Rate", "Type the Waiver Days and the Service Rate",
                "Both are entered", rates, ratesOk ? "PASS" : "FAIL");

        // Submit — when the Department already has a waiver ("Waiver already exist!") go to the next Department without one; when a Doctor
        // leaves Visit Type empty, go to the next Doctor. The last combination tried is always submitted (the loop used to end right after
        // re-filling, leaving the final combination unsubmitted).
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 8 && !ok; attempt++) {
            toast = pw.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(pw.lastDepartment).append('/').append(pw.lastDoctor).append(" -> \"").append(toast).append('"');
            if (ok) break;
            boolean taken = tl.contains("already") || tl.contains("exist");
            if (!taken && !tl.contains("visit type")) break;
            if (taken) existingDepts.add(pw.lastDepartment);
            if (!pw.clickBack()) break;
            if (!(pw.clickAdd() && pw.addFormOpen())) break;
            if (taken) {
                int next = pw.nextDepartmentIndexAvoiding(existingDepts, deptIndex);
                if (next < 0) break;                       // no Department without a waiver is left
                doctorIndex = 0; deptIndex = next;
            } else {
                doctorIndex++;
            }
            String refilled = pw.fillAll(deptIndex, doctorIndex, "7", "10");
            // Same guards as before the loop: skip Departments without a Doctor and Doctors without a Visit Type, never ones already taken.
            int vtGuard2 = 0;
            while ((pw.lastDoctor.isEmpty() || pw.lastVisitType.isEmpty()) && vtGuard2++ < 60) {
                if (!pw.lastDoctor.isEmpty()) doctorIndex++; else doctorIndex = 0;
                if (pw.lastDoctor.isEmpty()) {
                    int next = pw.nextDepartmentIndexAvoiding(existingDepts, deptIndex);
                    if (next < 0) break;
                    deptIndex = next;
                }
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
