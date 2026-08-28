package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BedReleasedChecklist — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Released Checklist</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Bed Released Checklist</b>.</li>
 *   <li>Select any record in the table.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Tick the mandatory checkbox.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class BedReleasedChecklist extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BedReleasedChecklist() { super("ApplicationConfig_BedReleasedChecklist"); }

    public static void main(String[] args) {
        BedReleasedChecklist t = new BedReleasedChecklist();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Bed Released Checklist", "Application Configuration > Admission > Bed Released Checklist",
                "Add a Bed Released Checklist: select a record, Add, enter Code + Remark, tick the mandatory checkbox, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedReleasedChecklist brc =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedReleasedChecklist(page);

        // 1) Navigate
        brc.navigateViaMenu();
        boolean onScreen = brc.onScreen();
        step(page, "Open Bed Released Checklist screen", "Click Application Configuration -> Bed Released Checklist",
                "The Bed Released Checklist screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Select any record from the table
        String rec = brc.selectFirstRecord();
        boolean recOk = rec != null && !rec.isEmpty();
        step(page, "Select a record", "Tick any one record in the table",
                "A record is selected", recOk ? "Selected: " + rec : "No record selected", recOk ? "PASS" : "FAIL");

        // 3) Add
        boolean added = brc.clickAdd();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 4) Enter Code + Remark, tick the mandatory checkbox once, then Submit — retry with fresh details on
        // "already exists" (Code and/or Remark can collide with existing records).
        String fill = "", toast = "";
        boolean cbOk = false;
        String cb = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = brc.fillCodeAndRemark(attempt);
            used++;
            if (attempt == 0) {
                cb = brc.selectMandatoryCheckbox();
                cbOk = cb != null && !cb.isEmpty();
            }
            toast = brc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BedReleasedChecklist: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=RC") && !fill.contains("Remark=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // 5) Select the mandatory checkbox
        step(page, "Select the mandatory checkbox", "Tick the mandatory checkbox",
                "The mandatory checkbox is ticked", cbOk ? "Ticked: " + cb : "No checkbox ticked", cbOk ? "PASS" : "FAIL");

        // 6) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Checklist Code", brc.lastCode);
        addSummary("Remark", brc.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
