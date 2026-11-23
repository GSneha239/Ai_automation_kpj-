package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BillStatus — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Bill Status</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Billing</b> → <b>Bill Status</b> (clicking <b>Add</b> first
 *       if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class BillStatus extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BillStatus() { super("ApplicationConfig_Billing_BillStatus"); }

    public static void main(String[] args) {
        BillStatus t = new BillStatus();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Bill Status",
                "Application Configuration > Billing > Bill Status",
                "Add a Bill Status: enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.BillStatus bs =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.BillStatus(page);

        // 1) Navigate — if the URL is right but Code+Remark are not both offered, FAIL and say why. The
        // route DOES resolve correctly (verified live: URL lands on #/BillStatus) — what actually fails
        // is that the form has no Remark field at all: no matching input, and no rich-text editor either
        // (the one input that looks like a candidate, id="textAngular-editableFix-...", is a hidden
        // accessibility artefact — class="ta-hidden-input" aria-hidden="true", no ng-model, no real
        // binding). This is the same "Code + Store only" defect confirmed on ~35 sibling screens elsewhere
        // in this suite — not a navigation failure, so the message says that plainly instead of "wrong page".
        boolean on = bs.navigateViaMenu();
        String landed = bs.currentScreen();
        step(page, "Open Bill Status screen", "Application Configuration -> Billing -> Bill Status",
                "The Bill Status screen is shown, offering both a Code and a Remark field",
                on ? "Opened " + landed
                   : "Navigation reached " + landed + ", but the form does NOT offer a Remark field — "
                     + "only Code (and Store) are present. Not a navigation defect: the same "
                     + "\"Code + Store only\" gap confirmed on ~35 other generic-master screens in this "
                     + "suite. Detail: " + bs.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — form offers no Remark field (Code + Store only)"); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = bs.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = bs.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("BillStatus: filled-form screenshot failed - " + e.getMessage()); }
            toast = bs.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("BillStatus: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=BS") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + bs.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + bs.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (bs.toastPng != null && bs.toastPng.length > 0) {
            step(bs.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Bill Status Code", bs.lastCode);
        addSummary("Remark", bs.lastRemark);
        addSummary("Field models", bs.lastCodeModel + " / " + bs.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
