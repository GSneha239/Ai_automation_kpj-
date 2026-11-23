package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RowMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Row Master</b> ({@code #/RowMasterList}).
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>MRD</b> → <b>Row Master</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddRowMaster} → {@code #/add-RowMaster}).</li>
 *   <li>Enter <b>Row Code*</b> and <b>Remark*</b> — plus the mandatory <b>Rack*</b>, without which the save is
 *       rejected.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDRowMaster}); the toast must be a SUCCESS message — any other message
 *       fails the step.</li>
 * </ol>
 */
public class RowMaster extends DevHisBase {

    public RowMaster() { super("ApplicationConfig_MRD_RowMaster"); }

    public static void main(String[] args) {
        RowMaster t = new RowMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Row Master",
                "Application Configuration > MRD > Row Master",
                "Add a Row: click Add, pick the mandatory Rack and enter Row Code + Remark, Submit; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.RowMaster rm =
                new com.kpj.pages.ApplicationConfiguration_page.MRD_page.RowMaster(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = rm.navigateViaMenu();
        String landed = rm.currentScreen();
        step(page, "Open Row Master screen",
                "Application Configuration -> MRD -> Row Master",
                "The Row Master list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected Row Master but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        boolean addOk = rm.clickAdd() || rm.onAddForm();
        step(page, "Click Add", "Click Add (AddRowMaster) to open the entry form",
                "The Row Master entry form is shown",
                addOk ? "Add form opened - " + rm.currentScreen()
                      : "Add did NOT open an entry form - the app is showing: " + rm.currentScreen(),
                addOk ? "PASS" : "FAIL");
        if (!addOk) { addSummary("Result", "FAILED - Add did not open the entry form (" + rm.currentScreen() + ")"); return; }

        String details = rm.fillDetails();
        boolean detOk = details.contains("RowCode=RW") && !details.contains("(no field)")
                && !details.contains("Rack=(no-opt)") && !details.contains("model=?");
        step(page, "Enter Code and Remark", "Enter Row Code* (unique) and Remark*, plus the mandatory Rack*",
                "Row Code, Remark and Rack are entered", details, detOk ? "PASS" : "FAIL");

        String toast = rm.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\"");
        // Use the screenshot captured WHILE the toast was on screen — toastr fades before a step screenshot lands.
        if (rm.toastPng != null && rm.toastPng.length > 0) {
            step(rm.toastPng, "Click Submit & success toast", "Click Submit (fnIUDRowMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, "Click Submit & success toast", "Click Submit (fnIUDRowMaster); wait for the success toast",
                    "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }

        addSummary("Rack", rm.lastRack);
        addSummary("Row Code", rm.lastCode);
        addSummary("Remark", rm.lastRemark);
        addSummary("Route", "#/RowMasterList -> #/add-RowMaster");
        addSummary("Result", ok ? toast
                : "FAILED - the app did not show a success message: " + (toast == null || toast.isEmpty() ? "(no toast)" : "\"" + toast + "\""));
    }
}
