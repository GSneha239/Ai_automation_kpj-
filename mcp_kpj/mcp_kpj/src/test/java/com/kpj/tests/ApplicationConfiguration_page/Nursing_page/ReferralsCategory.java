package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ReferralsCategory — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>ReferralsCategory</b> ({@code #/ReferralsCategory}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Nursing</b> → <b>ReferralsCategory</b>.</li>
 *   <li>Enter <b>Code*</b> and <b>Remark*</b> (clicking Add first if the screen has one).</li>
 *   <li>Click <b>Submit</b>; the toast must be a SUCCESS message.</li>
 * </ol>
 *
 * <p>On "already exists" the flow retries with the next realistic name. If EVERY name is taken it stops and
 * reports that instead of creating a duplicate.</p>
 */
public class ReferralsCategory extends DevHisBase {

    public ReferralsCategory() { super("ApplicationConfig_Nursing_ReferralsCategory"); }

    public static void main(String[] args) {
        ReferralsCategory t = new ReferralsCategory();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - ReferralsCategory",
                "Application Configuration > Nursing > ReferralsCategory",
                "Add a Referrals Category: enter Code + Remark, Submit; wait for the success toast. "
                        + "On 'already exists' retry with a different name; never create a duplicate.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ReferralsCategory rc =
                new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.ReferralsCategory(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = rc.navigateViaMenu();
        String landed = rc.currentScreen();
        step(page, "Open ReferralsCategory screen", "Application Configuration -> Nursing -> ReferralsCategory",
                "The ReferralsCategory screen is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected ReferralsCategory but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        String added = rc.clickAddIfPresent();
        System.out.println("ReferralsCategory: " + added);

        String details = rc.fillDetails();
        boolean detOk = details.contains("Code=RC") && !details.contains("(no field)");
        step(page, "Enter Code and Remark", "Enter Code* (unique) and Remark*",
                "Code and Remark are entered",
                details + (detOk ? "" : " || form offered: " + rc.describeForm()), detOk ? "PASS" : "FAIL");

        String toast = rc.submitWithRetries();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual;
        if (rc.allNamesTaken) {
            // Do not force a duplicate — report it so a different screen can be chosen.
            actual = "Every referral category in the pool already exists — NOT creating a duplicate. "
                    + "The app said: \"" + toast + "\"";
        } else if (toast == null || toast.isEmpty()) {
            actual = "No toast appeared";
        } else {
            actual = ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"";
        }
        if (rc.toastPng != null && rc.toastPng.length > 0) {
            step(rc.toastPng, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Code", rc.lastCode);
        addSummary("Remark", rc.lastRemark);
        addSummary("Route", "#/ReferralsCategory");
        addSummary("Result", ok ? toast
                : (rc.allNamesTaken ? "All names already exist — no duplicate created"
                                    : "FAILED - " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\"")));
    }
}
