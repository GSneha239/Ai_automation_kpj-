package com.kpj.tests.ApplicationConfiguration_page.Investigation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named Specimen — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Investigation &gt; <b>Specimen</b>.
 *
 * <ol>
 *   <li>Read the specimens already in the table (it was EMPTY when this test was written).</li>
 *   <li>Enter the <b>Code</b> (the next code in the table's format, or SP001 while the table is empty); enter a <b>Description</b> — a real specimen a lab receives that is not in the table (for example
 *       "Tissue Biopsy"); click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new specimen now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL SPECIMEN into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class Specimen extends DevHisBase {

    public Specimen() { super("ApplicationConfiguration_Investigation_Specimen"); }

    public static void main(String[] args) {
        Specimen t = new Specimen();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Investigation - Specimen", "Application Configuration > Investigation > Specimen",
                "Read the existing specimens; enter a Code in the table's format; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL SPECIMEN.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Specimen sp = new com.kpj.pages.ApplicationConfiguration_page.Investigation_page.Specimen(page);

        boolean on = sp.navigateViaMenu();
        step("Open Specimen", "Click Application Configuration -> Investigation -> Specimen", "The Specimen screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + sp.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Specimen not reached"); return; }

        List<String[]> rows = sp.awaitExisting();
        boolean ready = sp.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the specimens already in the table", "Read the Specimen table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + sp.pagerText() + "\") - there is no existing code or description to differ from"
                        : rows.size() + " specimen(s) exist (pager: \"" + sp.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = sp.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = sp.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — " + (rows.isEmpty() ? "the table is empty, so it starts a new series in the style of the sibling masters (two letters + three digits)"
                            : "same format as the existing codes " + rows.stream().map(r -> r[0]).toList() + ", one above the largest") : "Problem — " + sp.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + sp.problems); return; }

            boolean descOk = sp.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (" + (rows.isEmpty() ? "the table holds no descriptions yet, so it is unique" : "different from every one of the " + rows.size() + " existing descriptions") + ")"
                           : "Problem — " + sp.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + sp.problems); return; }

            toast = sp.submitAndGetToast();
            ok = sp.ok() && sp.saveSucceeded();
            if (!ok && sp.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The specimen is submitted", sp.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + sp.problems,
                sp.problems.isEmpty() ? "PASS" : "FAIL");

        // the new specimen should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : sp.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(description)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (sp.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + sp.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + sp.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + sp.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Specimens in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
