package com.kpj.tests.SystemConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.SystemConfiguration_page.UserPage;

/**
 * System Configuration &gt; <b>User</b> ({@code #/User}).
 *
 * <p>Each tab has its OWN Submit action (confirmed live: {@code FnIUDUserDetails()} /
 * {@code SubmitUserOtherDetails()} / {@code SaveIntegrationAccessControl()}), and a successful Submit closes the
 * whole Add session back to the User list (confirmed live: the tab strip is gone afterward). So each tab is
 * tested from its OWN FRESH <b>Add</b> click, not one continuous session across all four.</p>
 *
 * <ol>
 *   <li><b>Add</b> → <b>User Details</b> (default tab): select <b>Payable Type</b> + <b>Payable</b> (cascade),
 *       enter <b>Login ID</b>/<b>Password</b>/<b>Confirm Password</b>, tick a <b>Location Details</b> row,
 *       select <b>OPD Waiting Area</b>, tick a user right → <b>Submit</b> ({@code FnIUDUserDetails}).</li>
 *   <li><b>Add</b> → <b>Inventory Details1</b> tab: click <b>Add</b> (if present), tick a checkbox in each
 *       table → <b>Submit</b> ({@code SubmitUserOtherDetails}).</li>
 *   <li><b>Add</b> → <b>Report Field Settings</b> tab: tick a checkbox in each of Outpatient Queue Management,
 *       Occupancy List, Doctor Queue, Emergency Visit, Dispense Process (Preparation), Dispense Process
 *       (Dispensing) — FAIL that step if any table has no data → <b>Submit</b>.</li>
 *   <li><b>Add</b> → <b>Integration Access Control</b> tab: tick a checkbox in each table → <b>Submit</b>
 *       ({@code SaveIntegrationAccessControl}).</li>
 * </ol>
 *
 * <p>This flow CREATES LOGIN ACCOUNTS on the QA environment, with throwaway passwords.</p>
 */
public class User extends DevHisBase {

    private static final String[] REPORT_FIELD_TABLES = {
            "Outpatient Queue Management", "Occupancy List", "Doctor Queue", "Emergency Visit",
            "Dispense Process (Preparation)", "Dispense Process (Dispensing)"
    };

    public User() { super("SystemConfig_User"); }

