package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Ward — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Ward</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Ward</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Floor</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Ward extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Ward() { super("ApplicationConfig_Ward"); }

    public static void main(String[] args) {
        Ward t = new Ward();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Ward", "Application Configuration > Admission > Ward",
                "Add a Ward: Add, enter Code + Remark + Floor, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.Ward ward =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.Ward(page);

        // 1) Navigate
        ward.navigateViaMenu();
        boolean onScreen = ward.onScreen();
        step(page, "Open Ward screen", "Click Application Configuration -> Ward",
                "The Ward screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = ward.clickAdd();
        step(page, "Click Add", "Click Add", "The Ward add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter Code + Remark + Floor, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = ward.fillDetails(attempt);
            used++;
            toast = ward.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Ward: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=WD") && !fill.contains("Remark=(no") && !fill.contains("Floor=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Floor", "Enter the Code, Remark and Floor",
                "Code, Remark and Floor are entered", fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Ward Code", ward.lastCode);
        addSummary("Remark", ward.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
