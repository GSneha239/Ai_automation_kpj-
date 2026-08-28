package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named InvestigationTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>InvestigationTemplate</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Investigation</b> → <b>InvestigationTemplate</b> (clicking <b>Add</b> first
 *       if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class InvestigationTemplate extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public InvestigationTemplate() { super("ApplicationConfig_Investigation_InvestigationTemplate"); }

    public static void main(String[] args) {
        InvestigationTemplate t = new InvestigationTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Investigation Template",
                "Application Configuration > Investigation > Investigation Template",
                "Add an Investigation Template: Add, enter Code + Template Name + Remark, select Gender and Pathologist/Radiologist, Submit. "
                        + "On 'already exists', change the details and Submit again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.InvestigationTemplate ag =
                new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.InvestigationTemplate(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = ag.navigateViaMenu();
        String landed = ag.currentScreen();
        step(page, "Open Investigation Template screen", "Application Configuration -> Investigation -> Investigation Template",
                "The Investigation Template screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Investigation Template but the app opened: " + landed + "\n" + ag.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 1b) Learn the Code format from the rows already in the grid, BEFORE opening the add form. On the sibling
        // Interpretation Template a prefixed code was accepted once and then broke every later save on that screen.
        String codeStyle = ag.sampleCodeStyle();
        step(page, "Check the existing Code format", "Read the Code values already in the list grid",
                "The generated Code will match the format this screen already uses", codeStyle, "PASS");

        // 2) Add, if this screen has one (inline-add screens already show the form).
        String addHow = ag.clickAddIfPresent();
        step(page, "Open the add form", "Click Add if the screen has one", "The Code / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Code / Remark, Submit — retry with different details on "already exists".
        String details = "", drops = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = ag.fillDetails(attempt);
            drops = ag.selectDropdowns();
            used++;
            toast = ag.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("InvestigationTemplate: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        // Accept EITHER code format — sampleCodeStyle picks numeric or prefixed to match this screen's own rows,
        // so pinning the assertion to one shape fails a fill that was actually correct.
        boolean detOk = details.matches("(?s).*Code=[A-Za-z0-9][A-Za-z0-9-]{2,}.*")
                && !details.contains("(no field)") && !details.contains("(no description field)");
        step(page, "Enter Code, Name and Remark", "Enter Code* (unique), Name* and Remark*",
                "Code, Name and Remark are entered",
                details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "")
                        + (detOk ? "" : "\n" + ag.describeEditors()),
                detOk ? "PASS" : "FAIL");

        boolean dropOk = !ag.lastGender.isEmpty() && !ag.lastDoctor.isEmpty();
        step(page, "Select Gender and Pathologist/Radiologist",
                "Select a value in the Gender and Pathologist/Radiologist drop-downs",
                "Both drop-downs have a value selected", drops, dropOk ? "PASS" : "FAIL");

        // The API can confirm the save while the UI still shows a wrong toast — say so explicitly rather than
        // leaving a bare "not confirmed", and do NOT flip the step to PASS: no success toast IS the defect here.
        String http = ag.lastSaveHttp == null ? "" : ag.lastSaveHttp;
        boolean apiSaved = http.toLowerCase().contains("saved successfully") || http.contains("\"ResultStatus\":1");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + ag.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : (apiSaved
                                    ? "APP BUG - the record WAS SAVED but no success toast is shown. The save API"
                                      + " returned success and the UI displayed \"" + toast + "\" instead.\nHTTP: " + http
                                    : "Submit not confirmed - server returned: \"" + toast + "\"\nHTTP: " + http)));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (ag.toastPng != null && ag.toastPng.length > 0) {
            step(ag.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Investigation Template Code", ag.lastCode);
        addSummary("Gender", ag.lastGender);
        addSummary("Pathologist/Radiologist", ag.lastDoctor);
        addSummary("Name", ag.lastName);
        addSummary("Remark", ag.lastRemark);
        addSummary("Field models", ag.lastCodeModel + " / " + ag.lastNameModel + " / " + ag.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
