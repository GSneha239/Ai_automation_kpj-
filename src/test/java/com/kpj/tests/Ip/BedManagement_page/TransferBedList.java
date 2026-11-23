package com.kpj.tests.Ip.BedManagement_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named TransferBedList — referenced by its fully-qualified name.

/**
 * IP &gt; Bed Management &gt; <b>Transfer Bed List</b> ({@code #/TransferBedList}). Two actions:
 * <ol>
 *   <li><b>New</b> ({@code #/addTransferBed}) — search MRN (loads the admitted patient + current bed) → select
 *       target Ward/Room Type/Bed Type (system generates the vacant bed list) → select a bed → Remark →
 *       Save ({@code savebedtransfer()}).</li>
 *   <li><b>Transfer Checklist</b> — enter date range → Search → select a transferred-bed record → tick a checklist
 *       item → Remark → Save ({@code fnSaveTransferChecklist()}).</li>
 * </ol>
 *
 * <p>Per the M6 Bed Planning FSD: New searches by MRN, the current bed auto-populates, then Ward/Room Type/Bed
 * Type/Billing Class in Vacant Bed Selection generate the bed list; select an available bed and Save.</p>
 */
public class TransferBedList extends DevHisBase {

    public TransferBedList() { super("TransferBedList"); }

    public static void main(String[] args) {
        TransferBedList t = new TransferBedList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Bed Management - Transfer Bed List", "IP > Bed Management > Transfer Bed List",
                "New bed transfer (MRN search -> target ward/room/bed type -> select bed -> Save); and Transfer Checklist "
                        + "(date range -> Search -> select record -> tick checklist -> Remark -> Save).");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionNewTransfer();
        sectionTransferChecklist();
    }

    private com.kpj.pages.Ip.BedManagement_page.TransferBedList open() {
        com.kpj.pages.Ip.BedManagement_page.TransferBedList tb =
                new com.kpj.pages.Ip.BedManagement_page.TransferBedList(page);
        tb.navigateViaMenu();
        page.waitForTimeout(2000);
        return tb;
    }

    // ===== Section: New bed transfer ====================================
    private void sectionNewTransfer() {
        com.kpj.pages.Ip.BedManagement_page.TransferBedList tb = open();
        step(page, "New · Open Transfer Bed List", "IP > Bed Management > Transfer Bed List",
                "Screen shown", page.url(), page.url().toLowerCase().contains("transferbedlist") ? "PASS" : "FAIL");

        // Pull MRNs from the List Of Transferred Beds (patients with beds) as priority transfer candidates.
        java.time.format.DateTimeFormatter df = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate td = java.time.LocalDate.now();
        tb.setDateRangeAndSearch(td.minusDays(365).format(df), td.plusDays(1).format(df));
        java.util.List<String> candidates = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(tb.getTransferredMrns()));
        candidates.addAll(com.kpj.pages.Ip.BedManagement_page.TransferBedList.shuffledTransferMrns());   // then the fallback pool

        boolean form = tb.clickNew() && page.url().toLowerCase().contains("addtransferbed");
        step(page, "New · Click New", "Click New", "The new bed-transfer form (#/addTransferBed) opens",
                form ? "Form opened (" + page.url() + ")" : "Form did NOT open", form ? "PASS" : "FAIL");
        if (!form) return;

        // Auto-pick an admitted patient (current bed loads on search), then find target beds + transfer — the
        // SAME logic the Graphical View "occupied bed -> Transfer Bed" action reuses, so both entry points
        // behave identically rather than drifting apart as two copies.
        com.kpj.pages.Ip.BedManagement_page.TransferBedList.TransferResult res = tb.autoTransferAdmittedPatient(candidates, 4);

        step(page, "New · Enter MRN & Search", "Enter candidate MRN(s) + Search (SearchPatientByMRNo()); use the first ADMITTED patient (has a current bed) as the single test subject",
                "An admitted patient + current bed loaded", "Tried: " + res.tried + "| subject " + (res.usedMrn == null ? "none" : res.usedMrn + " (current ward: " + res.currentWard + ")"),
                res.usedMrn != null ? "PASS" : "FAIL");
        if (res.usedMrn == null) { addSummary("New · Result", res.failReason); return; }

        boolean targetOk = res.target != null && res.target.contains("greenBeds=");
        step(page, "New · Select target Ward/Room Type (generate bed list)", "Select target Ward + Room Type (no Bed Type); change until a vacant (green) bed list generates",
                "A vacant bed list is generated",
                targetOk ? res.target : "No ward/room-type combo produced a vacant (green) bed for MRN " + res.usedMrn,
                targetOk ? "PASS" : "FAIL");
        step(page, "New · Select a bed", "Tick the SELECT checkbox of a vacant bed",
                "A bed is selected",
                res.bed != null ? "selected: " + res.bed
                        : res.unselectableReason.isEmpty() ? "no bed available to select" : res.unselectableReason,
                res.bed == null ? "FAIL" : "PASS");
        step(page, "New · Enter remark", "Set Transfer by + Billing Class + Remark", "Transfer details filled",
                res.extras == null || res.extras.isEmpty() ? "Not reached — no bed was selected to fill remarks for" : res.extras,
                res.extras == null || res.extras.isEmpty() ? "FAIL" : "PASS");
        step(page, "New · Click Save & success toast", "Click Save (savebedtransfer()) — auto-picked MRN " + res.usedMrn,
                "'Bed Transfer Request saved successfully.' toast",
                res.ok ? res.toast : "FAIL: " + res.failReason, res.ok ? "PASS" : "FAIL");
        addSummary("New · Result", res.ok ? ("MRN " + res.usedMrn + " -> " + res.toast) : res.failReason);
    }

    // ===== Section: Transfer Checklist ==================================
    private void sectionTransferChecklist() {
        com.kpj.pages.Ip.BedManagement_page.TransferBedList tb = open();
        step(page, "Checklist · Open Transfer Bed List", "IP > Bed Management > Transfer Bed List",
                "Screen shown", page.url(), page.url().toLowerCase().contains("transferbedlist") ? "PASS" : "FAIL");

        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate today = java.time.LocalDate.now();
        int rows = tb.setDateRangeAndSearch(today.minusDays(120).format(f), today.plusDays(1).format(f));
        if (rows == 0) rows = tb.setDateRangeAndSearch(today.minusDays(365).format(f), today.plusDays(7).format(f));
        step(page, "Checklist · Enter date range & Search", "Set From/To date, click Search (fetchgrid())",
                "The List Of Transferred Beds lists records", rows + " transferred-bed record(s)", rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Checklist · Result", "No transferred-bed records"); return; }

        String sel = tb.selectFirstTransferRow();
        step(page, "Checklist · Select a record", "Tick the SELECT checkbox of a transferred-bed row",
                "A record is selected", sel == null ? "No row" : "Selected: " + sel, sel == null ? "FAIL" : "PASS");
        if (sel == null) return;

        boolean popup = tb.clickTransferChecklist();
        step(page, "Checklist · Click Transfer Checklist", "Click Transfer Checklist", "The Transfer Checklist popup opens",
                popup ? "Popup opened" : "Popup did NOT open", popup ? "PASS" : "FAIL");
        if (!popup) return;

        String item = tb.selectFirstChecklistItem();
        step(page, "Checklist · Select a checklist", "Tick a checklist item (item.IsSelected)",
                "A checklist item is selected", item == null ? "No item" : "Selected: " + item, item == null ? "FAIL" : "PASS");

        tb.enterChecklistRemark("Transfer checklist completed - automated test");
        step(page, "Checklist · Enter remark", "Enter the Remark (checklistNarration)", "The remark is entered", "Remark entered", "PASS");

        String toast = tb.saveChecklistAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("checklist") || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Checklist · Click Save & success toast", "Click Save (fnSaveTransferChecklist()); wait for the toast",
                "'Transfer checklist saved successfully.' toast", toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Checklist · Result", ok ? toast : "Not confirmed");
    }
}
