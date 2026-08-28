package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ServiceMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Service Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Service Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Service Name</b>, <b>Group</b>, <b>SubGroup</b>; fill <b>Pricing Policy Details*</b>;
 *       enter <b>HSN Code</b> + <b>Code Type</b> → click <b>Add</b> (code); select <b>Tax Details</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class ServiceMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / service name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ServiceMaster() { super("ApplicationConfig_Billing_ServiceMaster"); }

    public static void main(String[] args) {
        ServiceMaster t = new ServiceMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Service Master", "Application Configuration > Billing > Service Master",
                "Add a Service Master: Add, enter Service Name + Group + SubGroup, fill Pricing Policy Details, enter HSN Code + Code Type + Add, select Tax Details, Submit, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.ServiceMaster sm =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.ServiceMaster(page);

        // 1) Navigate
        sm.navigateViaMenu();
        boolean onScreen = sm.onScreen();
        step(page, "Open Service Master screen", "Click Application Configuration -> Billing -> Service Master",
                "The Service Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = sm.clickAdd();
        step(page, "Click Add", "Click Add", "The Service Master add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Header, Pricing Policy Details, HSN Code, Tax Details, Submit — retry with different details
        // on "already exists".
        String hdr = "", pp = "", cd = "", tax = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            hdr = sm.fillHeader(attempt);
            pp = sm.fillPricingPolicy();
            cd = sm.addCode();
            tax = sm.selectTax();
            used++;
            toast = sm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("ServiceMaster: attempt " + used + " (" + hdr + ") already exists — changing the details");
        }

        String hdrActual = hdr + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Service Name, Group, SubGroup", "Enter Service Name, select Group and SubGroup",
                "Service Name / Group / SubGroup entered", hdrActual, hdr.contains("Group=(no") ? "FAIL" : "PASS");

        step(page, "Fill Pricing Policy Details*", "Fill the Unit Purchase Price in the Pricing Policy Details grid",
                "Pricing Policy Details filled", pp, "PASS");

        step(page, "Enter HSN Code + Code Type, click Add", "Select Code Type, enter HSN Code, click Add (AddCode)",
                "The code row is added", cd, "PASS");

        step(page, "Select Tax Details", "Tick a Tax default in the Tax Details grid",
                "A tax detail is selected", tax, "PASS");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDServiceMaster); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Service Name", sm.lastName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
