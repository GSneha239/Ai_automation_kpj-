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

        // Each step is logged right AFTER its data is entered and BEFORE the next action, so its screenshot shows the
        // filled form (Ward + Room Type with the generated bed list; then the ticked bed; then the remark) — not the
        // screen after Save. (The combined markBedUnderMaintenance() the Graphical View reuses runs everything first.)
        final String remark = "Bed under maintenance - automated test";
        um.ensureDates(today.format(f), today.plusDays(7).format(f));

        String combo = um.selectWardRoomTypeUntilBeds();
        boolean bedsOk = um.bedCount() > 0;
        step(page, "Select Ward + Room Type (generate bed list)",
                "Select Ward + Room Type; if the bed list isn't generated, change the dropdown values until beds appear",
                "The bed list is generated with at least one bed", combo, bedsOk ? "PASS" : "FAIL");
        if (!bedsOk) { addSummary("Result", "No bed list generated for any Ward/Room Type combo tried: " + combo); return; }

        String bed = um.selectFirstBed();
        String extra = um.selectExtraBed(System.getProperty("um.extraBed", "BED-777"));
        System.out.println("UnderMaintenance: extra bed -> " + (extra.isEmpty() ? um.lastExtraBedNote : extra));
        if (bed != null && !extra.isEmpty()) bed = bed + " + " + extra;
        step(page, "Select the bed", "Tick the SELECT checkbox of the first bed in the generated list (and BED-777 when listed)",
                "A bed is selected", bed == null ? "No bed selected" : "Selected: " + bed, bed == null ? "FAIL" : "PASS");
        if (bed == null) {
            addSummary("Result", "A bed list generated (" + combo + ") but no bed row could be selected from it"
                    + (um.lastNoBedReason.isEmpty() ? "" : " - " + um.lastNoBedReason));
            return;
        }

        um.enterRemark(remark);
        step(page, "Enter remark", "Enter the Remark (undermaintenance.remark)",
                "The remark is entered", "Remark = '" + remark + "'", "PASS");

        String toast = um.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("mainten") || tl.contains("success") || tl.contains("saved") || tl.contains("added");
        step(page, "Click Save & success toast", "Click Save (IUDBedUnderMaitenance()); wait for the success toast",
                "'Bed Under Maintenance saved successfully.' toast",
                ok ? toast : "FAIL: " + (toast == null || toast.isEmpty()
                        ? "Save (IUDBedUnderMaitenance()) produced no toast at all for bed " + bed
                        : "Save rejected bed " + bed + ": \"" + toast + "\""), ok ? "PASS" : "FAIL");

        addSummary("Under Maintenance · Bed", bed);
        addSummary("Result", ok ? toast : "Save not confirmed: \"" + toast + "\"");
    }
}
