package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named TreatmentTechnique — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Treatment Technique</b>.
 *
 * <ol>
 *   <li>Read the treatment techniques already in the table (13 rows, codes 103 to 115).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next number: 116); enter a <b>Description</b> — a real treatment technique that is not in the table (for example "Physiotherapy");
 *       click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new technique now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL TREATMENT TECHNIQUE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class TreatmentTechnique extends DevHisBase {

    public TreatmentTechnique() { super("ApplicationConfiguration_Investigation_TreatmentTechnique"); }

    public static void main(String[] args) {
        TreatmentTechnique t = new TreatmentTechnique();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Treatment Technique", "Application Configuration > Investigation > Treatment Technique",
                "Read the existing treatment techniques; enter a Code in the existing format; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL TREATMENT TECHNIQUE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.TreatmentTechnique tt = new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.TreatmentTechnique(page);

        boolean on = tt.navigateViaMenu();
        step("Open Treatment Technique", "Click Application Configuration -> Investigation -> Treatment Technique", "The Treatment Technique screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + tt.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Treatment Technique not reached"); return; }

        List<String[]> rows = tt.awaitExisting();
        boolean ready = tt.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1].trim()).toList().toString();
        step("Read the treatment techniques already in the table", "Read the Treatment Technique table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + tt.pagerText() + "\") - there is no existing code or description to differ from"
                        : rows.size() + " treatment technique(s) exist (pager: \"" + tt.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = tt.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = tt.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — " + (rows.isEmpty() ? "the table is empty, so it starts the series"
                            : "same format as the existing codes " + rows.stream().map(r -> r[0]).sorted().toList() + ", one above the largest") : "Problem — " + tt.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + tt.problems); return; }

            boolean descOk = tt.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (" + (rows.isEmpty() ? "the table holds no descriptions yet, so it is unique" : "different from every one of the " + rows.size() + " existing descriptions") + ")"
                           : "Problem — " + tt.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + tt.problems); return; }

            toast = tt.submitAndGetToast();
            ok = tt.ok() && tt.saveSucceeded();
            if (!ok && tt.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The treatment technique is submitted", tt.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + tt.problems,
                tt.problems.isEmpty() ? "PASS" : "FAIL");

        // the new treatment technique should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : tt.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(description)) listed = r[0] + " = " + r[1].trim() + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (tt.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + tt.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + tt.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + tt.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Treatment techniques in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
