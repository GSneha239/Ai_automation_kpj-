package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AdverseReactionMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Adverse Reaction Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Adverse Reaction Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Comment</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class AdverseReactionMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public AdverseReactionMaster() { super("ApplicationConfig_BloodBank_AdverseReactionMaster"); }

    public static void main(String[] args) {
        AdverseReactionMaster t = new AdverseReactionMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Adverse Reaction Master", "Application Configuration > Blood Bank > Adverse Reaction Master",
                "Add an Adverse Reaction: Add, enter Code + Remark + Comment, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.AdverseReactionMaster ar =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.AdverseReactionMaster(page);

        // 1) Navigate
        ar.navigateViaMenu();
        boolean onScreen = ar.onScreen();
        step(page, "Open Adverse Reaction Master screen", "Click Application Configuration -> Blood Bank -> Adverse Reaction Master",
                "The Adverse Reaction Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = ar.clickAdd();
        step(page, "Click Add", "Click Add", "The Adverse Reaction add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = ar.fillDetails(attempt);
            used++;
            toast = ar.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("AdverseReactionMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=AR") && !fill.contains("=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Comment", "Enter Code, Remark, Comment",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (fnIUDAdverseReaction); on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Adverse Reaction Code", ar.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
