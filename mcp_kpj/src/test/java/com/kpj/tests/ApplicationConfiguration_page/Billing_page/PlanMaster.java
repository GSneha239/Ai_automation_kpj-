package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PlanMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Plan</b> (Plan Master).
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Plan</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Location</b>, <b>Plan Code</b>, <b>Plan Name</b>, <b>Pricing Policy</b>, <b>Group</b>,
 *       <b>Sub Group</b>, <b>Services</b>, <b>Class</b>, <b>Co-Pay(%)</b>, <b>Discount</b>.</li>
 *   <li>Click <b>Add</b> (add the detail line), then <b>Submit</b> → success toast.</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class PlanMaster extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the plan code / name already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public PlanMaster() { super("ApplicationConfig_Billing_PlanMaster"); }

    public static void main(String[] args) {
        PlanMaster t = new PlanMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Plan", "Application Configuration > Billing > Plan",
                "Add a Plan: Add, enter Location + Plan Code + Plan Name + Pricing Policy + Group + Sub Group + Services + Class + Co-Pay(%) + Discount, Add the detail line, Submit, then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PlanMaster pl =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PlanMaster(page);

        // 1) Navigate
        pl.navigateViaMenu();
        boolean onScreen = pl.onScreen();
        step(page, "Open Plan screen", "Click Application Configuration -> Billing -> Plan",
                "The Plan screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = pl.clickAdd();
        step(page, "Click Add", "Click Add", "The Plan add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) + 4) Enter fields, add the detail line, Submit — retry with different details on "already exists".
        String fill = "", lineToast = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = pl.fillDetails(attempt);
            lineToast = pl.addDetailLine();
            used++;
            toast = pl.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("PlanMaster: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("PlanCode=PL") && !fill.contains("Location=(no")
                && !fill.contains("PricingPolicy=(no") && !fill.contains("Group=(no") && !fill.contains("Class=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Plan details",
                "Enter Location, Plan Code, Plan Name, Pricing Policy, Group, Sub Group, Services, Class, Co-Pay(%), Discount",
                "All fields are entered", fillActual, fillOk ? "PASS" : "FAIL");

        boolean lineOk = lineToast != null && (lineToast.toLowerCase().contains("added")
                || lineToast.toLowerCase().contains("detail row") || lineToast.toLowerCase().contains("success"));
        step(page, "Click Add (add the detail line)", "Click Add (fnAddListOfServices) to add the detail line",
                "The detail line is added to the plan grid",
                lineToast == null || lineToast.isEmpty() ? "No confirmation after Add" : lineToast, lineOk ? "PASS" : "FAIL");

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit (fnIUDPlanMaster); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = pl.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Plan list; find the row just added (by Plan Code)",
                    "The newly added Plan row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Plan Code=" + pl.lastCode,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Plan Code", pl.lastCode);
        addSummary("Plan Name", pl.lastName);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
