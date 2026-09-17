package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PayableType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Payable Type</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Payable Type</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> and <b>Remark</b>, tick <b>Refentity</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class PayableType extends DevHisBase {

    public PayableType() { super("ApplicationConfig_Location_PayableType"); }

    public static void main(String[] args) {
        PayableType t = new PayableType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Location - Payable Type",
                "Application Configuration > Location > Payable Type",
                "Add a payable type: Code, Remark, tick Refentity, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableType pt =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.PayableType(page);

        boolean onScreen = pt.navigateViaMenu();
        step(page, "Open Payable Type screen",
                "Click Application Configuration -> Location -> Payable Type",
                "The Payable Type screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + pt.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("payable type links => " + pt.findPayableTypeLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + pt.describeForm());

        boolean added = pt.clickAdd() && pt.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + pt.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + pt.describeForm());

        // Code, Remark, Refentity
        String filled = pt.fillAll(0);
        boolean filledOk = !pt.lastCode.isEmpty() && !pt.lastRemark.isEmpty() && pt.lastRefentity;
        step(page, "Enter Code and Remark, select Refentity", "Type the Code and Remark, tick Refentity",
                "All three are entered", filled, filledOk ? "PASS" : "FAIL");

        // Submit — retry with a different Code/Remark if this one already exists.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        for (int attempt = 0; attempt < 4 && !ok; attempt++) {
            toast = pt.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(pt.lastCode).append('/').append(pt.lastRemark).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            if (!pt.clickBack()) break;
            if (!(pt.clickAdd() && pt.addFormOpen())) break;
            String refilled = pt.fillAll(attempt + 1);
            System.out.println("Submit retry " + (attempt + 1) + ": " + refilled);
        }
        step(page, "Click Submit", "Click Submit", "The payable type is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (pt.lastSaveApi.isEmpty() ? "" : "  [" + pt.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(pt.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Payable Type Saved Successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Code", pt.lastCode);
        addSummary("Remark", pt.lastRemark);
        addSummary("Refentity", String.valueOf(pt.lastRefentity));
        addSummary("Save API", pt.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
