package com.kpj.tests.ApplicationConfiguration_page.Nursing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named HistoryMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Nursing &gt; <b>History Master</b>.
 *
 * <ol>
 *   <li>Read the history items already in the tree (117 at the time, under 12 history types plus one childless top-level entry).</li>
 *   <li>Enter the <b>Code</b> (one above the largest all-digit code in use), a <b>Remark</b> that is not among them — a real history item such as "Sleep Pattern" — and select the <b>Parent</b> it belongs
 *       under (for example "Social History"). The screen has no Add button: the form is always open.</li>
 *   <li>Click <b>Save</b>, verify the success toast, and that the new item now appears in the screen's data under that parent.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused item is tried, up to three times.</p>
 *
 * <p>This test SAVES A REAL HISTORY ITEM into the master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class HistoryMaster extends DevHisBase {

    public HistoryMaster() { super("ApplicationConfig_Nursing_HistoryMaster"); }

    public static void main(String[] args) {
        HistoryMaster t = new HistoryMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Nursing - History Master", "Application Configuration > Nursing > History Master",
                "Read the history items; enter a code, a suitable unique remark (not among them) and select the parent; Save; verify the success toast. SAVES A REAL HISTORY ITEM.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Nursing_page.HistoryMaster hm = new com.kpj.pages.ApplicationConfiguration_page.Nursing_page.HistoryMaster(page);

        boolean on = hm.navigateViaMenu();
        step("Open History Master", "Click Application Configuration -> Nursing -> History Master", "The History Master screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — History Master not reached"); return; }

        List<com.kpj.pages.ApplicationConfiguration_page.Nursing_page.HistoryMaster.Item> items = hm.items();
        int parents = hm.options().size();
        step("Read the history items already in the tree", "Read the History Details tree", "The existing items are known",
                items.isEmpty() ? "The tree data did not load" : items.size() + " item(s) in " + hm.topLevel().size() + " top-level history types " + hm.topLevel().stream().map(t -> t[1] + " (" + t[2] + ")").toList()
                        + "; the Parent dropdown offers " + parents + " item(s)", items.isEmpty() ? "FAIL" : "PASS");
        if (items.isEmpty()) { addSummary("Result", "FAILED — the tree data did not load"); return; }

        String toast = "";
        boolean ok = false;
        String code = "", remark = "", parent = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            String[] pick = hm.pickNew(attempt);
            code = pick[0];
            remark = pick[1];
            boolean codeOk = hm.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code that is not in the tree (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered — one above the largest all-digit code in use (the codes are plain numbers following the item ids; three manual rows are exceptions: 099, 123, C012)"
                           : "Problem — " + hm.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + hm.problems); return; }

            boolean remarkOk = hm.enterRemark(remark);
            if (attempt == 0) step("Enter remark", "Enter a suitable, unique Remark that is not in the tree (" + remark + ")", "The remark is accepted",
                    remarkOk ? "Remark \"" + remark + "\" entered (different from every one of the " + items.size() + " existing descriptions)" : "Problem — " + hm.problems, remarkOk ? "PASS" : "FAIL");
            if (!remarkOk) { addSummary("Result", "FAILED — remark: " + hm.problems); return; }

            String sel = hm.selectParent(pick[2]);
            if (attempt == 0) step("Select Parent", "Select the Parent the item belongs under (" + pick[2] + ")", "The parent is selected",
                    !sel.isEmpty() ? "Parent \"" + sel + "\" selected" : "Problem — " + hm.problems, !sel.isEmpty() ? "PASS" : "FAIL");
            if (sel.isEmpty()) { addSummary("Result", "FAILED — parent: " + hm.problems); return; }
            parent = sel;

            toast = hm.saveAndGetToast();
            ok = hm.ok() && hm.saveSucceeded();
            if (!ok && hm.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + remark + " was rejected (\"" + toast + "\")";
                continue;
            }
            if (!ok) break;
        }
        step("Click Save", "Click Save", "The history item is saved",
                hm.problems.isEmpty() ? "Save clicked" + (hm.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + hm.lastDialog + "\"") + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried)
                        : "Problem — " + hm.problems, hm.problems.isEmpty() ? "PASS" : "FAIL");

        // the screen asks for the tree again after a save: the new item should be in it, under its parent
        boolean savedByServer = hm.savedByServer();
        String listed = "";
        if (ok || savedByServer) {
            com.kpj.pages.ApplicationConfiguration_page.Nursing_page.HistoryMaster.Item it = hm.awaitItem(code, remark, parent, 15000);
            if (it != null) listed = it.code + " = " + it.description + " (id " + it.id + ") under \"" + hm.nameOf(it.parentId) + "\"";
        }
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (hm.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + hm.lastSaveMessage.trim() + "\"")
                        + (listed.isEmpty() ? "; the new item was not found in the tree data afterwards" : "; the tree now holds " + listed)
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the item (ResultStatus 1, \"" + hm.lastSaveMessage.trim() + "\")"
                           + (listed.isEmpty() ? "; the new item was not found in the tree data afterwards" : "; the tree now holds " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + hm.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + hm.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("History items before", String.valueOf(items.size()));
        addSummary("Code / Remark / Parent added", code + " / " + remark + " / " + parent);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + hm.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("In the tree afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
