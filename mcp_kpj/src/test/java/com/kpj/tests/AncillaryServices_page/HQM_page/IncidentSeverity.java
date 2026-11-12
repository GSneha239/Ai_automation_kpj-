package com.kpj.tests.AncillaryServices_page.HQM_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

// The page object is also named IncidentSeverity — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; HQM &gt; <b>Incident Severity</b> ({@code #/INCSEV}).
 *
 * <ol>
 *   <li>Read the severity levels already in the table (000 N/A, 001 Low, 002 Moderate, 003 High, 004 Extreme / Very significant).</li>
 *   <li>Enter the <b>Code</b> (one above the largest: 005), a <b>Remark</b> that is not among them (a real severity level such as "Catastrophic / Sentinel event"), a <b>MIMS GUID</b> (a fresh GUID), a
 *       <b>MIMS Description</b> and select a <b>MIMS Type</b>. The screen has no Add button: the form is always open.</li>
 *   <li>Click <b>Submit</b>, verify the success toast, and verify that the new row is populated in the table.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused one is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL SEVERITY LEVEL into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class IncidentSeverity extends DevHisBase {

    public IncidentSeverity() { super("AncillaryServices_HQM_IncidentSeverity"); }

    public static void main(String[] args) {
        IncidentSeverity t = new IncidentSeverity();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - HQM - Incident Severity", "Ancillary Services > HQM > Incident Severity",
                "Read the severity levels; enter a code, a unique remark, a MIMS GUID, a MIMS description and a MIMS type; Submit; verify the success toast; verify the new row is populated in the table. SAVES A REAL SEVERITY LEVEL.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.HQM_page.IncidentSeverity is = new com.kpj.pages.AncillaryServices_page.HQM_page.IncidentSeverity(page);

        boolean on = is.navigateViaMenu();
        step("Open Incident Severity", "Click Ancillary Services -> HQM -> Incident Severity", "The Incident Severity screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Incident Severity not reached"); return; }

        String form = is.formName();
        List<String[]> rows = is.awaitExisting();
        boolean ready = is.gridReady();
        step("Read the severity levels already in the table", "Read the Incident Severity table", "The existing codes and remarks are known",
                !ready ? "The list did not load"
                        : rows.isEmpty() ? "The table is empty (pager: \"" + is.pagerText() + "\")"
                        : "Form Name \"" + form + "\" (locked); " + rows.size() + " level(s) exist (pager: \"" + is.pagerText() + "\"): " + rows.stream().map(r -> r[0] + " = " + r[1]).toList(),
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the list did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", guid = "", mimsDesc = "", mimsType = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = is.pickNew(rows, attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = is.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (one above the largest of the existing three-digit codes " + rows.stream().map(r -> r[0]).toList() + ")" : "Problem — " + is.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + is.problems); return; }

            boolean remarkOk = is.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a unique, appropriate Remark that is not in the table (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (a severity level above the table's own scale; different from every one of the " + rows.size() + " existing remarks)" : "Problem — " + is.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + is.problems); return; }

            guid = UUID.randomUUID().toString();
            boolean guidOk = is.enterMimsGuid(guid);
            if (attempt == 0) step("Enter MIMS GUID", "Enter a MIMS GUID (a fresh GUID, " + guid + ")", "The MIMS GUID is accepted",
                    guidOk ? "MIMS GUID \"" + guid + "\" entered" : "Problem — " + is.problems, guidOk ? "PASS" : "FAIL");
            if (!guidOk) { addSummary("Result", "FAILED — MIMS GUID: " + is.problems); return; }

            mimsDesc = "Catastrophic incident severity level, QA " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
            boolean descOk = is.enterMimsDescription(mimsDesc);
            if (attempt == 0) step("Enter MIMS Description", "Enter the MIMS Description (" + mimsDesc + ")", "The MIMS description is accepted",
                    descOk ? "MIMS Description \"" + mimsDesc + "\" entered" : "Problem — " + is.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — MIMS description: " + is.problems); return; }

            mimsType = is.selectMimsType(List.of("GenericItem"));
            if (attempt == 0) step("Select MIMS Type", "Select the MIMS Type", "A MIMS type is selected",
                    !mimsType.isEmpty() ? "MIMS Type \"" + mimsType + "\" selected (of " + is.mimsTypeOptions() + "); the screen's model holds it" : "Problem — " + is.problems, !mimsType.isEmpty() ? "PASS" : "FAIL");
            if (mimsType.isEmpty()) { addSummary("Result", "FAILED — MIMS type: " + is.problems); return; }

            toast = is.submitAndGetToast();
            ok = is.problems.isEmpty() && is.saveSucceeded();
            if (!ok && is.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The severity level is saved",
                is.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + is.problems, is.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = is.savedByServer();
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (is.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + is.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the level (ResultStatus 1, \"" + is.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + is.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + is.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // the new row must be populated in the table (if it is not, this step - and so the test - is a FAIL)
        String shown = (ok || savedByServer) ? is.awaitRowShown(code, remark, 15000) : "";
        String data = (ok || savedByServer) ? is.dataRowFor(code) : "";
        List<String[]> now = is.tableRows();
        // the table has no MIMS columns, so read back what the server stored for the three MIMS fields (the call the Edit pencil makes)
        String[] stored = (ok || savedByServer) ? is.serverRecord(code) : null;
        boolean mimsStored = stored != null && guid.equals(stored[3]) && mimsDesc.equals(stored[4]) && !stored[5].isEmpty() && !stored[5].equals("0");
        String mimsNote = stored == null ? "" : mimsStored ? "; the server also stored the MIMS GUID, Description and Type"
                : "; BUT the server did NOT store the MIMS values the form sent (read back through its own Edit call: MIMS GUID \"" + stored[3] + "\", MIMS Description \"" + stored[4] + "\", MIMS Type id \"" + stored[5] + "\" - empty / 0) - they are accepted and sent, then dropped";
        step("Verify the data added is populated in the table", "Look for the new row (code " + code + ", remark \"" + remark + "\") in the Incident Severity table", "The new row is listed in the table",
                !shown.isEmpty() ? "PASSES because the table shows \"" + shown + "\" (" + now.size() + " row(s) now, pager \"" + is.pagerText() + "\")" + (data.isEmpty() ? "" : "; the grid's own record for it: " + data) + mimsNote
                        : "FAILS because the table " + (now.isEmpty() ? "is BLANK" : "does not show the new row (it shows " + now.size() + " row(s): " + now.stream().map(r -> r[0] + " = " + r[1]).toList() + ")")
                          + (data.isEmpty() ? "; the grid's data does not hold it either" : "; the grid's own data DOES hold it: " + data),
                !shown.isEmpty() ? "PASS" : "FAIL");

        addSummary("Levels in the table before", String.valueOf(rows.size()));
        addSummary("Code / Remark added", code + " / " + remark);
        addSummary("MIMS GUID / Description / Type", guid + " / " + mimsDesc + " / " + mimsType);
        addSummary("Save request", is.lastSaveRequest.isEmpty() ? "(not seen)" : is.lastSaveRequest);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + is.lastSaveMessage.trim() + "\")" : "(no save confirmed" + (is.lastSaveMessage.isEmpty() ? "" : ": " + is.lastSaveMessage) + ")");
        addSummary("In the table afterwards", shown.isEmpty() ? "(not shown)" : shown + (data.isEmpty() ? "" : " | " + data));
        addSummary("MIMS values stored by the server", stored == null ? "(could not be read back)" : mimsStored ? "yes - GUID, Description and Type all stored"
                : "NO - read back through the Edit call: MIMS GUID \"" + stored[3] + "\", MIMS Description \"" + stored[4] + "\", MIMS Type id \"" + stored[5] + "\" although the request carried the GUID, the description and type id 1");
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok && !shown.isEmpty() ? toast : (shown.isEmpty() ? "FAIL - the new row is not populated in the table" + (ok ? "; the save itself worked (\"" + toast + "\")" : "")
                : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast))));
    }
}
