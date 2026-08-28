package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PrescriptionType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Prescription Type</b> ({@code #/Prescription Type}) — INLINE-ADD.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Prescription Type</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message. If it says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class PrescriptionType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PrescriptionType() { super("ApplicationConfig_Nursing_PrescriptionType"); }

    public static void main(String[] args) {
        PrescriptionType t = new PrescriptionType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Prescription Type",
                "Application Configuration > Nursing > Prescription Type",
                "Add a Prescription Type (inline-add): enter Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PrescriptionType pt =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.PrescriptionType(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = pt.navigateViaMenu();
        String landed = pt.currentScreen();
        String why = pt.showsCodeStoreLeftover()
                ? " - the app served the leftover generic commonmaster form (Code* + Store*) with NO Remark field"
                : "";
        step(page, "Open Prescription Type screen",
                "Application Configuration -> Nursing -> Prescription Type",
                "The Prescription Type screen (Code + Remark) is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Prescription Type but the app opened: " + landed + why
                     + " || " + pt.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - the page is wrong: " + landed + why); return; }

        // Enter Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = pt.fillDetails(attempt);
            used++;
            toast = pt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("PrescriptionType: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=PT") && !details.contains("(no field)");
        String detActual = details + (detOk ? "" : " || form offered: " + pt.describeForm())
                + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");

        // The message itself is the assertion: ONLY a success toast passes.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (pt.toastPng != null && pt.toastPng.length > 0) {
            step(pt.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", pt.lastCode);
        addSummary("Remark", pt.lastRemark);
        addSummary("Route", "#/Prescription Type (inline-add)");
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
