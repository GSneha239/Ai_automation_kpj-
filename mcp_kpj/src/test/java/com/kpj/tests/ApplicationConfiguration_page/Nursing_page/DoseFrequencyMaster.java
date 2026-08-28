package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DoseFrequencyMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Dose Frequency Master</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>Dose Frequency Master</b>, clicking
 *       <b>Add</b> (a no-op on inline-add screens).</li>
 *   <li>Enter <b>Code*</b>, <b>Remark*</b> and <b>Abbreviation*</b>.</li>
 *   <li>Select a <b>Time</b> and click the inner <b>Add</b> to append it to the time-detail grid.</li>
 *   <li>Click <b>Save</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Save again.</li>
 * </ol>
 */
public class DoseFrequencyMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public DoseFrequencyMaster() { super("ApplicationConfig_Nursing_DoseFrequencyMaster"); }

    public static void main(String[] args) {
        DoseFrequencyMaster t = new DoseFrequencyMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Dose Frequency Master",
                "Application Configuration > Nursing > Dose Frequency Master",
                "Click Add, enter Code + Remark + Abbreviation, select a Time and click Add (time-detail row), "
                        + "click Save; wait for the success toast. On 'already exists', change the details and Save again.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DoseFrequencyMaster df =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.DoseFrequencyMaster(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = df.navigateViaMenu();
        String landed = df.currentScreen();
        step(page, "Open Dose Frequency Master screen", "Application Configuration -> Nursing -> Dose Frequency Master",
                "The Dose Frequency Master screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Dose Frequency Master but the app opened: " + landed + "\n" + df.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = df.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Code / Remark / Abbreviation form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) Code / Remark / Abbreviation — retry with different details on "already exists" (checked after Save).
        String details = "";
        byte[] filledPng = null;
        details = df.fillHeaderDetails(0);
        try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
        catch (Exception e) { System.out.println("DoseFrequencyMaster: filled-form screenshot failed - " + e.getMessage()); }
        boolean detOk = details.contains("Code=DF") && !details.contains("(no field)");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Code, Remark, Abbreviation", "Enter Code* (unique), Remark* and Abbreviation*",
                    "Code, Remark and Abbreviation are entered", details, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Code, Remark, Abbreviation", "Enter Code* (unique), Remark* and Abbreviation*",
                    "Code, Remark and Abbreviation are entered", details, detOk ? "PASS" : "FAIL");
        }

        // 4) Select Time + click the inner Add (time-detail row).
        String timeRow = df.selectTimeAndAddRow();
        boolean timeOk = timeRow != null && timeRow.contains("Add row clicked");
        step(page, "Select Time and click Add", "Select a Time; click the inner Add to append the time-detail row",
                "A time row is added to the detail grid", timeRow, timeOk ? "PASS" : "MANUAL");

        // 5) Save — retry with different Code/Remark/Abbreviation on "already exists".
        String toast = "";
        boolean ok = false, exists = false;
        int used = 1;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0) {
                details = df.fillHeaderDetails(attempt);
                used = attempt + 1;
            }
            toast = df.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("DoseFrequencyMaster: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + df.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\"\nHTTP: " + df.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (df.toastPng != null && df.toastPng.length > 0) {
            step(df.toastPng, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save & success toast",
                    "Click Save; on 'already exists' change the details and Save again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Dose Frequency Code", df.lastCode);
        addSummary("Remark", df.lastRemark);
        addSummary("Abbreviation", df.lastAbbr);
        addSummary("Time", df.lastTime);
        addSummary("Field models", df.lastCodeModel + " / " + df.lastRemarkModel + " / " + df.lastAbbrModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
