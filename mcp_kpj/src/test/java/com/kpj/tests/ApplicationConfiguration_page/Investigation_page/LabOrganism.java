package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named LabOrganism — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Lab Organism</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>Lab Organism</b>.</li>
 *   <li>Click <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Tick a check box in the grid inside {@code #LabOrganismForm > div:nth-of-type(2)}.</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code already exists, change the
 *       details and Save again.</li>
 * </ol>
 */
public class LabOrganism extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public LabOrganism() { super("ApplicationConfig_Investigation_LabOrganism"); }

    public static void main(String[] args) {
        LabOrganism t = new LabOrganism();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Lab Organism",
                "Application Configuration > Investigation > Lab Organism",
                "Add a Lab Organism: Add, enter Code + Remark, tick a check box in the form grid, then Save and "
                        + "wait for the success toast. On 'already exists', change the details and Save again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabOrganism lo =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.LabOrganism(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = lo.navigateViaMenu();
        String landed = lo.currentScreen();
        step(page, "Open Lab Organism screen", "Application Configuration -> Investigation -> Lab Organism",
                "The Lab Organism screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Lab Organism but the app opened: " + landed + "\n" + lo.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // Read the list grid BEFORE Add so the generated Code matches the format this screen already uses.
        String codeStyle = lo.sampleCodeStyle();
        System.out.println("LabOrganism code style => " + codeStyle);

        // 2) Add (inline-add screens already show the form).
        String addHow = lo.clickAddIfPresent();
        step(page, "Click Add", "Click Add to open the Lab Organism form", "The Lab Organism form is shown",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");
        System.out.println("LabOrganism form => " + lo.describeForm());

        // 3) .. 5) Code / Remark -> tick a grid check box -> Save; retry on "already exists".
        String details = "", tick = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = lo.fillDetails(attempt);
            tick = lo.tickGridCheckbox();
            used++;
            toast = lo.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("LabOrganism: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + "  [" + codeStyle + "]" + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : ""),
                detOk ? "PASS" : "FAIL");

        boolean tickOk = lo.lastTickOk(tick);
        step(page, "Tick a check box in the grid",
                "Select any check box from the grid at #LabOrganismForm > div:nth-of-type(2)",
                "One grid check box is ticked",
                tickOk ? tick : tick + "\n" + lo.describeForm(), tickOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + lo.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + lo.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (lo.toastPng != null && lo.toastPng.length > 0) {
            step(lo.toastPng, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Lab Organism Code", lo.lastCode);
        addSummary("Remark", lo.lastRemark);
        addSummary("Ticked row", lo.lastTicked.isEmpty() ? tick : lo.lastTicked);
        addSummary("Field models", lo.lastCodeModel + " / " + lo.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
