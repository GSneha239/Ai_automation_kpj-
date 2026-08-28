package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OrganismComment — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Organism Comment</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Organism Comment</b> (NOT the
 *       neighbouring Lab Organism screen — the step FAILs and names the screen if the app opens that instead).</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and BOTH <b>Remark</b> boxes.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the details already exist, change
 *       them and Submit again.</li>
 * </ol>
 */
public class OrganismComment extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the record already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public OrganismComment() { super("ApplicationConfig_Investigation_OrganismComment"); }

    public static void main(String[] args) {
        OrganismComment t = new OrganismComment();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Organism Comment",
                "Application Configuration > Investigation > Organism Comment",
                "Add an Organism Comment: Add, enter Code and both Remark boxes, Submit; wait for the success "
                        + "toast. On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.OrganismComment oc =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.OrganismComment(page);

        // 1) Navigate — if the app serves a different screen (Lab Organism sits next to it), FAIL and say so.
        boolean on = oc.navigateViaMenu();
        String landed = oc.currentScreen();
        step(page, "Open Organism Comment screen", "Application Configuration -> Investigation -> Organism Comment",
                "The Organism Comment screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Organism Comment but the app opened: " + landed + "\n" + oc.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = oc.sampleCodeStyle();
        System.out.println("OrganismComment code style => " + codeStyle);

        // 2) Add (inline-add screens already show the form).
        String addHow = oc.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Organism Comment form", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("OrganismComment form => " + oc.describeForm());

        // 3) + 4) Code / both Remarks, Submit — retry with different details on "already exists".
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = oc.fillDetails(attempt);
            used++;
            toast = oc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            // "succes" (one s) is deliberate — sibling screens here return "Record added succesfully".
            ok = tl.contains("saved") || tl.contains("succes") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("OrganismComment: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Code and both Remarks", "Enter Code* (unique) and both Remark boxes",
                "Code and both Remarks are entered",
                details + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + oc.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + oc.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (oc.toastPng != null && oc.toastPng.length > 0) {
            step(oc.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Organism Comment Code", oc.lastCode);
        addSummary("Remark", oc.lastRemark);
        addSummary("Remark 2", oc.lastRemark2);
        addSummary("Field models", oc.lastCodeModel + " / " + oc.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
