package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RecallReason — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Recall Reason</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>CSSD Configuration</b> → <b>Recall Reason</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Recall Reason</b> (inline — no Add button).</li>
 *   <li>Click <b>Save</b> → success toast.</li>
 * </ol>
 */
public class RecallReason extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / reason already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public RecallReason() { super("ApplicationConfig_CSSD_RecallReason"); }

    public static void main(String[] args) {
        RecallReason t = new RecallReason();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - CSSD Configuration - Recall Reason", "Application Configuration > CSSD Configuration > Recall Reason",
                "Add a Recall Reason: enter Code + Recall Reason (inline), Save.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.RecallReason rr =
                new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.RecallReason(page);

        // 1) Navigate (CSSD Configuration submenu; inline-add screen)
        boolean onScreen = rr.navigateViaMenu();
        step(page, "Open Recall Reason screen", "Click Application Configuration -> CSSD Configuration -> Recall Reason",
                "The Recall Reason screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) + 3) Enter fields (inline — no Add), Save — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = rr.fillDetails(attempt);
            used++;
            toast = rr.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("RecallReason: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=RR");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Recall Reason", "Enter Code and Recall Reason",
                "Code and Recall Reason are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Save & success toast", "Click Save (fnAddRecallReason); on 'already exists' change the details and Save again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Recall Reason Code", rr.lastCode);
        addSummary("Recall Reason", rr.lastReasonName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
