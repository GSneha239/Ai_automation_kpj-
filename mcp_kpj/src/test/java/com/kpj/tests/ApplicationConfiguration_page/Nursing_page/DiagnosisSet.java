package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DiagnosisSet — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Diagnosis Set</b> — list → Add → master-detail form.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Diagnosis Set</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Type 2 digits in <b>Diagnosis Code</b> and pick a value from the drop-down — that AUTO-FILLS
 *       <b>Diagnosis Description</b>.</li>
 *   <li>Enter <b>Diagnosis Set Code</b> and <b>Diagnosis Set Description</b>.</li>
 *   <li>Click <b>Add</b> (commits the detail row), then <b>Submit</b>; wait for the success toast. If the toast
 *       says the code/description already exists, change the details and Submit again.</li>
 * </ol>
 */
public class DiagnosisSet extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DiagnosisSet() { super("ApplicationConfig_Nursing_DiagnosisSet"); }

    public static void main(String[] args) {
        DiagnosisSet t = new DiagnosisSet();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Diagnosis Set",
                "Application Configuration > Nursing > Diagnosis Set",
                "Add a Diagnosis Set: Add, pick a Diagnosis Code from the drop-down (auto-fills the Description), "
                        + "enter the Set Code + Set Description, Add the row, Submit. On 'already exists', change the details.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DiagnosisSet ds =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DiagnosisSet(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = ds.navigateViaMenu();
        String landed = ds.currentScreen();
        step(page, "Open Diagnosis Set screen", "Application Configuration -> Nursing -> Diagnosis Set",
                "The Diagnosis Set list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Diagnosis Set but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add
        boolean added = ds.clickAdd();
        step(page, "Click Add", "Click Add", "The Diagnosis Set add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - Add did not open"); return; }

        // 3) Diagnosis Code — type 2 digits, pick from the drop-down, Description auto-fills
        String diag = ds.pickDiagnosisCode();
        boolean diagOk = !ds.lastDiagnosisDescription.isEmpty();
        step(page, "Select Diagnosis Code (auto-fills Diagnosis Description)",
                "Type 2 numbers in Diagnosis Code and select a value from the drop-down",
                "A Diagnosis Code is selected and Diagnosis Description is auto-filled", diag,
                diagOk ? "PASS" : "FAIL");

        // 4) + 5) Set Code / Set Description, inner Add, Submit — retry with different details on "already exists".
        String setDetails = "", toast = "";
        boolean ok = false, exists = false, rowAdded = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            setDetails = ds.fillSetDetails(attempt);
            if (ds.detailRowCount() == 0) rowAdded = ds.clickInnerAdd();
            else rowAdded = true;
            used++;
            toast = ds.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("DiagnosisSet: attempt " + used + " (" + setDetails + ") already exists — changing the details");
        }

        boolean setOk = setDetails.contains("DiagnosisSetCode=DS") && !setDetails.contains("=(no)");
        step(page, "Enter Diagnosis Set Code and Diagnosis Set Description",
                "Enter Diagnosis Set Code* (unique) and Diagnosis Set Description*",
                "Both Diagnosis Set fields are entered",
                setDetails + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                setOk ? "PASS" : "FAIL");

        step(page, "Click Add (commit the diagnosis row)", "Click Add on the form to add the diagnosis as a row",
                "The diagnosis appears as a detail row",
                rowAdded ? "Detail row added (rows now: " + ds.detailRowCount() + ")" : "Add did NOT produce a detail row",
                rowAdded ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (ds.toastPng != null && ds.toastPng.length > 0) {
            step(ds.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Diagnosis Code", ds.lastDiagnosisCode);
        addSummary("Diagnosis Description", ds.lastDiagnosisDescription);
        addSummary("Diagnosis Set Code", ds.lastSetCode);
        addSummary("Diagnosis Set Description", ds.lastSetDescription);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
