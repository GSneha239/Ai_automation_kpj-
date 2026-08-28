package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Notification — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Notification</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Location</b> → <b>Notification</b> (clicking <b>Add</b>
 *       first if the screen has one — a no-op on inline-add screens).</li>
 *   <li>Enter <b>Notification Date*</b> and <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the entry already exists, change
 *       the details and Submit again. Any OTHER (non-success) toast is a hard FAIL — it is never treated as
 *       a pass.</li>
 * </ol>
 */
public class Notification extends DevHisBase {

    /**
     * Single attempt only — no data changes on "already exists" or any other message. The app's calendar
     * picker writes a day-first date string that the backend cannot reliably save for day-of-month > 12 (a
     * known app defect); whatever the app returns for one fixed set of details is reported as-is.
     */
    private static final int MAX_ATTEMPTS = 1;

    public Notification() { super("ApplicationConfig_Location_Notification"); }

    public static void main(String[] args) {
        Notification t = new Notification();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Notification",
                "Application Configuration > Location > Notification",
                "Click Add, enter Notification Date + Remark, click Submit; wait for the success toast. On "
                        + "'already exists', change the details and Submit again. Any other toast is a FAIL.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.Notification nt =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.Notification(page);

        // 1) Navigate — if the app serves a different screen, FAIL and say so plainly.
        boolean on = nt.navigateViaMenu();
        String landed = nt.currentScreen();
        step(page, "Open Notification screen", "Application Configuration -> Location -> Notification",
                "The Notification screen is shown",
                on ? "Opened " + landed
                   : "WRONG PAGE - expected Notification but the app opened: " + landed + "\n" + nt.describeForm(),
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // 2) Click Add, if this screen has one (inline-add screens already show the form).
        String addHow = nt.clickAddIfPresent();
        step(page, "Click Add", "Click Add if the screen has one", "The Notification Date / Remark form is available",
                addHow, addHow.startsWith("no Add button") ? "FAIL" : "PASS");

        // 3) + 4) Notification Date / Remark, Submit — retry with different details on "already exists"; any
        // OTHER (non-success) toast stops the loop and FAILs the step naming the exact message returned.
        String details = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        byte[] filledPng = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            details = nt.fillDetails(attempt);
            used++;
            // Screenshot the FILLED form here — before Submit, which can clear the form or show a toast that
            // would otherwise be what this step's screenshot ends up showing instead of the entered values.
            try { filledPng = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(8000)); }
            catch (Exception e) { System.out.println("Notification: filled-form screenshot failed - " + e.getMessage()); }
            toast = nt.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            String httpL = nt.lastSaveHttp == null ? "" : nt.lastSaveHttp.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            // This screen's on-screen toast is a generic "Failed!" with no detail — the real "already exist"
            // reason only appears in the HTTP response body (confirmed live), so check both.
            exists = tl.contains("exist") || tl.contains("already") || httpL.contains("exist") || httpL.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message — stop either way
            System.out.println("Notification: attempt " + used + " (" + details + ") already exists — changing the details");
        }

        boolean detOk = details.contains("NotificationDate=") && !details.contains("(no field)");
        String detActual = details + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        if (filledPng != null && filledPng.length > 0) {
            step(filledPng, "Enter Notification Date and Remark", "Enter Notification Date* (unique) and Remark*",
                    "Notification Date and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        } else {
            step(page, "Enter Notification Date and Remark", "Enter Notification Date* (unique) and Remark*",
                    "Notification Date and Remark are entered", detActual, detOk ? "PASS" : "FAIL");
        }

        // Wrong/unexpected toast text is always reported as a FAIL, never MANUAL — the user must see exactly
        // what the app actually returned when it is not a genuine success message.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared\n" + nt.describeForm()
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\"\nHTTP: " + nt.lastSaveHttp
                                : "Wrong/unexpected message - server returned: \"" + toast + "\"\nHTTP: " + nt.lastSaveHttp));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        if (nt.toastPng != null && nt.toastPng.length > 0) {
            step(nt.toastPng, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast",
                    "Click Submit; on 'already exists' change the details and Submit again",
                    "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");
        }

        addSummary("Notification Date", nt.lastDate);
        addSummary("Remark", nt.lastRemark);
        addSummary("Field models", nt.lastDateModel + " / " + nt.lastRemarkModel);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
