package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DialysisMeasure — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Dialysis Configuration &gt; <b>Dialysis Measure</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Dialysis Configuration</b> → <b>Dialysis Measure</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b> (inline).</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class DialysisMeasure extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DialysisMeasure() { super("ApplicationConfig_Dialysis_DialysisMeasure"); }

    public static void main(String[] args) {
        DialysisMeasure t = new DialysisMeasure();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Dialysis Configuration - Dialysis Measure", "Application Configuration > Dialysis Configuration > Dialysis Measure",
                "Add a Dialysis Measure: enter Code + Remark, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisMeasure dm =
                new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisMeasure(page);

        boolean onScreen = dm.navigateViaMenu();
        step(page, "Open Dialysis Measure screen", "Click Application Configuration -> Dialysis Configuration -> Dialysis Measure",
                "The Dialysis Measure screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Enter Code, Remark, then Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = dm.fillDetails(attempt);
            used++;
            toast = dm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("DialysisMeasure: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=DM");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark", "Enter Code and Remark",
                "Code and Remark are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Dialysis Measure Code", dm.lastCode);
        addSummary("Remark", dm.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
