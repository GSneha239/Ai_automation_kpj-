package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PricingPolicyMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Pricing Policy Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, tick a <b>Location Details</b> checkbox.</li>
 *   <li>Select <b>Class</b>, <b>Group</b>, <b>Subgroup</b>, enter <b>Discount</b> → click <b>Add</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class PricingPolicyMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PricingPolicyMaster() { super("ApplicationConfig_Billing_PricingPolicyMaster"); }

    public static void main(String[] args) {
        PricingPolicyMaster t = new PricingPolicyMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Pricing Policy Master", "Application Configuration > Billing > Pricing Policy Master",
                "Add a Pricing Policy: Add, enter Code + Remark + Location, select Class + Group + Subgroup + Discount, Add the detail line, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyMaster pp =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyMaster(page);

        // 1) Navigate
        pp.navigateViaMenu();
        boolean onScreen = pp.onScreen();
        step(page, "Open Pricing Policy Master screen", "Click Application Configuration -> Billing -> Pricing Policy Master",
                "The Pricing Policy Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = pp.clickAdd();
        step(page, "Click Add", "Click Add", "The Pricing Policy add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3a) - 4) Header, detail line, Submit — retry with different details on "already exists".
        String hdr = "", det = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            hdr = pp.fillHeader(attempt);
            det = pp.addDetailLine();
            used++;
            toast = pp.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("PricingPolicyMaster: attempt " + used + " (" + hdr + ") already exists — changing the details");
        }

        boolean hdrOk = hdr.contains("Code=PP");
        String hdrActual = hdr + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Location Details", "Enter Code, Remark; tick a Location Details checkbox",
                "Code / Remark / Location entered", hdrActual, hdrOk ? "PASS" : "FAIL");

        boolean detOk = !det.contains("Class=(no") && !det.contains("Group=(no");
        step(page, "Select Class, Group, Subgroup, Discount, click Add",
                "Select Class, Group, Subgroup; enter Discount; click Add (AddTariffDetails)",
                "The detail line is added", det, detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDtariffDetails); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Pricing Policy Code", pp.lastCode);
        addSummary("Remark", pp.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
