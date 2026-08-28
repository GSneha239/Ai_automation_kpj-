package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MewsScore — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Mews Score</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Mews Score</b>, click <b>Add</b>
 *       (a no-op on inline-add screens), enter <b>Code*</b> + <b>Description*</b>.</li>
 *   <li>Select <b>Vital</b>, enter <b>Score</b>, pick a <b>Type</b>, enter <b>Min</b>/<b>Max</b>/<b>Value</b>,
 *       click the inner <b>Add</b> to append the vital-score row.</li>
 *   <li>Enter <b>Interpretation</b> plus the <b>From Score</b>/<b>To Score</b> thresholds for both
 *       "Future Observation" and "Immediate Attention".</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. Any non-success toast is a hard FAIL.</li>
 * </ol>
 */
public class MewsScore extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code/description already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public MewsScore() { super("ApplicationConfig_Nursing_MewsScore"); }

    public static void main(String[] args) {
        MewsScore t = new MewsScore();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Mews Score",
                "Application Configuration > Nursing > Mews Score",
                "Click Add, enter Code + Description; select Vital, enter Score, pick Type, enter Min/Max/Value, "
                        + "click Add (vital-score row); enter Interpretation + From/To Score thresholds for "
                        + "Future Observation and Immediate Attention; click Submit. Any non-success toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MewsScore mw =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MewsScore(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = mw.navigateViaMenu();
        String landed = mw.currentScreen();
        step(page, "Open Mews Score screen", "Application Configuration -> Nursing -> Mews Score",
                "The Mews Score screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Mews Score but the app opened: " + landed + "\n" + mw.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = mw.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Description form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code + Description.
        String header = mw.fillHeaderDetails(0);
        boolean headerOk = header != null && !header.contains("(no field)");
        step(page, "Enter Code and Description", "Enter Code* and Description*",
                "Code and Description are entered", header, headerOk ? "PASS" : "FAIL");

        // 4) Vital + Score + Type + Min/Max/Value.
        String vitalRow = mw.fillVitalScoreRow();
        boolean vitalOk = vitalRow != null && !vitalRow.contains("(no vital select)") && !vitalRow.contains("(no field)");
        step(page, "Select Vital, enter Score, pick Type, enter Min/Max/Value",
                "Select Vital; enter Score; pick Type; enter Min, Max, Value",
                "Vital, Score, Type, Min, Max and Value are set", vitalRow, vitalOk ? "PASS" : "MANUAL");

        // 5) Click Add (inner) — appends the vital-score row to the detail grid.
        String addVital = mw.clickAddVitalRow();
        boolean addVitalOk = addVital != null && addVital.startsWith("inner Add clicked");
        byte[] vitalPng = null;
        try { vitalPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("MewsScore: vital-row screenshot failed - " + e.getMessage()); }
        if (vitalPng != null && vitalPng.length > 0) {
            step(vitalPng, "Click Add (vital-score row)", "Click the inner Add to append the vital-score row",
                    "A row is added to the vital-score detail grid", addVital, addVitalOk ? "PASS" : "MANUAL");
        } else {
            step(page, "Click Add (vital-score row)", "Click the inner Add to append the vital-score row",
                    "A row is added to the vital-score detail grid", addVital, addVitalOk ? "PASS" : "MANUAL");
        }

        // 6) Interpretation + From/To Score thresholds for Future Observation and Immediate Attention.
        String interp = mw.fillInterpretationAndThresholds();
        boolean interpOk = interp != null && !interp.contains("(no field)");
        step(page, "Enter Interpretation, From Score, To Score",
                "Enter Interpretation and the From Score / To Score thresholds for Future Observation and "
                        + "Immediate Attention",
                "Interpretation and both threshold ranges are filled",
                interp, interpOk ? "PASS" : "MANUAL");

        // 7) Submit — retry with different Code/Description on "already exists"; any OTHER (non-success) toast
        // stops the loop and FAILs the step naming the exact message returned.
        String toast = "";
        boolean ok = false, exists = false;
        int used = 1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                mw.fillHeaderDetails(attempt);
                used = attempt + 1;
            }
            toast = mw.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message — stop either way
            System.out.println("MewsScore: attempt " + used + " already exists — changing Code/Description");
        }

        // Wrong/unexpected toast text is always reported as a FAIL, never MANUAL — the user must see exactly
        // what the app actually returned when it is not a genuine success message.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + mw.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different Code/Description: \"" + toast + "\""
                                : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + mw.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (mw.toastPng != null && mw.toastPng.length > 0) {
            step(mw.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change Code/Description and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change Code/Description and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Mews Score Code", mw.lastCode);
        addSummary("Description", mw.lastDescription);
        addSummary("Vital / Score / Type", mw.lastVital + " / " + mw.lastScore + " / " + mw.lastType);
        addSummary("Min / Max / Value", mw.lastMin + " / " + mw.lastMax + " / " + mw.lastValue);
        addSummary("Interpretation", mw.lastInterpretation);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
