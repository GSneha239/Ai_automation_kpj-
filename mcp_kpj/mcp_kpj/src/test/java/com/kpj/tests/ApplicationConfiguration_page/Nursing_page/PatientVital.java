package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientVital — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Patient Vital</b> ({@code #/PatientVitalMaster}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Patient Vital</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddPatientVital}).</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Default Value</b>, <b>Min Value</b> and <b>Max Value</b>.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message. If it says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class PatientVital extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PatientVital() { super("ApplicationConfig_Nursing_PatientVital"); }

    public static void main(String[] args) {
        PatientVital t = new PatientVital();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Patient Vital",
                "Application Configuration > Nursing > Patient Vital",
                "Add a Patient Vital: click Add, enter Code + Remark + Default/Min/Max Value, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PatientVital pv =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PatientVital(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = pv.navigateViaMenu();
        String landed = pv.currentScreen();
        step(page, "Open Patient Vital screen",
                "Application Configuration -> Nursing -> Patient Vital",
                "The Patient Vital list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Patient Vital but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = pv.clickAdd() || pv.onAddForm();
        step(page, "Click Add", "Click Add (AddPatientVital) to open the entry form",
                "The Patient Vital entry form is shown",
                addOk ? "Add form opened - " + pv.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + pv.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + pv.currentScreen() + ")"); return; }

        // Enter Code / Remark / Default / Min / Max, Submit — retry with different Code/Remark on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = pv.fillDetails(attempt);
            used++;
            toast = pv.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("PatientVital: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=PV") && !details.contains("(no field)");
        String detActual = details + (detOk ? "" : " || form offered: " + pv.describeForm())
                + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Default Value, Min Value and Max Value",
                "Enter Code (unique), Remark and the Default / Min / Max values",
                "All the entry fields are filled", detActual, detOk ? "PASS" : "FAIL");

        // The message itself is the assertion: ONLY a success toast passes.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (pv.toastPng != null && pv.toastPng.length > 0) {
            step(pv.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", pv.lastCode);
        addSummary("Remark", pv.lastRemark);
        addSummary("Default Value", pv.lastDefault);
        addSummary("Min Value", pv.lastMin);
        addSummary("Max Value", pv.lastMax);
        addSummary("Route", "#/PatientVitalMaster");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
