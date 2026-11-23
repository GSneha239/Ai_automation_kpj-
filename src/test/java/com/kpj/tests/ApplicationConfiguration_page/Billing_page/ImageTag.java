package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ImageTag — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>ImageTag</b> (the generic common-master screen, {@code #/ImageTag}).
 *
 * <ol>
 *   <li>Select the <b>Form Name</b> (default "Image Category"; pin another with {@code -Dimagetag.form=<regex>}); read what the server holds for it.</li>
 *   <li>Enter a <b>Code</b> and a <b>Description</b> the chosen master does not hold (a real image tag such as "Surgical Site Photo").</li>
 *   <li>Click <b>Submit</b> and verify the success toast.</li>
 *   <li>Verify that the record just added is populated in the <b>table</b>.</li>
 * </ol>
 *
 * <p>If the server still rejects the pair as a duplicate, the next unused one is tried, up to three times.</p>
 *
 * <p>KNOWN APP DEFECT (see the page object): this screen's table never fills (its rights lookup is never given the form's id), so the last step FAILS while the record is on the server.</p>
 *
 * <p>This test SAVES A REAL RECORD into the chosen master on every run; it stays (it can only be made inactive, which this test does not do).</p>
 */
public class ImageTag extends DevHisBase {

    public ImageTag() { super("ApplicationConfig_Billing_ImageTag"); }

    public static void main(String[] args) {
        ImageTag t = new ImageTag();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - ImageTag", "Application Configuration > Billing > ImageTag",
                "Select a form name; enter a code and a unique description; Submit; verify the success toast; verify the new record is populated in the table. SAVES A REAL RECORD.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.ImageTag it = new com.kpj.pages.ApplicationConfiguration_page.Billing_page.ImageTag(page);

        boolean on = it.navigateViaMenu();
        step("Open ImageTag", "Click Application Configuration -> Billing -> ImageTag", "The ImageTag screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — ImageTag not reached"); return; }

        String wanted = System.getProperty("imagetag.form", "^Image Category$");
        String form = it.selectFormName(wanted);
        List<String[]> rows = form.isEmpty() ? null : it.serverRows();
        List<String> shown = form.isEmpty() ? List.of() : it.tableRows();
        step("Select form name", "Select the Form Name (" + wanted + ")", "A form name is selected",
                !form.isEmpty() ? "Form Name \"" + form + "\" selected (" + it.lastFormValue + "; the list offers " + it.formCount() + " forms); the server holds " + (rows == null ? "(unreadable)" : String.valueOf(rows.size())) + " record(s) for it"
                        + " while the screen's table shows " + shown.size() + " row(s)"
                        : "Problem — " + it.problems, !form.isEmpty() ? "PASS" : "FAIL");
        if (form.isEmpty()) { addSummary("Result", "FAILED — form name: " + it.problems); return; }
        if (rows == null) rows = List.of();

        String toast = "";
        boolean ok = false;
        String code = "", description = "", tried = "";
        for (int attempt = 0; attempt < 3 && !ok; attempt++) {
            if (attempt > 0 && !it.formNameSelected()) it.selectFormName(wanted);            // a refused save may clear the form
            String[] pick = it.pickNew(rows, attempt);
            code = pick[0];
            description = pick[1];
            boolean codeOk = it.enterCode(code);
            if (attempt == 0) step("Enter code", "Enter a Code the master does not hold (" + code + ")", "The code is accepted",
                    codeOk ? "Code \"" + code + "\" entered (none of the server's " + rows.size() + " " + form + " records has it)" : "Problem — " + it.problems, codeOk ? "PASS" : "FAIL");
            if (!codeOk) { addSummary("Result", "FAILED — code: " + it.problems); return; }

            boolean descOk = it.enterDescription(description);
            if (attempt == 0) step("Enter description", "Enter a suitable, unique Description (" + description + ")", "The description is accepted",
                    descOk ? "Description \"" + description + "\" entered (different from every one of the server's " + rows.size() + " " + form + " descriptions)" : "Problem — " + it.problems, descOk ? "PASS" : "FAIL");
            if (!descOk) { addSummary("Result", "FAILED — description: " + it.problems); return; }

            toast = it.submitAndGetToast();
            ok = it.ok() && it.saveSucceeded();
            if (!ok && it.rejectedAsDuplicate() && attempt < 2) {
                tried += (tried.isEmpty() ? "" : "; ") + code + " / " + description + " was rejected (\"" + toast + "\")";
                rows = it.serverRows() == null ? rows : it.serverRows();
                continue;
            }
            if (!ok) break;
        }
        step("Click Submit", "Click Submit", "The record is saved",
                it.problems.isEmpty() ? "Submit clicked" + (it.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + it.lastDialog + "\"") + (tried.isEmpty() ? "" : " — earlier attempt(s): " + tried)
                        : "Problem — " + it.problems, it.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = it.savedByServer();
        step("Verify success toast message", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (it.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + it.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the record (ResultStatus 1, \"" + it.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + it.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + it.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // RULE (the user's, for tables): if the data added is not populated in the table, or the table is blank, this step - and so the test - is a FAIL.
        boolean onServer = (ok || savedByServer) && it.serverHolds(code, description);
        boolean inTable = (ok || savedByServer) && it.awaitInTable(code, description, 12000);
        List<String> tableNow = it.tableRows();
        step("Verify the data added is populated in the table", "Look for the new code and description in the table", "The new record is listed in the table",
                inTable ? "PASSES because the table shows \"" + code + " | " + description + "\""
                        : "FAILS because the table " + (tableNow.isEmpty() ? "is BLANK" : "does not show the new record (it shows " + tableNow.size() + " row(s): " + tableNow.subList(0, Math.min(3, tableNow.size())) + ")")
                          + ": \"" + code + " | " + description + "\" is not populated in it; " + it.explainEmptyGrid() + "; "
                          + (onServer ? "the SERVER DOES hold the record (read with the call the grid would make) - app defect: the screen never fetches the rows" : "the server does not hold it either"),
                inTable ? "PASS" : "FAIL");

        addSummary("Form Name", form + " (" + it.lastFormValue + ")");
        addSummary("Code / Description added", code + " / " + description);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1, \"" + it.lastSaveMessage.trim() + "\")" : "(no save confirmed)");
        addSummary("On the server afterwards", onServer ? "yes - the record is held" : "(not found)");
        addSummary("Table", inTable ? "shows the new record" : (tableNow.isEmpty() ? "BLANK - the new data is not populated" : "does not show the new record"));
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok && inTable ? toast : (!inTable ? "FAIL - the data is not populated in the table" + (tableNow.isEmpty() ? " (the table is blank)" : "") + (ok ? "; the save itself worked (\"" + toast + "\")" : "")
                : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast))));
    }
}
