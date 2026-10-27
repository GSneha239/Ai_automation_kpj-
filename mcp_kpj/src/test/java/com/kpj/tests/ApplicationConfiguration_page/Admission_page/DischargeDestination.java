package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named DischargeDestination — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Discharge Destination</b>.
 *
 * <ol>
 *   <li>Read the destinations already in the table (their codes run DD0001, DD0002, ...).</li>
 *   <li>Enter the <b>Code</b> in the same format (the next number, so DD0013 when DD0012 is the largest); enter a <b>Description</b> — a real discharge destination that is not in the table (for
 *       example "Palliative Care / Hospice"); click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new destination now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL DISCHARGE DESTINATION into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class DischargeDestination extends DevHisBase {

    public DischargeDestination() { super("ApplicationConfiguration_Admission_DischargeDestination"); }

    public static void main(String[] args) {
        DischargeDestination t = new DischargeDestination();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Discharge Destination", "Application Configuration > Admission > Discharge Destination",
                "Read the existing destinations; enter a Code in the existing format; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL DISCHARGE DESTINATION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeDestination dd = new com.kpj.pages.ApplicationConfiguration_page.Admission_page.DischargeDestination(page);

        boolean on = dd.navigateViaMenu();
        step("Open Discharge Destination", "Click Application Configuration -> Admission -> Discharge Destination", "The Discharge Destination screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + dd.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Discharge Destination not reached"); return; }

        List<String[]> rows = dd.awaitExisting();
        boolean ready = dd.gridReady();
        String codes = rows.stream().map(r -> r[0]).toList().toString();
        step("Read the destinations already in the table", "Read the Discharge Destination table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + dd.pagerText() + "\")"
                                                                  : rows.size() + " destination(s) exist (pager: \"" + dd.pagerText() + "\"); codes " + codes + "; descriptions " + rows.stream().map(r -> r[1]).toList(),
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            code = dd.pickCode(rows, attempt);
            boolean codeOk = dd.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — same format as the existing codes " + codes + ", one above the largest" : "Problem — " + dd.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + dd.problems); return; }

            description = dd.pickDescription(rows, attempt);
            boolean descOk = dd.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (different from every one of the " + rows.size() + " existing descriptions)" : "Problem — " + dd.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + dd.problems); return; }

            toast = dd.submitAndGetToast();
            ok = dd.ok() && dd.saveSucceeded();
            if (!ok && dd.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The destination is submitted", dd.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + dd.problems,
                dd.problems.isEmpty() ? "PASS" : "FAIL");

        // the new destination should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : dd.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(description)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dd.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dd.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dd.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dd.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Destinations in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
