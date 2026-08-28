package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named VaccineScheduleMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Vaccination Schedule</b> (VaccineScheduleMaster,
 * {@code #/VaccineScheduleMasterList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Vaccination Schedule</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddVaccineScheduleMaster} → {@code #/add-VaccineScheduleMaster}).</li>
 *   <li>Enter <b>Schedule Name*</b>, <b>Age (In Days)*</b> and <b>Vaccination Name*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDVaccineSchedule}); the toast must be a SUCCESS message. If it says the
 *       schedule/vaccination name already exists, change the details and Submit again.</li>
 * </ol>
 */
public class VaccineScheduleMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public VaccineScheduleMaster() { super("ApplicationConfig_Nursing_VaccineScheduleMaster"); }

    public static void main(String[] args) {
        VaccineScheduleMaster t = new VaccineScheduleMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Vaccination Schedule (VaccineScheduleMaster)",
                "Application Configuration > Nursing > Vaccination Schedule",
                "Add a Vaccination Schedule: click Add, enter Schedule Name + Age (In Days) + Vaccination Name, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.VaccineScheduleMaster vs =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.VaccineScheduleMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = vs.navigateViaMenu();
        String landed = vs.currentScreen();
        step(page, "Open Vaccination Schedule screen",
                "Application Configuration -> Nursing -> Vaccination Schedule",
                "The Vaccination Schedule list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Vaccination Schedule but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = vs.clickAdd() || vs.onAddForm();
        step(page, "Click Add", "Click Add (AddVaccineScheduleMaster) to open the entry form",
                "The Vaccination Schedule entry form is shown",
                addOk ? "Add form opened - " + vs.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + vs.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + vs.currentScreen() + ")"); return; }

        // Enter Schedule Name / Age / Vaccination Name.
        String details = vs.fillDetails(0);
        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Schedule Name, Age (In Days) and Vaccination Name",
                "Enter Schedule Name*, Age (In Days)* and Vaccination Name*",
                "All three entry fields are filled", details, detOk ? "PASS" : "FAIL");

        // Click the inner Add to commit the line into the detail grid (VacSchList) — per the page object's own
        // note, an uncommitted row is what made Submit answer "Error!" on the sibling Pain Screening Master.
        String innerAdd = vs.clickInnerAdd();
        boolean innerAddOk = innerAdd != null && innerAdd.contains("rows=") && !innerAdd.contains("rows=-1") && !innerAdd.contains("NO row was committed");
        step(page, "Click Add", "Click the form's inner Add (AddVaccineSchedule) to commit the line into the detail grid",
                "A row is added to the detail grid (VacSchList)", innerAdd, innerAddOk ? "PASS" : "FAIL");

        // Submit — retry with different Schedule/Vaccination names on "already exists". Each retry re-fills and
        // re-commits a fresh detail row, since the uncommitted-row failure mode applies on every attempt.
        String toast = "";
        boolean ok = false, exists = false;
        int used = 1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                details = vs.fillDetails(attempt);
                vs.clickInnerAdd();
                used = attempt + 1;
            }
            toast = vs.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("VaccineScheduleMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        // The message itself is the assertion: ONLY a success toast passes.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (vs.toastPng != null && vs.toastPng.length > 0) {
            step(vs.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUDVaccineSchedule); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUDVaccineSchedule); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Schedule Name", vs.lastScheduleName);
        addSummary("Age (In Days)", vs.lastAgeInDays);
        addSummary("Vaccination Name", vs.lastVaccineName);
        addSummary("Route", "#/VaccineScheduleMasterList -> #/add-VaccineScheduleMaster");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
