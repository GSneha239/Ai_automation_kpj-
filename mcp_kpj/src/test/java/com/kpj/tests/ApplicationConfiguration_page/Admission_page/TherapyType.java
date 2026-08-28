package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named TherapyType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Therapy Type</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Therapy Type</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Therapy Type</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/therapy type already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class TherapyType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / therapy type already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public TherapyType() { super("ApplicationConfig_TherapyType"); }

    public static void main(String[] args) {
        TherapyType t = new TherapyType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Therapy Type", "Application Configuration > Admission > Therapy Type",
                "Add a Therapy Type: enter Code + Therapy Type, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.TherapyType tt =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.TherapyType(page);

        // 1) Navigate
        tt.navigateViaMenu();
        boolean onScreen = tt.onScreen();
        step(page, "Open Therapy Type screen", "Click Application Configuration -> Therapy Type",
                "The Therapy Type screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // (some master screens gate the form behind Add — click it if present)
        tt.clickAddIfPresent();

        // 2) + 3) Enter Code + Therapy Type, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = tt.fillCodeAndType(attempt);
            used++;
            toast = tt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("TherapyType: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=TT") && !fill.contains("Type=(no") && !fill.contains("Type=null");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Therapy Type", "Enter the Code and Therapy Type", "Code and Therapy Type are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // 3) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Therapy Type Code", tt.lastCode);
        addSummary("Therapy Type", tt.lastType);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
