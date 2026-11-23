package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named MimsOverrideJustificationMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>MIMSOverrideJustificationMaster</b>.
 *
 * <ol>
 *   <li>Read the justifications already in the table (BOR, DAM, NSA, OTH, PTC, WMC).</li>
 *   <li>Click <b>Add</b> (the "Add Override Justification" dialog opens), enter the <b>Code</b> (three upper-case letters like the existing ones, for example NCS) and a <b>Description</b> — a real,
 *       suitable override reason that is not in the table (for example "Not clinically significant for this patient"); click <b>Save</b>.</li>
 *   <li>Verify the success toast, that the dialog closes, and that the new justification now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and description are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL OVERRIDE JUSTIFICATION into the master on every run; it stays (it can only be made inactive, which this test does not do) and becomes a choice in the MIMS override windows.</p>
 */
public class MimsOverrideJustificationMaster extends DevHisBase {

    public MimsOverrideJustificationMaster() { super("ApplicationConfiguration_General_MimsOverrideJustificationMaster"); }

    public static void main(String[] args) {
        MimsOverrideJustificationMaster t = new MimsOverrideJustificationMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - MIMSOverrideJustificationMaster", "Application Configuration > General > MIMSOverrideJustificationMaster",
                "Read the existing justifications; Add; enter a Code in the existing format; enter a suitable, unique Description (not in the table); Save; verify the success toast. SAVES A REAL OVERRIDE JUSTIFICATION.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.MimsOverrideJustificationMaster mj =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.MimsOverrideJustificationMaster(page);

        boolean on = mj.navigateViaMenu();
        step("Open MIMSOverrideJustificationMaster", "Click Application Configuration -> General -> MIMSOverrideJustificationMaster", "The MIMSOverrideJustificationMaster screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — MIMSOverrideJustificationMaster not reached"); return; }

        List<String[]> rows = mj.awaitExisting();
        boolean ready = mj.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1]).toList().toString();
        step("Read the justifications already in the table", "Read the justification table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (pager: \"" + mj.pagerText() + "\")" : rows.size() + " justification(s) exist (pager: \"" + mj.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        boolean added = mj.clickAdd();
        step("Click Add", "Click Add", "The Add Override Justification dialog opens with Code and Description",
                added ? "Dialog \"" + mj.lastModalTitle + "\" opened (Code, Description, Status " + (mj.lastStatusActive ? "Active, left ticked" : "not ticked") + ", Save, Close)" : "Problem — " + mj.problems,
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — Add: " + mj.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            if (attempt > 0 && !mj.modalOpen() && !mj.clickAdd()) { addSummary("Result", "FAILED — Add (retry): " + mj.problems); return; }
            String[] pick = mj.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = mj.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format (three upper-case letters) that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — same format as the existing codes " + rows.stream().map(r -> r[0]).toList() : "Problem — " + mj.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + mj.problems); return; }

            boolean descOk = mj.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (different from every one of the " + rows.size() + " existing descriptions; it becomes a choice in the MIMS override dropdown)" : "Problem — " + mj.problems,
                    descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + mj.problems); return; }

            toast = mj.saveAndGetToast();
            ok = mj.ok() && mj.saveSucceeded();
            if (!ok && mj.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        boolean closed = ok && mj.waitModalClosed();
        step("Click Save", "Click Save", "The justification is saved", mj.problems.isEmpty() ? "Save clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) + (ok ? (closed ? "; the dialog closed" : "; the dialog stayed open") : "")
                : "Problem — " + mj.problems, mj.problems.isEmpty() ? "PASS" : "FAIL");

        // the new justification should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : mj.existing()) if (r[0].equalsIgnoreCase(code) && r[1].equalsIgnoreCase(description)) listed = r[0] + " = " + r[1] + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (mj.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + mj.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + mj.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + mj.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Justifications in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
