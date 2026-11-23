package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named DischargeType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Discharge Type</b>.
 *
 * <ol>
 *   <li>Read the discharge types already in the table (AOR, DTH, DEC, HOME, OTHS).</li>
 *   <li>Enter the <b>Code</b> and the <b>Description</b> — a real discharge type that is not in the table, in the style of the existing rows (a short upper-case code and an upper-case description, for
 *       example {@code ABSC} = {@code ABSCONDED}); click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new discharge type now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL DISCHARGE TYPE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class DischargeType extends DevHisBase {

    public DischargeType() { super("ApplicationConfiguration_Admission_DischargeType"); }

    public static void main(String[] args) {
        DischargeType t = new DischargeType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Discharge Type", "Application Configuration > Admission > Discharge Type",
                "Read the existing discharge types; enter a new Code; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL DISCHARGE TYPE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeType dt = new com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeType(page);

        boolean on = dt.navigateViaMenu();
        step("Open Discharge Type", "Click Application Configuration -> Admission -> Discharge Type", "The Discharge Type screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + dt.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Discharge Type not reached"); return; }

        List<String[]> rows = dt.awaitExisting();
        boolean ready = dt.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the discharge types already in the table", "Read the Discharge Type table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + dt.pagerText() + "\")" : rows.size() + " discharge type(s) exist (pager: \"" + dt.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = dt.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = dt.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table, in the style of the existing ones (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (short upper-case code like the existing " + rows.stream().map(r -> r[0]).toList() + ")" : "Problem — " + dt.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + dt.problems); return; }

            boolean descOk = dt.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (different from every one of the " + rows.size() + " existing descriptions)" : "Problem — " + dt.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + dt.problems); return; }

            toast = dt.submitAndGetToast();
            ok = dt.ok() && dt.saveSucceeded();
            if (!ok && dt.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The discharge type is submitted", dt.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + dt.problems,
                dt.problems.isEmpty() ? "PASS" : "FAIL");

        // the new discharge type should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : dt.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(description)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dt.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dt.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dt.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Discharge types in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
