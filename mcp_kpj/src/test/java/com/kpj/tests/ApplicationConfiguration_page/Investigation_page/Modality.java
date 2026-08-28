package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Modality — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Modality</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Modality</b>.</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b>, <b>Ae Title</b>, <b>Time Slot</b> and <b>No. of Patient</b>
 *       (clicking <b>Add</b> first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Select <b>Department</b> and <b>Equipment</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the details already exist, change
 *       them and Submit again.</li>
 * </ol>
 */
public class Modality extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the record already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Modality() { super("ApplicationConfig_Investigation_Modality"); }

    public static void main(String[] args) {
        Modality t = new Modality();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Modality",
                "Application Configuration > Investigation > Modality",
                "Add a Modality: enter Code, Remark, Ae Title, Time Slot and No. of Patient, select Department "
                        + "and Equipment, then Submit and wait for the success toast. On 'already exists', change "
                        + "the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Modality md =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Modality(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = md.navigateViaMenu();
        String landed = md.currentScreen();
        step(page, "Open Modality screen", "Application Configuration -> Investigation -> Modality",
                "The Modality screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Modality but the app opened: " + landed + "\n" + md.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = md.sampleCodeStyle();
        System.out.println("Modality code style => " + codeStyle);

        // Add, if this screen has one (inline-add screens already show the form).
        String addHow = md.clickAddIfPresent();
        System.out.println("Modality add => " + addHow);
        System.out.println("Modality form => " + md.describeForm());

        // 2) .. 4) fields -> dropdowns -> Submit; retry with different details on "already exists".
        String details = "", picks = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = md.fillDetails(attempt);
            picks = md.selectDepartmentAndEquipment();
            used++;
            toast = md.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            // "succes" (one s) is deliberate — sibling screens here return "Record added succesfully".
            ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Modality: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Code, Remark, Ae Title, Time Slot and No. of Patient",
                "Enter Code* (unique), Remark*, Ae Title, Time Slot and No. of Patient",
                "All five values are entered",
                details + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        boolean picksOk = !picks.contains("(not selected)");
        step(page, "Select Department and Equipment", "Select a Department and an Equipment",
                "Department and Equipment are selected",
                picksOk ? picks : picks + "\n" + md.describeForm(), picksOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + md.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + md.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (md.toastPng != null && md.toastPng.length > 0) {
            step(md.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Modality Code", md.lastCode);
        addSummary("Remark", md.lastRemark);
        addSummary("Ae Title", md.lastAeTitle);
        addSummary("Time Slot", md.lastTimeSlot);
        addSummary("No. of Patient", md.lastNoOfPatient);
        addSummary("Department", md.lastDepartment);
        addSummary("Equipment", md.lastEquipment);
        addSummary("Field models", md.lastCodeModel + " / " + md.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
