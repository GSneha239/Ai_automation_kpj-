package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BloodBagComponent — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Bag Component</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Blood Bag Component</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Expiry</b>, <b>Quantity</b>, <b>Service</b>, <b>Cross Match Service</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class BloodBagComponent extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BloodBagComponent() { super("ApplicationConfig_BloodBank_BloodBagComponent"); }

    public static void main(String[] args) {
        BloodBagComponent t = new BloodBagComponent();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Blood Bag Component", "Application Configuration > Blood Bank > Blood Bag Component",
                "Add a Blood Bag Component: Add, enter Code + Remark + Expiry + Quantity + Service + Cross Match Service, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodBagComponent bb =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodBagComponent(page);

        // 1) Navigate (Blood Bank screens need MENU nav)
        boolean onScreen = bb.navigateViaMenu();
        step(page, "Open Blood Bag Component screen", "Click Application Configuration -> Blood Bank -> Blood Bag Component",
                "The Blood Bag Component screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = bb.clickAdd();
        step(page, "Click Add", "Click Add", "The Blood Bag Component add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bb.fillDetails(attempt);
            used++;
            toast = bb.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BloodBagComponent: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=BC") && !fill.contains("Service=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Expiry, Quantity, Service, Cross Match Service",
                "Enter Code, Remark, Expiry, Quantity; select Service and Cross Match Service",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Blood Bag Component Code", bb.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
