package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named DepartmentGroup — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Department Group</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Department Group</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Department</b> and <b>Group</b> (there is no Code input on this screen).</li>
 *   <li>Click <b>Submit</b> → success toast (retry a different Group if it already exists).</li>
 *   <li>Check the list table for the added Department + Group row.</li>
 * </ol>
 */
public class DepartmentGroup extends DevHisBase {

    public DepartmentGroup() { super("ApplicationConfig_Billing_DepartmentGroup"); }

    public static void main(String[] args) {
        DepartmentGroup t = new DepartmentGroup();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Department Group", "Application Configuration > Billing > Department Group",
                "Add a Department Group: Add, select Department + Group, Submit (retry a different Group on 'already exists'), then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.DepartmentGroup dg =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.DepartmentGroup(page);

        // 1) Navigate
        dg.navigateViaMenu();
        boolean onScreen = dg.onScreen();
        step(page, "Open Department Group screen", "Click Application Configuration -> Billing -> Department Group",
                "The Department Group screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = dg.clickAdd();
        step(page, "Click Add", "Click Add", "The Department Group add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Select Department + Group and Submit. Department+Group is a unique combo and the environment already
        // holds most of them, so walk BOTH axes (Department x Group) — cycling Group against a single Department
        // only explores one row of the combination space and runs out.
        int deptCount = Math.max(1, dg.departmentCount());
        int groupCount = Math.max(1, dg.groupCount());
        int maxDepts = Math.min(deptCount, 12), maxGroups = Math.min(groupCount, 10), maxAttempts = 40;

        String dept = "", group = "", toast = "";
        boolean ok = false, exists = false, selStepDone = false;
        int used = 0;
        outer:
        for (int d = 1; d <= maxDepts; d++) {
            String dsel = dg.selectDepartmentOrdinal(d);
            // realSelectOrdinal()'s failure placeholders are exactly "(no-opt)", "(no)" and "(err)" — a
            // blanket "starts with (" check is too broad: the real Department master list includes an
            // entry literally named "(NAMA DR) MR C/N", and that false-failed here before Group was ever
            // selected or Submit ever clicked (0 combination attempts, no toast).
            if (isPlaceholder(dsel)) break;
            dept = dsel;
            for (int g = 1; g <= maxGroups; g++) {
                String gsel = dg.selectGroupOrdinal(g);
                if (isPlaceholder(gsel)) break;
                group = gsel;
                if (!selStepDone) {
                    boolean selOk = !isPlaceholder(dept) && !isPlaceholder(group);
                    step(page, "Select Department and Group", "Select a Department and a Group",
                            "Department and Group are selected", "Department=" + dept + " | Group=" + group,
                            selOk ? "PASS" : "FAIL");
                    selStepDone = true;
                }
                used++;
                toast = dg.submitAndGetToast();
                String tl = toast == null ? "" : toast.toLowerCase();
                ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
                exists = tl.contains("exist") || tl.contains("already");
                if (ok || !exists) break outer;
                System.out.println("DepartmentGroup: attempt " + used + " (Dept=" + dept + ", Group=" + group
                        + ") exists — trying the next combination");
                if (used >= maxAttempts) break outer;
            }
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after trying " + used + " Department x Group combination(s): \"" + toast + "\""
                                        : "Save not confirmed — server returned: \"" + toast + "\""));
        String submitStepTitle = "Click Submit & success toast (retry other Department x Group combos on 'exists')";
        String submitStepExpected = "Click Submit (fnIUDDepartmentGroup); if the combination already exists, try the next Department x Group combination and Submit again";
        String submitStepActual = (used > 1 && ok ? "(after " + used + " combination attempts, saved as Department=" + dept + " / Group=" + group + ") " : "") + actual;
        if (dg.toastPng != null && dg.toastPng.length > 0) {
            step(dg.toastPng, submitStepTitle, submitStepExpected, "'... saved successfully.' toast", submitStepActual, ok ? "PASS" : "FAIL");
        } else {
            step(page, submitStepTitle, submitStepExpected, "'... saved successfully.' toast", submitStepActual, ok ? "PASS" : "FAIL");
        }

        // 4) Check the table
        if (ok) {
            String row = dg.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Department Group list; find the row just added (Department + Group)",
                    "The newly added Department Group row is shown in the table",
                    found ? "Found in table: " + row
                          : "Saved (toast confirmed) but the new row was not located — Department=" + dg.lastDepartment + ", Group=" + dg.lastGroup,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Department", dg.lastDepartment);
        addSummary("Combination attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    /** True only for {@code realSelectOrdinal}'s actual failure placeholders — never a real option's text,
     *  even one (like "(NAMA DR) MR C/N") that happens to start with a literal "(". */
    private static boolean isPlaceholder(String v) {
        return v == null || v.equals("(no-opt)") || v.equals("(no)") || v.equals("(err)");
    }
}
