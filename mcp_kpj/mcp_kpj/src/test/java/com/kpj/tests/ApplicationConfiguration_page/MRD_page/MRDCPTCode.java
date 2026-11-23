package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named MRDCPTCode — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>MRD CPT Code</b>.
 *
 * <ol>
 *   <li>Read the CPT codes already in the table (it was EMPTY when this test was written).</li>
 *   <li>Enter the <b>Code</b> — a real five-digit CPT code that is not in the table (for example 99213); enter the <b>Remark</b> — its plain-language descriptor (for example "Established patient
 *       office visit - low complexity"); click <b>Submit</b>. The form is inline on this screen: there is no Add button, and its optional MIMS fields are left alone.</li>
 *   <li>Verify the success toast, and that the new code now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused pair is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL CPT CODE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class MRDCPTCode extends DevHisBase {

    public MRDCPTCode() { super("ApplicationConfig_MRD_MRDCPTCode"); }

    public static void main(String[] args) {
        MRDCPTCode t = new MRDCPTCode();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - MRD CPT Code", "Application Configuration > MRD > MRD CPT Code",
                "Read the existing CPT codes; enter a real CPT Code not in the table; enter a suitable, unique Remark; Submit; verify the success toast. SAVES A REAL CPT CODE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.MRDCPTCode mc = new com.kpj.pages.ApplicationConfiguration_page.MRD_page.MRDCPTCode(page);

        boolean on = mc.navigateViaMenu();
        step("Open MRD CPT Code", "Click Application Configuration -> MRD -> MRD CPT Code", "The MRD CPT Code screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + mc.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — MRD CPT Code not reached"); return; }

        List<String[]> rows = mc.awaitExisting();
        boolean ready = mc.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1].trim()).toList().toString();
        step("Read the CPT codes already in the table", "Read the MRD CPT Code table (all pages)", "The existing codes and remarks are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + mc.pagerText() + "\") - there is no existing code or remark to differ from"
                        : rows.size() + " CPT code(s) exist (pager: \"" + mc.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = mc.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = mc.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a real CPT Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — a five-digit CPT code" + (rows.isEmpty() ? " (the table is empty, so there is no format to copy)" : ", different from the existing " + rows.stream().map(r -> r[0]).toList())
                           : "Problem — " + mc.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + mc.problems); return; }

            boolean remarkOk = mc.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (" + (rows.isEmpty() ? "the table holds no remarks yet, so it is unique" : "different from every one of the " + rows.size() + " existing remarks") + ")"
                             : "Problem — " + mc.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + mc.problems); return; }

            toast = mc.submitAndGetToast();
            ok = mc.ok() && mc.saveSucceeded();
            if (!ok && mc.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The CPT code is submitted", mc.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + mc.problems,
                mc.problems.isEmpty() ? "PASS" : "FAIL");

        // the new CPT code should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : mc.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1].trim() + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (mc.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + mc.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + mc.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + mc.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("CPT codes in the table before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
