package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named InfectionTypeMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Infection Type Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Infection Type Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Field name</b>, <b>Control Binding</b>.</li>
 *   <li>Click <b>Add</b> (append the field row).</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class InfectionTypeMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public InfectionTypeMaster() { super("ApplicationConfig_InfectionTypeMaster"); }

    public static void main(String[] args) {
        InfectionTypeMaster t = new InfectionTypeMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Infection Type Master", "Application Configuration > Admission > Infection Type Master",
                "Add an Infection Type: Add, enter Code + Remark + Field name + Control Binding, Add the field row, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.InfectionTypeMaster itm =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.InfectionTypeMaster(page);

        // 1) Navigate
        itm.navigateViaMenu();
        boolean onScreen = itm.onScreen();
        step(page, "Open Infection Type Master screen", "Click Application Configuration -> Infection Type Master",
                "The Infection Type Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = itm.clickAdd();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter Code + Remark + Field name + Control Binding, add the field row, Submit — retry with fresh
        // details on "already exists" (Code and/or Remark can collide with existing records).
        String fill = "", addDetail = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = itm.fillDetails(attempt);
            used++;
            addDetail = itm.clickAddDetail();
            toast = itm.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("InfectionTypeMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=IT") && !fill.contains("Field=(no") && !fill.contains("Binding=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Field name, Control Binding", "Enter the Code, Remark, Field name and Control Binding",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        boolean addDetailOk = addDetail != null && !addDetail.isEmpty();
        step(page, "Click Add (append field row)", "Click Add to append the Field name/Control Binding row to the list",
                "The field row is added", addDetailOk ? "Add clicked (" + addDetail + ")" : "Detail Add not found",
                addDetailOk ? "PASS" : "FAIL");

        // 5) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Infection Type Code", itm.lastCode);
        addSummary("Remark", itm.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
