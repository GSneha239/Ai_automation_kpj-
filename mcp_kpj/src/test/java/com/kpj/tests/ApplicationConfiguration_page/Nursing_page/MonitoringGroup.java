package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named MonitoringGroup — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Monitoring Group</b>.
 *
 * <ol>
 *   <li>Read the monitoring groups already in the table (it was EMPTY when this test was written).</li>
 *   <li>Click <b>Add</b> (the form opens), enter the <b>Code</b> (the table's format, or MG001 while it is empty) and a <b>Remark</b> — a real kind of monitoring group that is not in the table
 *       (for example "Vital Signs"); click <b>Submit</b>. The optional Print Name is left alone, and no Monitoring Service is ticked unless the app refuses the group without one.</li>
 *   <li>Verify the success toast, and that the new group now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused code and remark are tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL MONITORING GROUP into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class MonitoringGroup extends DevHisBase {

    public MonitoringGroup() { super("ApplicationConfig_Nursing_MonitoringGroup"); }

    public static void main(String[] args) {
        MonitoringGroup t = new MonitoringGroup();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Monitoring Group", "Application Configuration > Nursing > Monitoring Group",
                "Read the existing monitoring groups; Add; enter a Code in the table's format; enter a suitable, unique Remark (not in the table); Submit; verify the success toast. SAVES A REAL MONITORING GROUP.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MonitoringGroup mg = new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MonitoringGroup(page);

        boolean on = mg.navigateViaMenu();
        step("Open Monitoring Group", "Click Application Configuration -> Nursing -> Monitoring Group", "The Monitoring Group screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Monitoring Group not reached"); return; }

        List<String[]> rows = mg.awaitExisting();
        boolean ready = mg.gridReady();
        String existing = rows.stream().map(r -> r[0] + " = " + r[1].trim()).toList().toString();
        step("Read the monitoring groups already in the table", "Read the Monitoring Group table (all pages)", "The existing codes and remarks are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + mg.pagerText() + "\") - there is no existing code or remark to differ from"
                        : rows.size() + " monitoring group(s) exist (pager: \"" + mg.pagerText() + "\"): " + existing,
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        boolean opened = mg.clickAdd();
        List<String> services = opened ? mg.serviceNames() : List.of();
        step("Click Add", "Click Add", "The Monitoring Group form opens",
                opened ? "Form opened (" + page.url() + "): Code, Remark, Print Name and a Monitoring Service table of " + services.size() + " service(s)" : "Problem — " + mg.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + mg.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", tried = "", serviceNote = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = mg.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = mg.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the table's format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — " + (rows.isEmpty() ? "the table is empty, so it starts a new series (MG001)" : "same format as the existing codes " + rows.stream().map(r -> r[0]).sorted().toList() + ", one above the largest")
                           : "Problem — " + mg.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + mg.problems); return; }

            boolean remarkOk = mg.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (" + (rows.isEmpty() ? "the table holds no remarks yet, so it is unique" : "different from every one of the " + rows.size() + " existing remarks") + ")"
                             : "Problem — " + mg.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + mg.problems); return; }

            toast = mg.submitAndGetToast();
            ok = mg.ok() && mg.saveSucceeded();
            if (!ok && mg.needsService() && mg.lastTickedService.isEmpty()) {         // not in the written steps: only when the app insists on a service
                String first = toast;
                String ticked = mg.tickFirstRealService();
                if (!ticked.isEmpty()) {
                    serviceNote = "the app refused the group without a Monitoring Service (\"" + first + "\"), so \"" + ticked + "\" was ticked and Submit clicked again";
                    toast = mg.submitAndGetToast();
                    ok = mg.ok() && mg.saveSucceeded();
                }
            }
            if (!ok && mg.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The monitoring group is submitted",
                mg.problems.isEmpty() ? "Submit clicked" + (serviceNote.isEmpty() ? " with no Monitoring Service ticked (none was required)" : " — " + serviceNote) + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried)
                        : "Problem — " + mg.problems,
                mg.problems.isEmpty() ? "PASS" : "FAIL");

        // the screen returns to the list after a save: the new group should be in it
        String listed = "";
        boolean back = false;
        if (ok) {
            back = mg.returnToList();
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : mg.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1].trim() + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (mg.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + mg.lastSaveMessage + "\"")
                        + (back ? "; back on the list" : "; the list was not reached")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + mg.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + mg.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Monitoring groups in the table before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("Monitoring Service ticked", serviceNote.isEmpty() ? "(none - not required)" : mg.lastTickedService);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
