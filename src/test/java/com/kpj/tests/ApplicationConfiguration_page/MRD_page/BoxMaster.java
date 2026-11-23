package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BoxMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Box Master</b> ({@code #/BoxMasterList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>Box Master</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddBoxMaster} → {@code #/add-BoxMaster}).</li>
 *   <li>Pick <b>Row*</b> and enter <b>Box Code*</b> + <b>Remark*</b>.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDBoxMaster}); the toast must be a SUCCESS message — any other message
 *       fails the step.</li>
 * </ol>
 */
public class BoxMaster extends DevHisBase {

    public BoxMaster() { super("ApplicationConfig_MRD_BoxMaster"); }

    public static void main(String[] args) {
        BoxMaster t = new BoxMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Box Master",
                "Application Configuration > MRD > Box Master",
                "Add a Box: click Add, pick Row and enter Box Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.BoxMaster bm =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.BoxMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = bm.navigateViaMenu();
        String landed = bm.currentScreen();
        step(page, "Open Box Master screen",
                "Application Configuration -> MRD -> Box Master",
                "The Box Master list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Box Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = bm.clickAdd() || bm.onAddForm();
        step(page, "Click Add", "Click Add (AddBoxMaster) to open the entry form",
                "The Box Master entry form is shown",
                addOk ? "Add form opened - " + bm.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + bm.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + bm.currentScreen() + ")"); return; }

        String details = bm.fillDetails();
        boolean detOk = details.contains("BoxCode=BX") && !details.contains("(no field)")
                && !details.contains("Row=(no-opt)") && !details.contains("model=?");
        step(page, "Enter Row, Box Code and Remark",
                "Pick Row* (from the Row Master list) and enter Box Code* (unique) + Remark*",
                "Row, Box Code and Remark are entered", details, detOk ? "PASS" : "FAIL");

        String toast = bm.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (bm.toastPng != null && bm.toastPng.length > 0) {
            step(bm.toastPng, "Click Submit & success toast", "Click Submit (fnIUDBoxMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDBoxMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Row", bm.lastRow);
        addSummary("Box Code", bm.lastCode);
        addSummary("Remark", bm.lastRemark);
        addSummary("Route", "#/BoxMasterList -> #/add-BoxMaster");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
