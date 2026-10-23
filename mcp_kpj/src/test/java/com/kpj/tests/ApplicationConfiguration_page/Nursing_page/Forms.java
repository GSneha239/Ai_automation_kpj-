package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Forms — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Forms</b> ({@code #/FormsList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Forms</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Form Name*</b> and <b>Form Type*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the Form Name / Type already
 *       exists, select a different Form Name / Form Type combination and Submit again.</li>
 * </ol>
 */
public class Forms extends DevHisBase {

    public Forms() { super("ApplicationConfig_Nursing_Forms"); }

    public static void main(String[] args) {
        Forms t = new Forms();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Forms",
                "Application Configuration > Nursing > Forms",
                "Add a Form: Add, select Form Name + Form Type, Submit; wait for the success toast. On "
                        + "'already exists', pick a different Form Name / Form Type and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Forms fm =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.Forms(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = fm.navigateViaMenu();
        String landed = fm.currentScreen();
        step(page, "Open Forms screen", "Application Configuration -> Nursing -> Forms",
                "The Forms screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Forms but the app opened: " + landed + "\n" + fm.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add
        String addHow = fm.clickAddIfPresent();
        step(page, "Click Add", "Click Add", "The Forms add form is shown",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Form Name / Form Type, Submit — on "already exists" try the next Name / Type combination.
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        int nameIdx = 0, typeIdx = 0;
        for (int attempt = 0; attempt < 400; attempt++) {
            details = fm.selectFormNameAndType(nameIdx, typeIdx);
            used++;
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Forms: filled-form screenshot failed - " + e.getMessage()); }
            toast = fm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Forms: attempt " + used + " (" + details + ") already exists — trying another combination");
            // Walk the Form Type first, then the next Form Name.
            if (typeIdx + 1 < Math.max(1, fm.typeCount)) typeIdx++;
            else if (nameIdx + 1 < Math.max(1, fm.nameCount)) { nameIdx++; typeIdx = 0; }
            else break;   // every combination tried
        }

        boolean detOk = details.contains("Form Name=") && details.contains("Form Type=")
                && !details.contains("(no dropdown)") && !details.contains("(no options)") && !details.contains("(select failed)");
        String detActual = details + (used > 1 ? "  [combination changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Select Form Name and Form Type", "Select the Form Name* and the Form Type*",
                    "Form Name and Form Type are selected", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Form Name and Form Type", "Select the Form Name* and the Form Type*",
                    "Form Name and Form Type are selected", detActual, detOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + fm.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " combination(s): \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + fm.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (fm.toastPng != null && fm.toastPng.length > 0) {
            step(fm.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' pick a different Form Name / Form Type and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' pick a different Form Name / Form Type and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Form Name", fm.lastFormName);
        addSummary("Form Type", fm.lastFormType);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
