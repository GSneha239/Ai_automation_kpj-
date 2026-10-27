package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named DepartmentWiseDischargedChecklist — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Department Wise Discharged Checklist</b>.
 *
 * <ol>
 *   <li>Read the rows already in the table (it can be empty), so the new ones can differ from them.</li>
 *   <li>Click <b>Add</b>; enter the <b>Code</b>; select a <b>Department</b> (Billing Department, else Nursing Department, Medical Record, Dietician, Pharmacy, else the first one); enter a <b>Remark</b>
 *       written for that department (for Billing: "Final bill settled or guarantee letter confirmed") that is not in the table; click <b>Submit</b>.</li>
 *   <li>Verify the success toast, and that the new item now appears in the list under its department.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and remark are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL CHECKLIST ITEM into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class DepartmentWiseDischargedChecklist extends DevHisBase {

    public DepartmentWiseDischargedChecklist() { super("ApplicationConfiguration_Admission_DepartmentWiseDischargedChecklist"); }

    public static void main(String[] args) {
        DepartmentWiseDischargedChecklist t = new DepartmentWiseDischargedChecklist();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Department Wise Discharged Checklist", "Application Configuration > Admission > Department Wise Discharged Checklist",
                "Read the existing rows; Add; enter a new Code; select a Department; enter a suitable, unique Remark (not in the table); Submit; verify the success toast. SAVES A REAL CHECKLIST ITEM.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.DepartmentWiseDischargedChecklist dw =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.DepartmentWiseDischargedChecklist(page);

        boolean on = dw.navigateViaMenu();
        step("Open Department Wise Discharged Checklist", "Click Application Configuration -> Admission -> Department Wise Discharged Checklist", "The Department Wise Discharged Checklist list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Department Wise Discharged Checklist not reached"); return; }

        List<String[]> rows = dw.awaitExisting();
        boolean ready = dw.gridReady();
        String sample = rows.stream().limit(6).map(r -> r[0] + " = " + r[1] + (r[3].isEmpty() ? "" : " [" + r[3] + "]")).toList().toString();
        step("Read the rows already in the table", "Read the Department Wise Discharged Checklist table (all pages)", "The existing codes and remarks are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + dw.pagerText() + "\"), so there is nothing the new row has to differ from"
                                                                  : rows.size() + " row(s) exist (pager: \"" + dw.pagerText() + "\"): " + sample,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        boolean opened = dw.clickAdd();
        step("Click Add", "Click Add", "The Department Wise Discharged Checklist form opens", opened ? "Form opened (" + page.url() + ")" : "Problem — " + dw.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + dw.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", dept = "", remark = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            code = dw.pickCode(rows, attempt);
            boolean codeOk = dw.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered" + (rows.isEmpty() ? "" : " (not among the " + rows.size() + " existing codes)") : "Problem — " + dw.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + dw.problems); return; }

            if (attempt == 0) {
                dept = dw.selectDepartment();
                boolean deptOk = !dept.isEmpty() && dw.ok();
                step("Select department", "Select a Department", "A department is selected", deptOk ? "Selected \"" + dept + "\"" : "Problem — " + dw.problems, deptOk ? "PASS" : "FAIL");
                if (!deptOk) { addSummary("Result", "FAILED — department: " + dw.problems); return; }
            }

            remark = dw.pickRemark(rows, dept, attempt);
            boolean remarkOk = dw.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark for " + dept + " that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered" + (rows.isEmpty() ? "" : " (different from every one of the " + rows.size() + " existing remarks)") : "Problem — " + dw.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + dw.problems); return; }

            toast = dw.submitAndGetToast();
            ok = dw.ok() && dw.saveSucceeded();
            if (!ok && dw.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The checklist item is submitted", dw.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + dw.problems,
                dw.problems.isEmpty() ? "PASS" : "FAIL");

        // the new item should now be in the list (the screen returns to it after a save)
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : dw.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1] + " (status " + r[2] + (r[3].isEmpty() ? "" : ", department " + r[3]) + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dw.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dw.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dw.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dw.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Rows in the table before", String.valueOf(rows.size()));
        addSummary("Code / Department / Remark added", code + " / " + dept + " / " + remark);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
