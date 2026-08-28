package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ConcessionTemplate — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Concession Template</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Concession Template</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b>, <b>Remark</b>, <b>Group</b>, <b>Percentage</b>.</li>
 *   <li>Click <b>Add</b> (add the detail line), then <b>Save</b>; wait for the success toast. If the toast says
 *       the code/remark already exists, change the details and Save again.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class ConcessionTemplate extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public ConcessionTemplate() { super("ApplicationConfig_Billing_ConcessionTemplate"); }

    public static void main(String[] args) {
        ConcessionTemplate t = new ConcessionTemplate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Concession Template", "Application Configuration > Billing > Concession Template",
                "Add a Concession Template: Add, enter Code + Remark + Group + Percentage, Add the detail line, Save, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.ConcessionTemplate ct =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.ConcessionTemplate(page);

        // 1) Navigate
        ct.navigateViaMenu();
        boolean onScreen = ct.onScreen();
        step(page, "Open Concession Template screen", "Click Application Configuration -> Billing -> Concession Template",
                "The Concession Template screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = ct.clickAdd();
        step(page, "Click Add", "Click Add", "The Concession Template add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, add the detail line, Save — retry with different details on "already exists".
        String fill = "", lineToast = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = ct.fillDetails(attempt);
            used++;
            lineToast = ct.addDetailLine();
            toast = ct.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("ConcessionTemplate: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=CT") && !fill.contains("Group=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code, Remark, Group, Percentage", "Enter Code, Remark, Group, Percentage",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        // 4a) Click Add (add the detail line)
        boolean lineOk = lineToast != null && (lineToast.toLowerCase().contains("added")
                || lineToast.toLowerCase().contains("detail row") || lineToast.toLowerCase().contains("success"));
        step(page, "Click Add (add the detail line)", "Click Add (AddConcessionDetails) to add the Group + Percentage line",
                "The detail line is added to the template grid",
                lineToast == null || lineToast.isEmpty() ? "No confirmation after Add" : lineToast, lineOk ? "PASS" : "FAIL");

        // 4b) Click Save -> success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Save & success toast", "Click Save (fnIUDConcessiontemplate); on 'already exists' change the details and Save again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = ct.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Concession Template list; find the row just added (by Code)",
                    "The newly added Concession Template row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Code=" + ct.lastCode,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Concession Code", ct.lastCode);
        addSummary("Remark", ct.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