    public static void main(String[] args) {
        User t = new User();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("System Configuration - User", "System Configuration > User",
                "Add a user (User Details tab), then re-Add and test each other tab in turn (Inventory Details1, "
                        + "Report Field Settings, Integration Access Control) — each tab's Submit closes the Add "
                        + "session, so each is tested from its own fresh Add click.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        UserPage up = new UserPage(page);

        // If the app serves a different screen, FAIL and say so plainly rather than filling leftover markup.
        boolean on = up.navigateViaMenu();
        String landed = up.currentScreen();
        step(page, "Open User screen", "System Configuration -> User",
                "The User list is shown",
                on ? "Opened " + landed : "WRONG PAGE - expected User but the app opened: " + landed,
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - wrong page opened: " + landed); return; }

        // ===== Tab 1: User Details =====

        boolean addOk1 = clickAddStep(up, "Click Add");
        if (!addOk1) { addSummary("Result", "FAILED - Add did not open the entry form"); return; }

        String details = up.fillUserDetails();
        boolean detOk = details.contains("LoginID=QAUSER") && !details.contains("(no field)")
                && !details.contains("PayableType=(no") && !details.contains("Payable=(no");
        step(page, "Select Payable Type + Payable; enter Login ID, Password, Confirm Password",
                "Select Payable Type* then Payable* (cascade); enter Login ID*, Password*, Confirm Password*",
                "Payable Type, Payable, Login ID and both password fields are set",
                details, detOk ? "PASS" : "FAIL");

        String loc = up.fillLocationAndTypes();
        // DEPOSIT/RECEIPT TYPE IS NO LONGER ASSERTED (removed 2026-08-10, deliberately — not an oversight).
        // The field (UserDetailsModified.SubGroupID, fed by the Payable Type cascade) has ZERO options for EVERY
        // Payable Type on both KLG and DSH — 8 types tried on KLG, 7 on DSH, including types offering 1564
        // payables. It is an environment/master-data gap (the Sub Group master under Billing has nothing to
        // offer these locations), not something this flow can drive, and the app does not actually enforce it:
        // Submit now returns "User Details saved successfully." with the field empty.
        boolean locOk = !loc.contains("Location=(none)") && !loc.contains("OPDWaitingArea=(not found)");
        step(page, "Select Location Details, OPD Waiting Area",
                "Tick a Location Details row; select OPD Waiting Area",
                "Location and OPD Waiting Area are selected",
                loc + "\nDeposit/Receipt Type: NOT ASSERTED — no options exist for any Payable Type on this"
                        + " environment (Sub Group master not configured); the app saves without it",
                locOk ? "PASS" : "FAIL");

        String right = up.tickUserRight();
        boolean rightOk = right.contains("(ticked)");
        step(page, "Tick a user right", "Tick any checkbox in the user-rights block (#UserDetailsTab)",
                "One user right is ticked", right, rightOk ? "PASS" : "FAIL");

        submitAndStep(up, "Click Submit & success toast (User Details)", "Click Submit (FnIUDUserDetails)");

        // ===== Tab 2: Inventory Details1 — fresh Add (a successful Submit above closed the session) =====

        boolean addOk2 = clickAddStep(up, "Click Add (Inventory Details1)");
        String invTab = addOk2 ? up.clickInventoryDetails1Tab() : "(skipped — Add did not open the form)";
        boolean invTabOk = invTab.contains("clicked");
        step(page, "Click Inventory Details1 tab", "Click the Inventory Details1 tab",
                "The Inventory Details1 tab is shown", invTab, invTabOk ? "PASS" : "FAIL");

        // "No Add button" is a no-op, not a failure — this tab can be inline (tables already visible), the same
        // convention several other DevHIS screens in this codebase use for their own Add button.
        String invAdd = invTabOk ? up.clickAddInInventoryTab() : "(skipped — tab did not open)";
        boolean invAddOk = invTabOk && (invAdd.contains("Add clicked") || invAdd.contains("no Add button"));
        step(page, "Click Add (inside Inventory Details1)", "Click Add again inside the Inventory Details1 tab, if present",
                "The inventory table(s) are shown", invAdd, invAddOk ? "PASS" : "FAIL");

        String invCb = invAddOk ? up.tickCheckboxInEachTable() : "(skipped — tab did not open)";
        boolean invCbOk = invAddOk && invCb.contains("ticked=") && !invCb.contains("ticked=0");
        step(page, "Select any checkbox in each table (Inventory Details1)",
                "Tick one checkbox in each table on the Inventory Details1 tab",
                "A checkbox is ticked in every table", invCb, invCbOk ? "PASS" : "FAIL");

        submitAndStep(up, "Click Submit & success toast (Inventory Details1)", "Click Submit (SubmitUserOtherDetails)");

        // ===== Tab 3: Report Field Settings — fresh Add =====

        boolean addOk3 = clickAddStep(up, "Click Add (Report Field Settings)");
        String rfsTab = addOk3 ? up.clickReportFieldSettingsTab() : "(skipped — Add did not open the form)";
        boolean rfsTabOk = rfsTab.contains("clicked");
        step(page, "Click Report Field Settings tab", "Click the Report Field Settings tab",
                "The Report Field Settings tab is shown", rfsTab, rfsTabOk ? "PASS" : "FAIL");

        String rfsTicks = rfsTabOk ? up.tickCheckboxInNamedTables(REPORT_FIELD_TABLES) : "(skipped — tab did not open)";
        boolean rfsOk = rfsTabOk && !rfsTicks.contains("(no data)") && !rfsTicks.contains("(table not found)")
                && !rfsTicks.contains("no checkbox found") && !rfsTicks.contains("click-failed");
        step(page, "Select any checkbox in each table (Report Field Settings)",
                "Tick a checkbox in each of: " + String.join(", ", REPORT_FIELD_TABLES),
                "A checkbox is ticked in every table (none has no data)", rfsTicks, rfsOk ? "PASS" : "FAIL");

        submitAndStep(up, "Click Submit & success toast (Report Field Settings)", "Click Submit");

        // ===== Tab 4: Integration Access Control — fresh Add =====

        boolean addOk4 = clickAddStep(up, "Click Add (Integration Access Control)");
        String iacTab = addOk4 ? up.clickIntegrationAccessControlTab() : "(skipped — Add did not open the form)";
        boolean iacTabOk = iacTab.contains("clicked");
        step(page, "Click Integration Access Control tab", "Click the Integration Access Control tab",
                "The Integration Access Control tab is shown", iacTab, iacTabOk ? "PASS" : "FAIL");

        String iacCb = iacTabOk ? up.tickCheckboxInEachTable() : "(skipped — tab did not open)";
        boolean iacCbOk = iacTabOk && iacCb.contains("ticked=") && !iacCb.contains("ticked=0");
        step(page, "Select any checkbox in each table (Integration Access Control)",
                "Tick one checkbox in each table on the Integration Access Control tab",
                "A checkbox is ticked in every table", iacCb, iacCbOk ? "PASS" : "FAIL");

        submitAndStep(up, "Click Submit & success toast (Integration Access Control)", "Click Submit (SaveIntegrationAccessControl)");

        addSummary("Login ID", up.lastLoginId);
        addSummary("Payable Type / Payable", up.lastPayableType + " / " + up.lastPayable);
        addSummary("Location", up.lastLocation);
        addSummary("OPD Waiting Area", up.lastWaitingArea);
        addSummary("Deposit/Receipt Type", up.lastDepositType);
        addSummary("User right ticked", up.lastRight);
        addSummary("Inventory Details1", invCb);
        addSummary("Report Field Settings", rfsTicks);
        addSummary("Integration Access Control", iacCb);
        addSummary("Route", "#/User -> #/Add-UserDetailsModified");
    }

    /** Click Add and report the step; returns whether the entry form opened. */
    private boolean clickAddStep(UserPage up, String stepName) {
        boolean addOk = up.clickAdd() || up.onAddForm();
        step(page, stepName, "Click Add to open the user entry form",
                "The user entry form is shown",
                addOk ? "Add form opened - " + up.currentScreen() : "Add did NOT open the form - " + up.currentScreen(),
                addOk ? "PASS" : "FAIL");
        return addOk;
    }

    /** Click whichever Submit button is visible on the CURRENTLY ACTIVE tab and report its toast — every tab on
     *  this form routes "Submit" to its own save function, so this is called once per tab. */
    private void submitAndStep(UserPage up, String stepName, String desc) {
        String toast = up.submitAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        // The message itself is the assertion: ONLY a success toast passes.
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "WRONG MESSAGE - expected a success message but the app showed: \"" + toast + "\""
                        // "Please fill * mark fields!" never says WHICH — list them.
                        + "\nStarred fields still empty: " + up.emptyStarredFields());
        if (up.toastPng != null && up.toastPng.length > 0) {
            step(up.toastPng, stepName, desc, "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        } else {
            step(page, stepName, desc, "A '... saved successfully' success toast is shown", actual, ok ? "PASS" : "FAIL");
        }
    }
}
