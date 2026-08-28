package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ComplaintMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Complaint</b> ({@code #/Complaint} → {@code #/addComplaint}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Complaint</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddComplaint}), select <b>Group*</b>, enter <b>Code*</b> and <b>Complaint*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast (screenshotted while visible).</li>
 * </ol>
 */
public class ComplaintMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / complaint already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ComplaintMaster() { super("ApplicationConfig_Nursing_ComplaintMaster"); }

    public static void main(String[] args) {
        ComplaintMaster t = new ComplaintMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Complaint",
                "Application Configuration > Nursing > Complaint",
                "Add a Complaint: Add, select Group, enter Code + Complaint, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ComplaintMaster bs =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ComplaintMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = bs.navigateViaMenu();
        String landed = bs.currentScreen();
        step(page, "Open Complaint screen", "Application Configuration -> Nursing -> Complaint",
                "The Complaint list screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Complaint but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean added = bs.clickAdd();
        step(page, "Click Add", "Click Add (AddComplaint) -> #/addComplaint", "The Complaint add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        // Select Group, enter Code and Complaint, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = bs.fillDetails(attempt);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("ComplaintMaster: filled-form screenshot failed - " + e.getMessage()); }
            toast = bs.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("ComplaintMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=CM") && !details.contains("Group=(no") && !details.contains("Complaint=(no)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Select Group, enter Code and Complaint",
                    "Select Group* (complaint.groupid), enter Code* and Complaint*",
                    "Group, Code and Complaint are set", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Group, enter Code and Complaint",
                    "Select Group* (complaint.groupid), enter Code* and Complaint*",
                    "Group, Code and Complaint are set", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (bs.toastPng != null && bs.toastPng.length > 0) {
            step(bs.toastPng, "Click Submit & success toast",
                    "Click Submit (fnIUDState); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit (fnIUDState); on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Complaint Code", bs.lastCode);
        addSummary("Complaint", bs.lastComplaint);
        addSummary("Group", bs.lastGroup);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/Complaint -> #/addComplaint");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
