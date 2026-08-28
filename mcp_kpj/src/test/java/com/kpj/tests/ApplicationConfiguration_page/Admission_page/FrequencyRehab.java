package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FrequencyRehab — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Frequency Rehab</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Frequency Rehab</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b> → <b>Submit</b> → "Master Saved Successfully." toast.</li>
 *   <li>Change a record's <b>Status</b> to true → "Status updated successfully." toast.</li>
 * </ol>
 */
public class FrequencyRehab extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public FrequencyRehab() { super("ApplicationConfig_FrequencyRehab"); }

    public static void main(String[] args) {
        FrequencyRehab t = new FrequencyRehab();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Frequency Rehab", "Application Configuration > Admission > Frequency Rehab",
                "Add a Frequency Rehab: enter Code + Remark, Submit; then change a record's Status to true.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.FrequencyRehab fr =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.FrequencyRehab(page);

        // 1) Navigate
        fr.navigateViaMenu();
        boolean onScreen = fr.onScreen();
        step(page, "Open Frequency Rehab screen", "Click Application Configuration -> Frequency Rehab",
                "The Frequency Rehab screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        fr.clickAddIfPresent();

        // 2) + 3) Enter Code + Remark, Submit — retry with fresh details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = fr.fillCodeAndRemark(attempt);
            used++;
            toast = fr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("FrequencyRehab: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=FR") && !fill.contains("Remark=(no") && !fill.contains("Remark=null");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'Master Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 4) Change status to true -> "Status updated successfully."
        String statusToast = fr.changeStatusToTrue();
        boolean statusOk = statusToast != null && (statusToast.toLowerCase().contains("status")
                || statusToast.toLowerCase().contains("updated") || statusToast.toLowerCase().contains("success"));
        step(page, "Change status to true & toast", "Tick a record's Status checkbox in the grid (change to true)",
                "'Status updated successfully.' toast",
                statusToast == null || statusToast.isEmpty() ? "No status toast appeared" : statusToast, statusOk ? "PASS" : "FAIL");

        addSummary("Frequency Rehab Code", fr.lastCode);
        addSummary("Remark", fr.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", (ok ? toast : "not saved") + " | " + (statusOk ? statusToast : "status not updated"));
    }
}
