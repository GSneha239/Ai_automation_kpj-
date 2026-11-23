package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Container — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Container</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Container</b> (clicking <b>Add</b>
 *       first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Select <b>Colour</b> (enter R/G/B values).</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class Container extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Container() { super("ApplicationConfig_Investigation_Container"); }

    public static void main(String[] args) {
        Container t = new Container();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Container",
                "Application Configuration > Investigation > Container",
                "Add a Container: enter Code + Remark, select Colour (R/G/B), Submit; wait for the success "
                        + "toast. On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Container ctn =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Container(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = ctn.navigateViaMenu();
        String landed = ctn.currentScreen();
        step(page, "Open Container screen", "Application Configuration -> Investigation -> Container",
                "The Container screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Container but the app opened: " + landed + "\n" + ctn.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = ctn.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark / Colour form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code / Remark, Colour, Submit — retry with different details on "already exists".
        String details = "", colour = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null, colourPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ctn.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Container: filled-form screenshot failed - " + e.getMessage()); }
            colour = ctn.selectColour();
            try { colourPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Container: colour screenshot failed - " + e.getMessage()); }
            toast = ctn.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("Container: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("Code=CT") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                    "Code and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        boolean colourOk = !colour.startsWith("(not found)");
        String colourActual = colour + (colourOk ? "" : "\n" + ctn.describeForm());
        if (colourPng != null && colourPng.length > 0) {
            step(colourPng, "Select Colour (enter RGB values)", "Enter R/G/B values for the Colour swatch",
                    "A Colour is set from the entered RGB values", colourActual, colourOk ? "PASS" : "FAIL");
        } else {
            step(page, "Select Colour (enter RGB values)", "Enter R/G/B values for the Colour swatch",
                    "A Colour is set from the entered RGB values", colourActual, colourOk ? "PASS" : "FAIL");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + ctn.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + ctn.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (ctn.toastPng != null && ctn.toastPng.length > 0) {
            step(ctn.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Container Code", ctn.lastCode);
        addSummary("Remark", ctn.lastRemark);
        addSummary("Colour", colour);
        addSummary("Field models", ctn.lastCodeModel + " / " + ctn.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
