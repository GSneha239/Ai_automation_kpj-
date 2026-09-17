package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BedType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Type</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Bed Type</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Bed Type</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class BedType extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Bed Type already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BedType() { super("ApplicationConfig_Admission_BedType"); }

    public static void main(String[] args) {
        BedType t = new BedType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Bed Type", "Application Configuration > Admission > Bed Type",
                "Add a Bed Type: Add, enter Code + Bed Type, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedType bt =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedType(page);

        // 1) Navigate
        bt.navigateViaMenu();
        boolean onScreen = bt.onScreen();
        step(page, "Open Bed Type screen", "Click Application Configuration -> Bed Type",
                "The Bed Type screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = bt.clickAdd();
        step(page, "Click Add", "Click Add", "The Bed Type add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Enter Code + Bed Type, then Submit — retry with fresh details on "already exists" (Code and/or Bed
        // Type can collide with existing records).
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bt.fillCodeAndBedType(attempt);
            used++;
            toast = bt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BedType: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=BT") && !fill.contains("BedType=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Bed Type", "Enter the Code and Bed Type", "Code and Bed Type are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Bed Type Code", bt.lastCode);
        addSummary("Bed Type", bt.lastBedType);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
