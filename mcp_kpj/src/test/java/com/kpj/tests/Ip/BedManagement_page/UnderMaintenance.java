package com.kpj.tests.Ip.BedManagement_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named UnderMaintenance — referenced by its fully-qualified name.

/**
 * IP &gt; Bed Management &gt; <b>Under Maintenance</b> ({@code #/underMaintenanceList}).
 *
 * <p>Per the M6 Bed Planning FSD, marking a bed Under Maintenance takes it out of the vacant pool. Flow:</p>
 * <ol>
 *   <li>Click <b>Add</b> ({@code AddUnderMaintence()}) → the add form ({@code #/add-undermaintenance}).</li>
 *   <li>Select <b>Ward</b> + <b>Room Type</b> — the system generates the bed list from these filters; if empty,
 *       change the dropdown values until beds appear.</li>
 *   <li>Select a bed from the generated list.</li>
 *   <li>Enter a <b>Remark</b> → <b>Save</b> ({@code IUDBedUnderMaitenance()}) → success toast.</li>
 * </ol>
 */
public class UnderMaintenance extends DevHisBase {

    public UnderMaintenance() { super("UnderMaintenance"); }

    public static void main(String[] args) {
        UnderMaintenance t = new UnderMaintenance();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Bed Management - Under Maintenance", "IP > Bed Management > Under Maintenance",
                "Add -> select Ward + Room Type (generate bed list; change dropdowns if empty) -> select a bed -> enter Remark -> Save.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Ip.BedManagement_page.UnderMaintenance um =
                new com.kpj.pages.Ip.BedManagement_page.UnderMaintenance(page);

        boolean opened = um.navigateViaMenu();
        step(page, "Open Under Maintenance", "IP > Bed Management > Under Maintenance",
                "The Under Maintenance (#/underMaintenanceList) screen is shown",
                opened ? "Opened " + page.url() : "Did NOT reach the screen", opened ? "PASS" : "FAIL");
        if (!opened) return;

        boolean form = um.clickAdd();
        step(page, "Click Add", "Click Add (AddUnderMaintence())",
                "The add form (#/add-undermaintenance) opens", form ? "Form opened (" + page.url() + ")" : "Form did NOT open",
                form ? "PASS" : "FAIL");
        if (!form) return;

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();

        // Select Ward + Room Type (generate bed list) -> select a bed -> Remark -> Save — the SAME logic the
        // Graphical View "vacant bed -> Under Maintenance" action reuses, so both entry points behave
        // identically rather than drifting apart as two copies.
        com.kpj.pages.Ip.BedManagement_page.UnderMaintenance.MaintenanceResult res =
                um.markBedUnderMaintenance(today.format(f), today.plusDays(7).format(f), "Bed under maintenance - automated test");

        step(page, "Select Ward + Room Type (generate bed list)",
                "Select Ward + Room Type; if the bed list isn't generated, change the dropdown values until beds appear",
                "The bed list is generated with at least one bed", res.combo, res.bedsOk ? "PASS" : "FAIL");
        if (!res.bedsOk) { addSummary("Result", res.failReason); return; }

        step(page, "Select the bed", "Tick the SELECT checkbox of the first bed in the generated list",
                "A bed is selected", res.bed == null ? "No bed selected" : "Selected: " + res.bed, res.bed == null ? "FAIL" : "PASS");
        if (res.bed == null) { addSummary("Result", res.failReason); return; }

        step(page, "Enter remark", "Enter the Remark (undermaintenance.remark)",
                "The remark is entered", "Remark = 'Bed under maintenance - automated test'", "PASS");

        step(page, "Click Save & success toast", "Click Save (IUDBedUnderMaitenance()); wait for the success toast",
                "'Bed Under Maintenance saved successfully.' toast",
                res.ok ? res.toast : "FAIL: " + res.failReason, res.ok ? "PASS" : "FAIL");

        addSummary("Under Maintenance · Bed", res.bed == null ? "-" : res.bed);
        addSummary("Result", res.ok ? res.toast : res.failReason);
    }
}
