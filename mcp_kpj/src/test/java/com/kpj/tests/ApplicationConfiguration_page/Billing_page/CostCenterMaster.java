package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CostCenterMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Cost Centre</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Cost Centre</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>; enter <b>Cost Centre Code</b>, <b>Cost Centre Name</b>, <b>Profit Centre</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class CostCenterMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public CostCenterMaster() { super("ApplicationConfig_Billing_CostCenterMaster"); }

    public static void main(String[] args) {
        CostCenterMaster t = new CostCenterMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Cost Centre", "Application Configuration > Billing > Cost Centre",
                "Add a Cost Centre: Add, select Location, enter Cost Centre Code + Cost Centre Name + Profit Centre, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.CostCenterMaster cc =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.CostCenterMaster(page);

        // 1) Navigate
        cc.navigateViaMenu();
        boolean onScreen = cc.onScreen();
        step(page, "Open Cost Centre screen", "Click Application Configuration -> Billing -> Cost Centre",
                "The Cost Centre screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = cc.clickAdd();
        step(page, "Click Add", "Click Add", "The Cost Centre add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = cc.fillDetails(attempt);
            used++;
            toast = cc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("CostCenterMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("CostCentreCode=CC") && !fill.contains("Location=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Location, Cost Centre Code, Name, Profit Centre",
                "Select Location; enter Cost Centre Code, Cost Centre Name, Profit Centre",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDCostCenterMaster); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Cost Centre Code", cc.lastCode);
        addSummary("Cost Centre Name", cc.lastName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
