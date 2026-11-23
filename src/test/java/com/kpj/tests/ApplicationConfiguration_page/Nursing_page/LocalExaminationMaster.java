package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named LocalExaminationMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Local Examination Master</b>.
 *
 * <ol>
 *   <li>Select a <b>Group</b> (one that already has entries, because Parent is required and only such a group offers any: Bed, then Catherisation Laboratory).</li>
 *   <li>Enter the <b>Patient Complaints</b> (a real finding that is not among the group's entries, for example "Pressure sore over the sacrum") and select the <b>Parent</b>. The screen has no Add button.</li>
 *   <li>Click <b>Save</b> and verify the success toast.</li>
 *   <li>Verify that the new entry appears in the <b>Local Examination Details</b> table.</li>
 * </ol>
 *
 * <p>If the server still rejects the complaint as a duplicate, the next unused one is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL LOCAL EXAMINATION ENTRY into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class LocalExaminationMaster extends DevHisBase {

    public LocalExaminationMaster() { super("ApplicationConfig_Nursing_LocalExaminationMaster"); }

    public static void main(String[] args) {
        LocalExaminationMaster t = new LocalExaminationMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Local Examination Master", "Application Configuration > Nursing > Local Examination Master",
                "Select a group; enter the patient complaints (a suitable, unique finding) and select the parent; Save; verify the success toast; verify the entry appears in the Local Examination Details table. SAVES A REAL ENTRY.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.LocalExaminationMaster le = new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.LocalExaminationMaster(page);

        boolean on = le.navigateViaMenu();
        step("Open Local Examination Master", "Click Application Configuration -> Nursing -> Local Examination Master", "The Local Examination Master screen is shown",
                on ? "Opened " + page.url() + "; the Group dropdown offers " + le.groupOptions().size() + " group(s)" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Local Examination Master not reached"); return; }

        StringBuilder tried = new StringBuilder();
        String group = le.selectGroupWithParents(List.of("Bed", "Catherisation Laboratory"), 12, tried);
        List<String[]> parents = group.isEmpty() ? List.of() : le.parentOptions();
        final int entriesBefore = group.isEmpty() ? 0 : le.entries().size();                  // the group's entries before this run adds one
        step("Select group", "Select a Group (one that offers a Parent)", "A group is selected and its parents are listed",
                !group.isEmpty() ? "Group \"" + group + "\" selected; it holds " + entriesBefore + " entr(ies) and offers " + parents.size() + " Parent(s): " + parents.stream().map(p -> p[0]).toList()
                        + " (looked at: " + tried + ")" : "Problem — " + le.problems, !group.isEmpty() ? "PASS" : "FAIL");
        if (group.isEmpty()) { addSummary("Result", "FAILED — group: " + le.problems); return; }

        String toast = "";
        boolean ok = false;
        String complaint = "", parent = "", tried2 = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            complaint = le.pickComplaint(attempt);
            boolean typed = le.enterComplaint(complaint);
            if (attempt == 0) step("Enter patient complaints", "Enter the Patient Complaints, a finding that is not in the group's entries (" + complaint + ")", "The complaint is accepted",
                    typed ? "Patient Complaints \"" + complaint + "\" entered (different from every one of the group's " + entriesBefore + " existing entries)" : "Problem — " + le.problems, typed ? "PASS" : "FAIL");
            if (!typed) { addSummary("Result", "FAILED — patient complaints: " + le.problems); return; }

            String sel = le.selectParent("Test");
            if (attempt == 0) step("Select Parent", "Select the Parent", "The parent is selected",
                    !sel.isEmpty() ? "Parent \"" + sel + "\" selected (the root entry of the group; the other entries are test rows)" : "Problem — " + le.problems, !sel.isEmpty() ? "PASS" : "FAIL");
            if (sel.isEmpty()) { addSummary("Result", "FAILED — parent: " + le.problems); return; }
            parent = sel;

            toast = le.saveAndGetToast();
            ok = le.ok() && le.saveSucceeded();
            if (!ok && le.rejectedAsDuplicate() && attempt < 2) {
                tried2 += (tried2.isEmpty() ? "" : "; ") + complaint + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Save", "Click Save", "The entry is saved",
                le.problems.isEmpty() ? "Save clicked" + (le.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + le.lastDialog + "\"") + (tried2.isEmpty() ? "" : " — earlier attempt(s): " + tried2)
                        : "Problem — " + le.problems, le.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = le.savedByServer();
        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.LocalExaminationMaster.Entry entry = (ok || savedByServer) ? le.awaitEntry(complaint, parent, 15000) : null;
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (le.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + le.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the entry (ResultStatus 1, \"" + le.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + le.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + le.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // the data added should appear in the Local Examination Details table
        boolean shown = (ok || savedByServer) && le.awaitInDetailsPanel(complaint, 12000);
        String panel = le.detailsPanelText();
        // RULE (the user's): if the new data is not populated in the Local Examination Details table, or the table is blank, this step - and so the test - is a FAIL.
        step("Verify the entry appears in the Local Examination Details table", "Look for the new entry in the Local Examination Details table", "The new entry is listed in the table",
                shown ? "The table shows \"" + complaint + "\"" + (entry != null ? " (the screen's data holds it as id " + entry.id + " under \"" + le.nameOf(entry.parentId) + "\")" : "")
                      : "FAILS because the Local Examination Details table " + (panel.isEmpty() ? "is BLANK" : "does not show the new entry (it shows \"" + (panel.length() > 200 ? panel.substring(0, 200) + "..." : panel) + "\")") + ": \"" + complaint + "\" is not populated in it; "
                        + (entry != null ? "the screen's own data DOES hold it (id " + entry.id + ", group \"" + entry.group + "\", under \"" + le.nameOf(entry.parentId) + "\")" : "the screen's data does not hold it either")
                        + (entriesBefore > 0 ? "; the table showed nothing for the group's " + entriesBefore + " earlier entries either - app defect" : ""),
                shown ? "PASS" : "FAIL");

        addSummary("Group / Parent", group + " / " + parent);
        addSummary("Patient complaints added", complaint);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + le.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("In the screen's data afterwards", entry != null ? "id " + entry.id + " under \"" + le.nameOf(entry.parentId) + "\"" : "(not found)");
        addSummary("Local Examination Details table", shown ? "shows the new entry" : (panel.isEmpty() ? "BLANK - the new data is not populated" : "does not show the new entry"));
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok && shown ? toast : (!shown ? "FAIL - the data is not populated in the Local Examination Details table" + (panel.isEmpty() ? " (the table is blank)" : "") + (ok ? "; the save itself worked (\"" + toast + "\")" : "")
                : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast))));
    }
}
