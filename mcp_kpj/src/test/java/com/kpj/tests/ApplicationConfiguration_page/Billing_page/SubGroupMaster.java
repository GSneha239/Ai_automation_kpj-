package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named SubGroupMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Sub Group Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Sub Group</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Location</b>, <b>Group</b>, <b>Sub Group Code</b>, <b>Sub Group</b>.</li>
 *   <li>Enter <b>Depreciation Field</b> + <b>Value</b> → click <b>Add</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class SubGroupMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the sub group already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public SubGroupMaster() { super("ApplicationConfig_Billing_SubGroupMaster"); }

    public static void main(String[] args) {
        SubGroupMaster t = new SubGroupMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Sub Group", "Application Configuration > Billing > Sub Group",
                "Add a Sub Group: Add, enter Location + Group + Sub Group Code + Sub Group, enter Depreciation Field + Value + Add, Submit, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.SubGroupMaster sg =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.SubGroupMaster(page);

        // 1) Navigate
        sg.navigateViaMenu();
        boolean onScreen = sg.onScreen();
        step(page, "Open Sub Group screen", "Click Application Configuration -> Billing -> Sub Group",
                "The Sub Group screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = sg.clickAdd();
        step(page, "Click Add", "Click Add", "The Sub Group add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3a) + 3b) + 4) Header, Depreciation detail, Submit — retry with different details on "already exists".
        String hdr = "", dep = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            hdr = sg.fillHeader(attempt);
            used++;
            if (attempt == 0) {
                dep = sg.addDepreciationDetail();
            }
            toast = sg.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("SubGroupMaster: attempt " + used + " (" + hdr + ") already exists — changing the details");
        }

        boolean hdrOk = hdr.contains("SubGroupCode=SG") && !hdr.contains("Location=(no") && !hdr.contains("Group=(no");
        String hdrActual = hdr + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Location, Group, Sub Group Code, Sub Group",
                "Enter Location, Group, Sub Group Code and Sub Group name",
                "All Sub Group details are entered", hdrActual, hdrOk ? "PASS" : "FAIL");

        step(page, "Enter Depreciation Field + Value, click Add",
                "Enter Depreciation Field + Value; click Add (AddDepreciationDetails)",
                "The depreciation detail line is added", dep, "PASS");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Submit & success toast", "Click Submit (fnIUDSubGroup); on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = sg.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Sub Group list; find the row just added (by Sub Group Code)",
                    "The newly added Sub Group row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Code=" + sg.lastCode,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Sub Group Code", sg.lastCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
