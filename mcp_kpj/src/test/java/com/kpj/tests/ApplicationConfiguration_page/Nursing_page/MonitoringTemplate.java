package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named MonitoringTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>Monitoring Template</b>.
 *
 * <ol>
 *   <li>Read the templates already in the list (123 = manual tester, C001 = TEST) and click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> (MT001), a <b>Remark</b> that is not in the list — a real monitoring chart (for example "Routine Vital Signs Chart") — and its <b>Print Name</b>.</li>
 *   <li>Tick the checkbox of the Monitoring <b>Group</b> the chart belongs to (for example "Vital Signs"), click <b>Save</b>.</li>
 *   <li>Verify the success toast, and that the new template is in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused chart is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL MONITORING TEMPLATE into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class MonitoringTemplate extends DevHisBase {

    public MonitoringTemplate() { super("ApplicationConfig_Nursing_MonitoringTemplate"); }

    public static void main(String[] args) {
        MonitoringTemplate t = new MonitoringTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - Monitoring Template", "Application Configuration > Nursing > Monitoring Template",
                "Read the existing templates; Add; enter a code, a suitable unique remark (not in the list) and a print name; tick the group; Save; verify the success toast. SAVES A REAL MONITORING TEMPLATE.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MonitoringTemplate mt = new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.MonitoringTemplate(page);

        boolean on = mt.navigateViaMenu();
        step("Open Monitoring Template", "Click Application Configuration -> Nursing -> Monitoring Template", "The Monitoring Template screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Monitoring Template not reached"); return; }

        List<String[]> rows = mt.awaitExisting();
        boolean ready = mt.gridReady();
        step("Read the templates already in the list", "Read the Monitoring Template list (all pages)", "The existing codes and remarks are known",
                !ready ? "The list did not load" : rows.isEmpty() ? "The list is empty (pager: \"" + mt.pagerText() + "\")"
                        : rows.size() + " template(s) exist (pager: \"" + mt.pagerText() + "\"): " + rows.stream().map(r -> r[0] + " = " + r[1].trim() + " / " + r[2].trim()).toList(),
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the list did not load"); return; }

        boolean opened = mt.clickAdd();
        List<String> groups = opened ? mt.groupNames() : List.of();
        step("Click Add", "Click Add", "The Monitoring Template form opens",
                opened ? "Form opened (" + page.url() + "): Code, Remark, Print Name and a Group table of " + groups.size() + " group(s): " + groups : "Problem — " + mt.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — Add: " + mt.problems); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", print = "", group = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = mt.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            print = pick[2];
            boolean codeOk = mt.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the list (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (the existing codes " + rows.stream().map(r -> r[0]).toList() + " share no format, so this starts the MT series, like MG001 of the Monitoring Group screen)"
                           : "Problem — " + mt.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + mt.problems); return; }

            boolean remarkOk = mt.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the list (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (different from every one of the " + rows.size() + " existing remarks)" : "Problem — " + mt.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + mt.problems); return; }

            boolean printOk = mt.enterPrintName(print);
            if (attempt == 0) step("Enter print name", "Enter the Print Name (" + print + ")", "The print name is accepted", printOk ? "Print Name \"" + print + "\" entered" : "Problem — " + mt.problems, printOk ? "PASS" : "FAIL");
            if (!printOk) { addSummary("Result", "FAILED — print name: " + mt.problems); return; }

            String ticked = mt.tickGroup(pick[3]);
            if (attempt == 0) step("Tick the checkbox for the group", "Tick the checkbox of the group the chart belongs to (" + pick[3] + ")", "The group is ticked",
                    !ticked.isEmpty() ? "Group \"" + ticked + "\" ticked" + (ticked.equals(pick[3]) ? "" : " (\"" + pick[3] + "\" is not offered, so the first group)") + "; ticked now: " + mt.tickedGroups() : "Problem — " + mt.problems,
                    !ticked.isEmpty() ? "PASS" : "FAIL");
            if (ticked.isEmpty()) { addSummary("Result", "FAILED — group: " + mt.problems); return; }
            group = ticked;

            toast = mt.saveAndGetToast();
            ok = mt.ok() && mt.saveSucceeded();
            if (!ok && mt.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Save", "Click Save", "The template is saved",
                mt.problems.isEmpty() ? "Save clicked" + (mt.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + mt.lastDialog + "\"") + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried)
                        : "Problem — " + mt.problems, mt.problems.isEmpty() ? "PASS" : "FAIL");

        // the screen returns to the list after a save: the new template should be in it
        String listed = "";
        boolean back = false;
        boolean savedByServer = mt.lastSaveStatus == 1;                          // the server's own answer, whatever toast the screen shows
        if (ok || savedByServer) {
            back = mt.returnToList();
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : mt.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(remark)) listed = r[0] + " = " + r[1].trim() + " / " + r[2].trim() + " (status " + r[3] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        // RULE (the user's): only whether a SUCCESS toast appears is checked. The screen shows a green success toast with the placeholder text "Message Not Found." - that is a pass.
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because a success toast appeared (style: " + mt.lastToastKinds + "): \"" + toast + "\""
                        + (mt.toastTextIsSuccess() ? "" : " - its wording is the app's placeholder rather than a success sentence, but it is a success toast, which is what is checked")
                        + (mt.lastSaveMessage.isEmpty() ? "" : "; the server answered \"" + mt.lastSaveMessage + "\"")
                        + (back ? "; back on the list" : "; the list was not reached") + (listed.isEmpty() ? "; the new template was not found in the list afterwards" : "; the list now shows " + listed)
                   : savedByServer ? "FAILS because no success toast appeared (toast shown on screen: \"" + toast + "\", style: " + (mt.lastToastKinds.isEmpty() ? "unknown" : mt.lastToastKinds) + ") - although the SERVER SAVED the template (ResultStatus 1, \"" + mt.lastSaveMessage.trim() + "\")"
                           + (back ? "; back on the list" : "; the list was not reached") + (listed.isEmpty() ? "; the new template was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + mt.saveDiagnosis()
                                     : "FAILS because the toast is not a success toast (style: " + (mt.lastToastKinds.isEmpty() ? "unknown" : mt.lastToastKinds) + "): \"" + toast + "\" — " + mt.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Templates in the list before", String.valueOf(rows.size()));
        addSummary("Code / Remark / Print Name added", code + " / " + remark + " / " + print);
        addSummary("Group ticked", group);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + mt.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast + (mt.lastToastKinds.isEmpty() ? "" : " (style: " + mt.lastToastKinds + ")"));
        addSummary("Result", ok ? "PASS - a success toast appeared (\"" + toast + "\")" : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but no success toast: \"" + toast + "\"" : toast)));
    }
}
