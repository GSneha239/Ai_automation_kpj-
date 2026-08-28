package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Payor — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Payor</b> (Receivable Master).
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Payor</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Payor Type</b>, <b>Payable Code</b>, <b>Payor</b>, <b>Primary Phone No</b>, <b>E-mail</b>,
 *       <b>Primary Contact Person</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class Payor extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / payor already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Payor() { super("ApplicationConfig_Billing_Payor"); }

    public static void main(String[] args) {
        Payor t = new Payor();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Payor", "Application Configuration > Billing > Payor (Receivable Master)",
                "Add a Payor: Add, enter Payor Type + Payable Code + Payor + Primary Phone No + E-mail + Primary Contact Person, Submit, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.Payor py =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.Payor(page);

        // 1) Navigate
        py.navigateViaMenu();
        boolean onScreen = py.onScreen();
        step(page, "Open Payor screen", "Click Application Configuration -> Billing -> Payor",
                "The Payor (Receivable Master) screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = py.clickAdd();
        step(page, "Click Add", "Click Add", "The Payor add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = py.fillDetails(attempt);
            used++;
            toast = py.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Payor: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("PayableCode=PY") && !fill.contains("PayorType=(no")
                && !fill.contains("Payor=(no") && !fill.contains("Email=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Payor Type, Payable Code, Payor, Phone, E-mail, Contact Person",
                "Enter Payor Type, Payable Code, Payor, Primary Phone No, E-mail, Primary Contact Person",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDReceivable); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = py.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Payor list; find the row just added (by Payable Code)",
                    "The newly added Payor row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Payable Code=" + py.lastCode,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Payable Code", py.lastCode);
        addSummary("Payor", py.lastName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
