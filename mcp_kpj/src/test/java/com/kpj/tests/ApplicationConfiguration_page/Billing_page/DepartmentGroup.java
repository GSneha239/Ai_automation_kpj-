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
        boolean onScreen = dg.navigateViaMenu();
        if (!onScreen) addSummary("Menu offered", dg.lastMenu);
        step(page, "Open Department Group screen", "Click Application Configuration -> Billing -> Department Group",
                "The Department Group screen is shown",
                onScreen ? "Opened " + page.url() + (dg.lastRoute.isEmpty() ? "" : " (menu route " + dg.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // What is already assigned — read from the list while it is on screen, before Add leaves it.
        java.util.List<String> pairs = dg.readListPairs();
        java.util.Set<String> assignedDepts = new java.util.HashSet<>();
        for (String p : pairs) assignedDepts.add(p.substring(0, p.indexOf('\t')));
        if (pairs.isEmpty()) addSummary("List read", "no rows read — " + dg.lastListProbe);

        // 2) Add
        boolean added = dg.clickAdd();
        step(page, "Click Add", "Click Add", "The Department Group add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Select Department + Group and Submit. Department+Group is a unique combo and the environment already
        // holds most of them, so walk BOTH axes (Department x Group) — cycling Group against a single Department
        // only explores one row of the combination space and runs out.
        // The Department and Group lists are filled from the server AFTER the form opens; counting straight away once read
        // "1 x 57" (Departments not loaded yet) and the walk had nothing to select. Wait for both to fill (up to 15 s).
        for (int w = 0; w < 30 && (dg.departmentCount() < 2 || dg.groupCount() < 2); w++) page.waitForTimeout(500);
        int deptCount = Math.max(1, dg.departmentCount());
        int groupCount = Math.max(1, dg.groupCount());
        int maxAttempts = 40;
        // A Department can hold only ONE Group: once it is on the list, every further Group for it is refused with "Group Already
        // Assigned to the Department!" (two such Departments burned all 40 blind attempts of an earlier run). The list was read above,
        // so try only the Departments that are NOT on it yet, each with a Group chosen at random. If every Department already has a
        // Group, fall back to a short blind walk so the rule is re-confirmed rather than assumed.
        java.util.List<String> deptNames = dg.departmentNames();
        java.util.List<Integer> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < deptNames.size(); i++)
            if (!assignedDepts.contains(deptNames.get(i).replaceAll("\\s+", " ").trim().toUpperCase())) candidates.add(i + 1);
        boolean noFreeDept = candidates.isEmpty();
        addSummary("Rows on the list", pairs.size() + " Department+Group rows, " + assignedDepts.size() + " distinct Departments");
        addSummary("Departments without a Group", candidates.size() + " of " + deptNames.size());
        java.util.Random rnd = new java.util.Random();
        int startD = rnd.nextInt(deptCount), startG = rnd.nextInt(groupCount);
        if (noFreeDept) { maxAttempts = 6; }
        else { java.util.Collections.shuffle(candidates, rnd); maxAttempts = Math.min(maxAttempts, candidates.size()); }
        System.out.println("DepartmentGroup: " + candidates.size() + " Department(s) of " + deptNames.size() + " have no Group yet"
                + (noFreeDept ? " — none free, trying a short blind walk from Department #" + (startD + 1) + " / Group #" + (startG + 1) : ""));

        String dept = "", group = "", toast = "";
        boolean ok = false, exists = false, selStepDone = false;
        int used = 0, lastD = -1;
        for (int k = 0; k < maxAttempts; k++) {
            int g = noFreeDept ? (startG + k) % groupCount + 1 : rnd.nextInt(groupCount) + 1;   // 1-based Group ordinal
            int d = noFreeDept ? (startD + (startG + k) / groupCount) % deptCount + 1
                               : candidates.get(k);                                            // a Department with no Group yet
            if (d != lastD) {
                String dsel = dg.selectDepartmentOrdinal(d);
                // realSelectOrdinal()'s failure placeholders are exactly "(no-opt)", "(no)" and "(err)" — a
                // blanket "starts with (" check is too broad: the real Department master list includes an
                // entry literally named "(NAMA DR) MR C/N", and that false-failed here before Group was ever
                // selected or Submit ever clicked (0 combination attempts, no toast).
                if (isPlaceholder(dsel)) break;
                dept = dsel;
                lastD = d;
            }
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
            if (ok || !exists) break;
            System.out.println("DepartmentGroup: attempt " + used + " (Dept=" + dept + ", Group=" + group
                    + ") exists — trying the next combination");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? (noFreeDept ? "ENVIRONMENT DATA EXHAUSTED: all " + deptNames.size() + " Departments already have a Group ("
                                                        + assignedDepts.size() + " distinct Departments on the list), and a Department holds only one; "
                                                        + "a blind try of " + used + " combination(s) was also refused: \"" + toast + "\""
                                                      : "Still 'already exists' after trying " + used + " Department x Group combination(s): \"" + toast + "\"")
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
