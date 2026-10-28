package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named TestType — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; CSSD Configuration &gt; <b>Test Type</b>.
 *
 * <ol>
 *   <li>Read the test types already in the table (TT1 Biological, TT2 Chemical, TT3 Physical, TT4 Packaging).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next number: TT5); enter a <b>Test Type</b> — a real CSSD sterilisation test that is not in the table (for example "Bowie-Dick"); click
 *       <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new test type now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and test type are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL TEST TYPE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class TestType extends DevHisBase {

    public TestType() { super("ApplicationConfiguration_CSSD_TestType"); }

    public static void main(String[] args) {
        TestType t = new TestType();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - CSSD Configuration - Test Type", "Application Configuration > CSSD Configuration > Test Type",
                "Read the existing test types; enter a Code in the existing format; enter a suitable, unique Test Type (not in the table); Submit; verify the success toast. SAVES A REAL TEST TYPE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.TestType tt = new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.TestType(page);

        boolean on = tt.navigateViaMenu();
        step("Open Test Type", "Click Application Configuration -> CSSD Configuration -> Test Type", "The Test Type screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Test Type not reached"); return; }

        List<String[]> rows = tt.awaitExisting();
        boolean ready = tt.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the test types already in the table", "Read the Test Type table (all pages)", "The existing codes and test types are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + tt.pagerText() + "\")" : rows.size() + " test type(s) exist (pager: \"" + tt.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", testType = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = tt.pickNew(rows, attempt);
            code = pick[0];
            testType = pick[1];
            boolean codeOk = tt.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — same format as the existing codes " + rows.stream().map(r -> r[0]).toList() + ", one above the largest" : "Problem — " + tt.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + tt.problems); return; }

            boolean typeOk = tt.enterTestType(testType);
            if (attempt == 0) step("Enter test type", "Enter a suitable, unique Test Type that is not in the table (" + testType + ")", "The test type is accepted",
                    typeOk ? "Test Type \"" + testType + "\" entered (different from every one of the " + rows.size() + " existing test types)" : "Problem — " + tt.problems, typeOk ? "PASS" : "FAIL");
            if (!typeOk) { addSummary("Result", "FAILED — test type: " + tt.problems); return; }

            toast = tt.submitAndGetToast();
            ok = tt.ok() && tt.saveSucceeded();
            if (!ok && tt.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + testType + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The test type is submitted", tt.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + tt.problems,
                tt.problems.isEmpty() ? "PASS" : "FAIL");

        // the new test type should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : tt.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(testType)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (tt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + tt.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + tt.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + tt.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Test types in the table before", String.valueOf(rows.size()));
        addSummary("Code / Test Type added", code + " / " + testType);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
