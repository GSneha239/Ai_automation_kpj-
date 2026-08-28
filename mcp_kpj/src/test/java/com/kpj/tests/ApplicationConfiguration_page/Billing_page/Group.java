package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Group — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Group</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Billing</b> → <b>Group</b>, click <b>Add</b>.</li>
 *   <li>Enter <b>Group Code</b> and <b>Group</b> (name).</li>
 *   <li>Fill <b>Group Location Details</b>: Code, OPLedger, IPLedger, Cost Centre, OPD MarkUp, IPD MarkUp,
 *       RefEntitySharePer, Print Order.</li>
 *   <li>Select the <b>true/false</b> status toggle.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast.</li>
 * </ol>
 */
public class Group extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the group code / name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Group() { super("ApplicationConfig_Billing_Group"); }

    public static void main(String[] args) {
        Group t = new Group();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Group",
                "Application Configuration > Billing > Group",
                "Add a Group: Code + Group, Location Details (Code, OPLedger, IPLedger, Cost Centre, "
                        + "OPD MarkUp, IPD MarkUp, RefEntitySharePer, Print Order), true/false status, Submit; "
                        + "wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.Group gr =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.Group(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = gr.navigateViaMenu();
        String landed = gr.currentScreen();
        step(page, "Open Group screen", "Application Configuration -> Billing -> Group",
                "The Group screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Group but the app opened: " + landed + "\n" + gr.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add.
        String addHow = gr.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Group form", "The Group form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) - 6) Group Code + Group (name), Location Details, true/false, Submit — retry with different
        // details on "already exists".
        String header = "", loc = "", statusResult = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] headerPng = null, locPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            header = gr.fillGroupHeader(attempt);
            try { headerPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Group: header screenshot failed - " + e.getMessage()); }

            loc = gr.fillLocationDetails(attempt);
            try { locPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Group: location details screenshot failed - " + e.getMessage()); }

            statusResult = gr.selectStatusTrueFalse();
            used++;

            toast = gr.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Group: attempt " + used + " (" + header + ") already exists — changing the details");
        }

        boolean headerOk = header.contains("GroupCode=GR") && !header.contains("(not found)");
        String headerActual = header + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (headerPng != null && headerPng.length > 0) {
            step(headerPng, "Enter Group Code and Group", "Enter Group Code* and Group* (name)",
                    "Group Code and Group are entered", headerActual, headerOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Group Code and Group", "Enter Group Code* and Group* (name)",
                    "Group Code and Group are entered", headerActual, headerOk ? "PASS" : "FAIL");
        }

        boolean locOk = !loc.contains("(not found)");
        if (locPng != null && locPng.length > 0) {
            step(locPng, "Fill Group Location Details",
                    "Fill Code, OPLedger, IPLedger, Cost Centre, OPD MarkUp, IPD MarkUp, RefEntitySharePer, Print Order",
                    "All Group Location Details fields are filled",
                    loc + (locOk ? "" : "\n" + gr.describeForm()), locOk ? "PASS" : "FAIL");
        } else {
            step(page, "Fill Group Location Details",
                    "Fill Code, OPLedger, IPLedger, Cost Centre, OPD MarkUp, IPD MarkUp, RefEntitySharePer, Print Order",
                    "All Group Location Details fields are filled",
                    loc + (locOk ? "" : "\n" + gr.describeForm()), locOk ? "PASS" : "FAIL");
        }

        boolean statusOk = !statusResult.startsWith("(no true/false");
        step(page, "Select true/false", "Select the status toggle (True)",
                "The true/false status is set", statusResult, statusOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + gr.describeForm()
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + gr.lastSaveHttp));
        if (gr.toastPng != null && gr.toastPng.length > 0) {
            step(gr.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Group Code", gr.lastGroupCode);
        addSummary("Group", gr.lastGroup);
        addSummary("Location Details Code", gr.lastLocationCode);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
