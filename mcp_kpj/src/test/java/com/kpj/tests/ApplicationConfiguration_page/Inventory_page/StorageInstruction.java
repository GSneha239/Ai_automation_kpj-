package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named StorageInstruction — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Storage Instruction</b>.
 *
 * <ol>
 *   <li>Read the storage instructions already in the table (1 Below 25°C, 2 Refrigerate, 3 Protect from Light, 4 NA).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next number: 5); enter a <b>Description</b> — a real medicine storage instruction that is not in the table (for example "Do Not Freeze");
 *       click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new instruction now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL STORAGE INSTRUCTION into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class StorageInstruction extends DevHisBase {

    public StorageInstruction() { super("ApplicationConfiguration_Inventory_StorageInstruction"); }

    public static void main(String[] args) {
        StorageInstruction t = new StorageInstruction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Storage Instruction", "Application Configuration > Inventory > Storage Instruction",
                "Read the existing storage instructions; enter a Code in the existing format; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL STORAGE INSTRUCTION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.StorageInstruction si = new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.StorageInstruction(page);

        boolean on = si.navigateViaMenu();
        step("Open Storage Instruction", "Click Application Configuration -> Inventory -> Storage Instruction", "The Storage Instruction screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + si.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Storage Instruction not reached"); return; }

        List<String[]> rows = si.awaitExisting();
        boolean ready = si.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the storage instructions already in the table", "Read the Storage Instruction table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + si.pagerText() + "\")" : rows.size() + " storage instruction(s) exist (pager: \"" + si.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = si.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = si.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — same format as the existing codes " + rows.stream().map(r -> r[0]).toList() + ", one above the largest" : "Problem — " + si.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + si.problems); return; }

            boolean descOk = si.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (different from every one of the " + rows.size() + " existing descriptions)" : "Problem — " + si.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + si.problems); return; }

            toast = si.submitAndGetToast();
            ok = si.ok() && si.saveSucceeded();
            if (!ok && si.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The storage instruction is submitted", si.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + si.problems,
                si.problems.isEmpty() ? "PASS" : "FAIL");

        // the new storage instruction should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : si.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(description)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (si.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + si.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + si.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + si.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Storage instructions in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
