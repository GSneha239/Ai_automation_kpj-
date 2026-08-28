package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LabSample — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Sample</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Lab Sample</b> (NOT the neighbouring
 *       Lab Sample Suitability — the step FAILs and names the screen if the app opens that instead).</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code already exists, change the
 *       details and Submit again.</li>
 * </ol>
 */
public class LabSample extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public LabSample() { super("ApplicationConfig_Investigation_LabSample"); }

    public static void main(String[] args) {
        LabSample t = new LabSample();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Lab Sample",
                "Application Configuration > Investigation > Lab Sample",
                "Add a Lab Sample: Add, enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabSample ls =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabSample(page);

        // 1) Navigate — if the app serves a different screen (Lab Sample Suitability sits next to it), FAIL and say so.
        boolean on = ls.navigateViaMenu();
        String landed = ls.currentScreen();
        step(page, "Open Lab Sample screen", "Application Configuration -> Investigation -> Lab Sample",
                "The Lab Sample screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Lab Sample but the app opened: " + landed + "\n" + ls.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = ls.sampleCodeStyle();
        System.out.println("LabSample code style => " + codeStyle);

        // 2) Add (inline-add screens already show the form).
        String addHow = ls.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Lab Sample form", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("LabSample form => " + ls.describeForm());

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ls.fillDetails(attempt);
            used++;
            toast = ls.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            // "succes" (one s) is deliberate — sibling screens here return "Record added succesfully".
            ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("LabSample: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = !details.contains("(no field)");
        // The screen also makes Sample Type Name* mandatory — without it Submit fails with the misleading
        // "Please Enter Description!", so it is filled here alongside the two fields the flow names.
        step(page, "Enter Code and Remark", "Enter Code* (unique), Sample Type Name* and Remark*",
                "Code, Sample Type Name and Remark are entered",
                details + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + ls.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + ls.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (ls.toastPng != null && ls.toastPng.length > 0) {
            step(ls.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Sample Type Code", ls.lastCode);
        addSummary("Sample Type Name", ls.lastName);
        addSummary("Remark", ls.lastRemark);
        addSummary("Field models", ls.lastCodeModel + " / " + ls.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
