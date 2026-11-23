package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named TransferCheckList — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Transfer CheckList</b>.
 *
 * <ol>
 *   <li>Read the checklist items that already exist in the table, so the new ones can differ from them.</li>
 *   <li>Click <b>Add</b>; enter the <b>Code</b> in the existing format (three digits, one above the largest: 003 after 001 and 002); enter the <b>Remark</b> — a real patient-transfer checklist item
 *       (for example "Receiving ward or unit informed of the transfer") that is not in the table; click
 *       <b>Submit</b>. The Mandatory tick is left as it is.</li>
 *   <li>Verify the success toast, and that the new item now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused remark is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL CHECKLIST ITEM into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class TransferCheckList extends DevHisBase {

    public TransferCheckList() { super("ApplicationConfiguration_Admission_TransferCheckList"); }

    public static void main(String[] args) {
        TransferCheckList t = new TransferCheckList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Transfer CheckList", "Application Configuration > Admission > Transfer CheckList",
                "Read the existing checklist items; Add; enter a new Code; enter a new, suitable Remark (not in the table); Submit; verify the success toast. SAVES A REAL CHECKLIST ITEM.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.TransferCheckList ac = new com.kpj.pages.ApplicationConfiguration_page.Admission_page.TransferCheckList(page);

        boolean on = ac.navigateViaMenu();
        step("Open Transfer CheckList", "Click Application Configuration -> Admission -> Transfer CheckList", "The Transfer CheckList list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Transfer CheckList not reached"); return; }

        List<String[]> rows = ac.awaitExisting();
        boolean haveList = !rows.isEmpty();
        String sample = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the checklist items already in the table", "Read the Transfer CheckList table (all pages)", "The existing codes and remarks are known",
                haveList ? rows.size() + " item(s) exist (pager: \"" + ac.pagerText() + "\"): " + sample : "The table listed no rows", haveList ? "PASS" : "FAIL");
        if (!haveList) { addSummary("Result", "FAILED — the existing checklist items could not be read"); return; }

        boolean opened = ac.clickAdd();
        step("Click Add", "Click Add", "The Transfer CheckList form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + ac.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + ac.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = ac.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = ac.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (not among the " + rows.size() + " existing codes)" : "Problem — " + ac.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { if (attempt != 0) step("Enter code (retry)", "Enter the Code " + code, "The code is accepted", "Problem — " + ac.problems, "FAIL"); addSummary("Result", "FAILED — code: " + ac.problems); return; }
            boolean remarkOk = ac.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (different from every one of the " + rows.size() + " existing remarks)" : "Problem — " + ac.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { if (attempt != 0) step("Enter remark (retry)", "Enter the Remark " + remark, "The remark is accepted", "Problem — " + ac.problems, "FAIL"); addSummary("Result", "FAILED — remark: " + ac.problems); return; }

            toast = ac.submitAndGetToast();
            ok = ac.ok() && ac.saveSucceeded();
            if (!ok && ac.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The checklist item is submitted", ac.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + ac.problems,
                ac.problems.isEmpty() ? "PASS" : "FAIL");

        // the new item should now be in the list (the screen returns to it after a save)
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : ac.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (ac.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + ac.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + ac.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + ac.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Existing items before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
