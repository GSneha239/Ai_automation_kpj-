package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PriceRevisionApplicability — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Price Revision/ Applicability</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Price Revision/ Applicability</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b> (+ tick a mandatory Location).</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class PriceRevisionApplicability extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PriceRevisionApplicability() { super("ApplicationConfig_Billing_PriceRevisionApplicability"); }

    public static void main(String[] args) {
        PriceRevisionApplicability t = new PriceRevisionApplicability();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Price Revision/ Applicability", "Application Configuration > Billing > Price Revision/ Applicability",
                "Add a Price Revision/Applicability: Add, enter Code + Remark (+ mandatory Location), Submit, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PriceRevisionApplicability pr =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PriceRevisionApplicability(page);

        // 1) Navigate
        pr.navigateViaMenu();
        boolean onScreen = pr.onScreen();
        step(page, "Open Price Revision/ Applicability screen", "Click Application Configuration -> Billing -> Price Revision/ Applicability",
                "The Price Revision/ Applicability screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = pr.clickAdd();
        step(page, "Click Add", "Click Add", "The Price Revision/ Applicability add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = pr.fillDetails(attempt);
            used++;
            toast = pr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("PriceRevisionApplicability: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=PR");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark", "Enter Code and Remark (+ tick a mandatory Location)",
                "Code and Remark are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDtariffDetails); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = pr.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Price Revision/ Applicability list; find the row just added (by Code)",
                    "The newly added row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Code=" + pr.lastCode,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Code", pr.lastCode);
        addSummary("Remark", pr.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
