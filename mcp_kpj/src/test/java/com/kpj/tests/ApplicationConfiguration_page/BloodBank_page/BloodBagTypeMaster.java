package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BloodBagTypeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Bag Type Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Blood Bag Type Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, select <b>Item</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class BloodBagTypeMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BloodBagTypeMaster() { super("ApplicationConfig_BloodBank_BloodBagTypeMaster"); }

    public static void main(String[] args) {
        BloodBagTypeMaster t = new BloodBagTypeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Blood Bag Type Master", "Application Configuration > Blood Bank > Blood Bag Type Master",
                "Add a Blood Bag Type: Add, enter Code + Remark + Item, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodBagTypeMaster bt =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodBagTypeMaster(page);

        // 1) Navigate (Blood Bank needs MENU nav)
        boolean onScreen = bt.navigateViaMenu();
        step(page, "Open Blood Bag Type Master screen", "Click Application Configuration -> Blood Bank -> Blood Bag Type Master",
                "The Blood Bag Type Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = bt.clickAdd();
        step(page, "Click Add", "Click Add", "The Blood Bag Type add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bt.fillDetails(attempt);
            used++;
            toast = bt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BloodBagTypeMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=BT") && !fill.contains("Item=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Item", "Enter Code, Remark; select Item",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Blood Bag Type Code", bt.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
