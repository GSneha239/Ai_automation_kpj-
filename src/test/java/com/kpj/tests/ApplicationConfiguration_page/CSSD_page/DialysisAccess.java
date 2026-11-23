package com.kpj.tests.ApplicationConfiguration_page.CSSD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named DialysisAccess — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Dialysis Configuration &gt; <b>Dialysis Access</b>.
 *
 * <ol>
 *   <li>Read the accesses already in the table (01 = Vascular Access).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next two-digit number: 02); enter a <b>Remark</b> — a real dialysis access that is not in the table (for example "Arteriovenous Fistula");
 *       click <b>Submit</b>. The form is inline on this screen: there is no Add button, and its optional MIMS fields are left alone.</li>
 *   <li>Verify the success toast, and that the new access now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and remark are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL DIALYSIS ACCESS into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class DialysisAccess extends DevHisBase {

    public DialysisAccess() { super("ApplicationConfiguration_Dialysis_DialysisAccess"); }

    public static void main(String[] args) {
        DialysisAccess t = new DialysisAccess();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Dialysis Configuration - Dialysis Access", "Application Configuration > Dialysis Configuration > Dialysis Access",
                "Read the existing accesses; enter a Code in the existing format; enter a suitable, unique Remark (not in the table); Submit; verify the success toast. SAVES A REAL DIALYSIS ACCESS.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisAccess da = new com.kpj.pages.ApplicationConfiguration_page.CSSD_page.DialysisAccess(page);

        boolean on = da.navigateViaMenu();
        step("Open Dialysis Access", "Click Application Configuration -> Dialysis Configuration -> Dialysis Access", "The Dialysis Access screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + da.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Dialysis Access not reached"); return; }

        List<String[]> rows = da.awaitExisting();
        boolean ready = da.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the accesses already in the table", "Read the Dialysis Access table (all pages)", "The existing codes and remarks are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + da.pagerText() + "\")" : rows.size() + " access(es) exist (pager: \"" + da.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = da.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = da.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — same format as the existing codes " + rows.stream().map(r -> r[0]).toList() + ", one above the largest" : "Problem — " + da.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + da.problems); return; }

            boolean remarkOk = da.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (different from every one of the " + rows.size() + " existing remarks)" : "Problem — " + da.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + da.problems); return; }

            toast = da.submitAndGetToast();
            ok = da.ok() && da.saveSucceeded();
            if (!ok && da.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The access is submitted", da.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + da.problems,
                da.problems.isEmpty() ? "PASS" : "FAIL");

        // the new access should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : da.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (da.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + da.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + da.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + da.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Accesses in the table before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
