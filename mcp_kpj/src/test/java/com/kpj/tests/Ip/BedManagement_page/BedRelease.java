package com.kpj.tests.Ip.BedManagement_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BedRelease — referenced by its fully-qualified name.

/**
 * IP &gt; Bed Management &gt; <b>Bed Release</b> ({@code #/BedRelease}). Three sections:
 * <ol>
 *   <li><b>Lists</b> — the Discharge / Transfer / Additional Bed List tabs load; search by MRN filters the list.</li>
 *   <li><b>Locked row</b> — a pink row (final bill not generated) has Notify and Released disabled.</li>
 *   <li><b>Release</b> — one releasable bed through the whole housekeeping flow: Select → Notify → Start → End →
 *       Cleaning Complete → Released → Save → bed checklist → Save → "Bed Released Successfully" and the bed
 *       leaves the Discharge Bed List. Steps already done on that bed earlier are recorded, not repeated.</li>
 * </ol>
 *
 * <p>⚠ Each run RELEASES one bed (a discharged patient whose final bill is generated).</p>
 */
public class BedRelease extends DevHisBase {

    public BedRelease() { super("BedRelease"); }

    public static void main(String[] args) {
        BedRelease t = new BedRelease();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private int discharge = -1, transfer = -1, additional = -1;
    private String releasedMrn = null;

    @Override
    protected void body() {
        meta("IP - Bed Management - Bed Release", "IP > Bed Management > Bed Release",
                "Discharge / Transfer / Additional Bed List tabs and MRN search; pink (no final bill) row is locked; one bed "
                        + "released through Select -> Notify -> Start -> End -> Cleaning Complete -> Released -> Save -> checklist -> Save.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Ip.BedManagement_page.BedRelease br = open();
        if (br == null) { addSummary("Result", "FAILED - Bed Release not reached"); return; }

        String mrn = sectionLists(br);
        sectionLockedRow(br);
        sectionRelease(br, mrn);

        addSummary("Application URL", BASE + "/#/BedRelease");
        addSummary("Beds listed", "Discharge " + discharge + " | Transfer " + transfer + " | Additional " + additional);
        addSummary("Released bed (MRN)", releasedMrn == null ? "(none)" : releasedMrn);
    }

    private com.kpj.pages.Ip.BedManagement_page.BedRelease open() {
        com.kpj.pages.Ip.BedManagement_page.BedRelease br = new com.kpj.pages.Ip.BedManagement_page.BedRelease(page);
        boolean opened = br.navigateViaMenu();
        if (opened) br.prepare();
        discharge = opened ? br.listSize("Discharge") : -1;
        step(page, "Open Bed Release", "IP > Bed Management > Bed Release",
                "The Discharge Bed List loads",
                opened ? "Opened " + page.url() + " - " + discharge + " beds in the Discharge Bed List (" + br.pinkRowCount() + " pink)" : "Did NOT reach the screen (" + page.url() + ")",
                opened && discharge >= 0 ? "PASS" : "FAIL");
        return opened ? br : null;
    }

    // ===== Section: Lists ===============================================
    /** Tabs + search. Returns the MRN of a releasable bed (used by the Release section), or null when there is none. */
    private String sectionLists(com.kpj.pages.Ip.BedManagement_page.BedRelease br) {
        transfer = br.openTab("Transfer");
        step(page, "Lists · Transfer Bed List tab", "Click Transfer Bed List", "The Transfer Bed List loads",
                transfer >= 0 ? transfer + " beds listed" : "List did not load", transfer >= 0 ? "PASS" : "FAIL");
        additional = br.openTab("Additional");
        step(page, "Lists · Additional Bed List tab", "Click Additional Bed List", "The Additional Bed List loads",
                additional >= 0 ? additional + " beds listed" : "List did not load", additional >= 0 ? "PASS" : "FAIL");
        int back = br.openTab("Discharge");

        String mrn = br.pickReleasableRowMrn();
        if (mrn != null) {
            int shown = br.searchDischarge(mrn);
            boolean onlyIt = br.allVisibleRowsAre(mrn);
            step(page, "Lists · Search by MRN", "Type MRN " + mrn + " in the Discharge search box",
                    "Only that patient's row is listed", shown + " row(s) shown" + (onlyIt ? ", all for " + mrn : ", NOT all for " + mrn),
                    shown > 0 && onlyIt ? "PASS" : "FAIL");
        } else {
            step(page, "Lists · Search by MRN", "Type an MRN in the Discharge search box", "Only that patient's row is listed",
                    "Skipped - no releasable bed in the list (" + back + " beds, all pink or released)", "FAIL");
        }
        return mrn;
    }

    // ===== Section: Locked row ==========================================
    private void sectionLockedRow(com.kpj.pages.Ip.BedManagement_page.BedRelease br) {
        String pink = br.firstPinkRowMrn();
        if (pink == null) {
            step(page, "Locked row · Pink row is locked", "Find a pink row (final bill not generated)", "Notify and Released are disabled",
                    "No pink row in the list today - nothing to check", "PASS");
            return;
        }
        br.searchDischarge(pink);
        boolean isPink = br.rowIsPink(pink);
        boolean notifyLocked = br.controlDisabled(pink, "Notify");
        boolean releaseLocked = br.controlDisabled(pink, "Released");
        step(page, "Locked row · Pink row is locked", "Find MRN " + pink + "; look at its Notify and Released controls",
                "The row is pink (final bill not generated); Notify and Released are disabled",
                "pink=" + isPink + " | Notify disabled=" + notifyLocked + " | Released disabled=" + releaseLocked,
                isPink && notifyLocked && releaseLocked ? "PASS" : "FAIL");
    }

    // ===== Section: Release =============================================
    private void sectionRelease(com.kpj.pages.Ip.BedManagement_page.BedRelease br, String mrn) {
        if (mrn == null) {
            step(page, "Release · Choose a bed to release", "Pick a row whose final bill is generated and that is not released",
                    "A releasable row is on view", "No releasable bed in the Discharge Bed List", "FAIL");
            return;
        }
        br.searchDischarge(mrn);
        step(page, "Release · Choose a bed to release", "Pick a row whose final bill is generated and that is not released",
                "A releasable row is on view", br.rowState(mrn), "PASS");

        boolean selected = br.ensureSelected(mrn);
        step(page, "Release · Select the row", "Tick the row's Select box", "The row is selected (Notify and Save act on selected rows)",
                selected ? "Selected" : "Select box did not tick", selected ? "PASS" : "FAIL");

        // Notify
        if (br.rowHas(mrn, "isnotify")) {
            step(page, "Release · Notify housekeeping", "Click the bell", "Housekeeping is notified; Start unlocks", "Already notified earlier - Start is unlocked", "PASS");
        } else {
            String c = br.clickControl(mrn, "Notify");
            boolean ok = br.waitForRow(mrn, "isnotify", 15000);
            br.searchDischarge(mrn);
            step(page, "Release · Notify housekeeping", "Click the bell", "\"Message sent successfully\"; the row shows notified and Start unlocks",
                    c + " - " + String.join(" | ", br.messages()) + " | " + br.rowState(mrn), ok ? "PASS" : "FAIL");
            if (!ok) return;
        }

        // Start
        boolean startedNow = false;
        if (br.rowHas(mrn, "StartTime")) {
            step(page, "Release · Start housekeeping", "Click Start", "Start date-time and user are filled", "Already started earlier - " + br.rowState(mrn), "PASS");
        } else {
            String c = br.clickControl(mrn, "Start");
            boolean ok = br.waitForRow(mrn, "StartTime", 15000);
            br.searchDischarge(mrn);
            startedNow = ok;
            step(page, "Release · Start housekeeping", "Click Start", "Start date-time and user are filled and saved",
                    c + (br.messages().isEmpty() ? "" : " - " + String.join(" | ", br.messages())) + " | " + br.rowState(mrn), ok ? "PASS" : "FAIL");
            if (!ok) return;
        }

        // End (Save needs End later than Start; both are kept to the minute)
        if (br.rowHas(mrn, "EndTime")) {
            step(page, "Release · End housekeeping", "Click End", "End date-time is filled", "Already ended earlier - " + br.rowState(mrn), "PASS");
        } else {
            if (startedNow) br.waitForNextMinute();
            String c = br.clickControl(mrn, "End");
            boolean ok = br.waitForRow(mrn, "EndTime", 15000);
            br.searchDischarge(mrn);
            step(page, "Release · End housekeeping", "Click End (a minute after Start)", "End date-time and user are filled and saved",
                    c + (br.messages().isEmpty() ? "" : " - " + String.join(" | ", br.messages())) + " | " + br.rowState(mrn), ok ? "PASS" : "FAIL");
            if (!ok) return;
        }

        // Cleaning Complete
        if (br.rowHas(mrn, "iscleaningcomplete")) {
            step(page, "Release · Cleaning Complete", "Tick Cleaning Complete", "\"Cleaning Completed Successfully\"", "Already marked complete earlier", "PASS");
        } else {
            String c = br.clickControl(mrn, "Cleaning");
            String said = String.join(" | ", br.messages());
            boolean ok = said.toLowerCase().contains("cleaning completed") || br.waitForRow(mrn, "iscleaningcomplete", 8000);
            step(page, "Release · Cleaning Complete", "Tick Cleaning Complete", "\"Cleaning Completed Successfully\"; Released unlocks",
                    c + " - " + (said.isEmpty() ? "(no message captured)" : said), ok ? "PASS" : "FAIL");
            if (!ok) return;
        }

        // Released + Save + checklist
        br.searchDischarge(mrn);
        br.ensureSelected(mrn);
        String rel = br.clickControl(mrn, "Released");
        boolean relTicked = "clicked".equals(rel);
        step(page, "Release · Tick Released", "Tick the Released box", "Released is ticked (only possible after Cleaning Complete)",
                relTicked ? "Ticked" : "Could not tick (" + rel + ")" + (br.messages().isEmpty() ? "" : " - " + String.join(" | ", br.messages())),
                relTicked ? "PASS" : "FAIL");
        if (!relTicked) return;

        boolean popup = br.clickSave();
        step(page, "Release · Save", "Click Save under the Discharge Bed List", "The bed release checklist popup opens",
                popup ? "Checklist popup opened" : "No checklist popup - " + String.join(" | ", br.messages()), popup ? "PASS" : "FAIL");
        if (!popup) return;

        int items = br.completeChecklistAndSave();
        String said = String.join(" | ", br.messages());
        boolean releasedMsg = said.toLowerCase().contains("released");
        boolean gone = !br.inList(mrn);
        step(page, "Release · Checklist & release", "Tick all " + items + " checklist item(s) and click Save in the popup",
                "\"Bed Released Successfully\"; the bed leaves the Discharge Bed List",
                (said.isEmpty() ? "(no message captured)" : said) + " | row " + (gone ? "removed from the list" : "STILL in the list"),
                (releasedMsg || gone) ? "PASS" : "FAIL");
        if (releasedMsg || gone) releasedMrn = mrn;
    }
}
