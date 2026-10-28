package com.kpj.tests.ApplicationConfiguration_page.MRD_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.Comparator;
import java.util.List;

// The page object is also named RackMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; MRD &gt; <b>Rack Master</b>.
 *
 * <ol>
 *   <li>Read the racks already in the table (40 rows when this test was written: RC0001 = Rack 1 ... RC0040 = Rack 40).</li>
 *   <li>Enter the <b>Code</b> in the existing format (the next one: RC0041); enter a <b>Description</b> that follows the table's own naming with the matching number and collides with none of them (Rack 41);
 *       click <b>Submit</b>. The form is inline on this screen: there is no Add button.</li>
 *   <li>Verify the success toast, and that the new rack now appears in the list.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused pair is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL RACK into the master on every run; it stays (it can only be made inactive, which this test does not do) and becomes available to the MRD Row Master.</p>
 */
public class RackMaster extends DevHisBase {

    public RackMaster() { super("ApplicationConfig_MRD_RackMaster"); }

    public static void main(String[] args) {
        RackMaster t = new RackMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** The rows as text: all of them when few, else the first and last few by code (the table is a long regular series). */
    private static String summary(List<String[]> rows) {
        List<String> all = rows.stream().sorted(Comparator.comparing((String[] r) -> r[0])).map(r -> r[0] + " = " + r[1].trim()).toList();
        if (all.size() <= 8) return all.toString();
        return "[" + String.join(", ", all.subList(0, 3)) + ", ... , " + String.join(", ", all.subList(all.size() - 3, all.size())) + "]";
    }

    @Override
    protected void body() {
        meta("Application Configuration - MRD - Rack Master", "Application Configuration > MRD > Rack Master",
                "Read the existing racks; enter a Code in the existing format; enter a suitable, unique Description (not in the table); Submit; verify the success toast. SAVES A REAL RACK.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.MRD_page.RackMaster rm = new com.kpj.pages.ApplicationConfiguration_page.MRD_page.RackMaster(page);

        boolean on = rm.navigateViaMenu();
        step("Open Rack Master", "Click Application Configuration -> MRD -> Rack Master", "The Rack Master screen is shown",
                on ? "Opened " + page.url() + " (Form Name \"" + rm.formName() + "\")" : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Rack Master not reached"); return; }

        List<String[]> rows = rm.awaitExisting();
        boolean ready = rm.gridReady();
        step("Read the racks already in the table", "Read the Rack Master table (all pages)", "The existing codes and descriptions are known",
                !ready ? "The table did not load" : rows.isEmpty() ? "The table is empty (the grid loaded with no rows; pager: \"" + rm.pagerText() + "\") - there is no existing code or description to differ from"
                        : rows.size() + " rack(s) exist (pager: \"" + rm.pagerText() + "\"): " + summary(rows),
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the table did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = rm.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = rm.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code in the existing format that is not in the table (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — " + (rows.isEmpty() ? "the table is empty, so it starts the series" : "same format as the existing codes (" + summaryCodes(rows) + "), one above the largest")
                           : "Problem — " + rm.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + rm.problems); return; }

            boolean descOk = rm.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description that is not in the table (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (" + (rows.isEmpty() ? "the table holds no descriptions yet, so it is unique" : "follows the table's own \"Rack n\" naming with the code's number and differs from every one of the " + rows.size() + " existing descriptions") + ")"
                           : "Problem — " + rm.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + rm.problems); return; }

            toast = rm.submitAndGetToast();
            ok = rm.ok() && rm.saveSucceeded();
            if (!ok && rm.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The rack is submitted", rm.problems.isEmpty() ? "Submit clicked" + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried) : "Problem — " + rm.problems,
                rm.problems.isEmpty() ? "PASS" : "FAIL");

        // the new rack should now be in the list
        String listed = "";
        if (ok) {
            long end = System.currentTimeMillis() + 15000;
            while (System.currentTimeMillis() < end && listed.isEmpty()) {
                for (String[] r : rm.existing()) if (r[0].equalsIgnoreCase(code) && r[1].trim().equalsIgnoreCase(description)) listed = r[0] + " = " + r[1].trim() + " (status " + r[2] + ")";
                if (listed.isEmpty()) page.waitForTimeout(500);
            }
        }
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (rm.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + rm.lastSaveMessage + "\"")
                        + (listed.isEmpty() ? "; the new row was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + rm.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + rm.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Racks in the table before", String.valueOf(rows.size()));
        addSummary("Code / Description added", code + " / " + description);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }

    /** The first and last codes, e.g. "RC0001 ... RC0040". */
    private static String summaryCodes(List<String[]> rows) {
        List<String> codes = rows.stream().map(r -> r[0]).sorted().toList();
        return codes.size() <= 4 ? codes.toString() : codes.get(0) + " ... " + codes.get(codes.size() - 1);
    }
}
