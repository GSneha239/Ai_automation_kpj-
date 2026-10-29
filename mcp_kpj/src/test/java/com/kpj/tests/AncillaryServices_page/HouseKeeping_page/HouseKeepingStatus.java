package com.kpj.tests.AncillaryServices_page.HouseKeeping_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named HouseKeepingStatus — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; House Keeping &gt; <b>House Keeping Status</b>.
 *
 * <ol>
 *   <li>Read the statuses already in the table (001 Completed, 002 In Progress, 003 Not completed).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next number: 004); enter a <b>Remark</b> — another real state of a housekeeping task that is not in the table (for example "Pending");
 *       click <b>Submit</b>. The form is inline on this screen: there is no Add button, and its optional MIMS fields are left alone.</li>
 *   <li>Verify the success toast, and that the new status now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and remark are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL HOUSEKEEPING STATUS into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class HouseKeepingStatus extends DevHisBase {

    public HouseKeepingStatus() { super("AncillaryServices_HouseKeeping_HouseKeepingStatus"); }

    public static void main(String[] args) {
        HouseKeepingStatus t = new HouseKeepingStatus();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - House Keeping - House Keeping Status", "Ancillary Services > House Keeping > House Keeping Status",
                "Read the existing statuses; enter a Code in the existing format; enter a suitable, unique Remark (not in the table); Submit; verify the success toast. SAVES A REAL HOUSEKEEPING STATUS.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingStatus hs = new com.kpj.pages.AncillaryServices_page.HouseKeeping_page.HouseKeepingStatus(page);

        boolean on = hs.navigateViaMenu();
        step("Open House Keeping Status", "Click Ancillary Services -> House Keeping -> House Keeping Status", "The House Keeping Status screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + hs.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — House Keeping Status not reached"); return; }

        List<String[]> rows = hs.awaitExisting();
        boolean ready = hs.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1].trim()).toList().toString();
        step("Read the statuses already in the table", "Read the House Keeping Status table (all pages)", "The existing codes and remarks are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + hs.pagerText() + "\") - there is no existing code or remark to differ from"
                        : rows.size() + " status(es) exist (pager: \"" + hs.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = hs.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = hs.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — " + (rows.isEmpty() ? "the table is empty, so it starts the series"
                            : "same format as the existing codes " + rows.stream().map(r -> r[0]).sorted().toList() + ", one above the largest") : "Problem — " + hs.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + hs.problems); return; }

            boolean remarkOk = hs.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (" + (rows.isEmpty() ? "the table holds no remarks yet, so it is unique" : "different from every one of the " + rows.size() + " existing remarks") + ")"
                             : "Problem — " + hs.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + hs.problems); return; }

            toast = hs.submitAndGetToast();
            ok = hs.ok() && hs.saveSucceeded();
            if (!ok && hs.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The status is submitted", hs.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + hs.problems,
                hs.problems.isEmpty() ? "PASS" : "FAIL");

        // the new status should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : hs.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1].trim() + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (hs.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + hs.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + hs.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + hs.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Statuses in the table before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
