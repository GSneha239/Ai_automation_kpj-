package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DischargeCheckList — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Discharge CheckList</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Discharge CheckList</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Tick the <b>Mandatory</b> checkbox.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class DischargeCheckList extends DevHisBase {

    public DischargeCheckList() { super("ApplicationConfig_Admission_DischargeCheckList"); }

    public static void main(String[] args) {
        DischargeCheckList t = new DischargeCheckList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Discharge CheckList", "Application Configuration > Admission > Discharge CheckList",
                "Add a Discharge CheckList: Add, enter Code + Remark, tick the Mandatory checkbox, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeCheckList dcl =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeCheckList(page);

        // 1) Navigate
        dcl.navigateViaMenu();
        boolean onScreen = dcl.onScreen();
        step(page, "Open Discharge CheckList screen", "Click Application Configuration -> Admission -> Discharge CheckList",
                "The Discharge CheckList screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = dcl.clickAdd() && dcl.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add form did not open"); return; }

        // 3) Enter Code + Remark
        String fill = dcl.fillCodeAndRemark();
        boolean fillOk = fill != null && fill.contains("Code=DC") && !fill.contains("Remark=(no");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fill, fillOk ? "PASS" : "FAIL");

        // 4) Select the mandatory checkbox
        String cb = dcl.selectMandatoryCheckbox();
        boolean cbOk = cb != null && !cb.isEmpty();
        step(page, "Select the Mandatory checkbox", "Tick the Mandatory checkbox",
                "The Mandatory checkbox is ticked", cbOk ? "Ticked: " + cb : "No checkbox ticked", cbOk ? "PASS" : "FAIL");

        // 5) Submit — the record must really be created (the list grid is paged, so verify via the master API).
        String toast = dcl.submitAndGetToast();
        String saved = dcl.fetchSavedRow(dcl.lastCode);
        boolean savedOk = saved != null && saved.startsWith("id=");
        step(page, "Click Submit", "Click Submit; verify the new Discharge CheckList row exists",
                "The Discharge CheckList row is created (Code, Remark, Mandatory = true)",
                savedOk ? "Saved: " + saved : "Row NOT created (" + dcl.lastCode + ")", savedOk ? "PASS" : "FAIL");

        // 6) Success toast.
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Error message: \"" + toast + "\"";
        if (!ok && savedOk) actual += " instead of the success message — the save API itself returned "
                + dcl.lastSaveApi;
        step(dcl.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Discharge Check List Master details added successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Checklist Code", dcl.lastCode);
        addSummary("Remark", dcl.lastRemark);
        addSummary("Saved row", savedOk ? saved : "not found");
        addSummary("Result", ok ? toast : "FAILED — no success message; the screen showed \"" + toast + "\"");
    }
}
