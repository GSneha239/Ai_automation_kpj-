package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DialysisParameter — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Dialysis Configuration &gt; <b>Dialysis Parameter</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Dialysis Configuration</b> → <b>Dialysis Parameter</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b> (inline).</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class DialysisParameter extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DialysisParameter() { super("ApplicationConfig_Dialysis_DialysisParameter"); }

    public static void main(String[] args) {
        DialysisParameter t = new DialysisParameter();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Dialysis Configuration - Dialysis Parameter", "Application Configuration > Dialysis Configuration > Dialysis Parameter",
                "Add a Dialysis Parameter: enter Code + Remark, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisParameter dp =
                new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisParameter(page);

        boolean onScreen = dp.navigateViaMenu();
        step(page, "Open Dialysis Parameter screen", "Click Application Configuration -> Dialysis Configuration -> Dialysis Parameter",
                "The Dialysis Parameter screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter Code, Remark, then Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = dp.fillDetails(attempt);
            used++;
            toast = dp.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("DialysisParameter: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=DP");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark", "Enter Code and Remark",
                "Code and Remark are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Dialysis Parameter Code", dp.lastCode);
        addSummary("Remark", dp.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
