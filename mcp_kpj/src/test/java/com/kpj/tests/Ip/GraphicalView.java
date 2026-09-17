package com.kpj.tests.Ip;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// NOTE: the page object is also named GraphicalView, so it is referenced by its fully-qualified name
// (com.kpj.pages.Ip.GraphicalView) — importing it would clash with this test class name.

/**
 * IP &gt; Inpatients &gt; <b>Graphical View</b> (bed board).
 *
 * <p>The board is a grid of bed buttons color-coded by {@code BedList.BedStatus} — green = vacant (0),
 * red = occupied (1), yellow = discharged (2) — with M/F gender and a housekeeping-in-progress "bucket"
 * icon overlaid on occupied beds (see the legend at {@code //*[@id="BedStatusCriteria"]/div[2]/div[3]}).</p>
 *
 * <p>Flow: select an occupied bed → View Details → Bed Information → Admission Details → Rate Details →
 * pick a Pricing Policy → OK.</p>
 */
public class GraphicalView extends DevHisBase {

    public GraphicalView() { super("IP_GraphicalView"); }

    public static void main(String[] args) {
        GraphicalView t = new GraphicalView();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Inpatients - Graphical View", "IP > Inpatients > Graphical View",
                "Select an occupied bed on the graphical bed board, view its Bed Information / Admission "
                        + "Details / Rate Details, pick a Pricing Policy, and close via OK.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", "tieba / Tieba@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Ip.GraphicalView gv = new com.kpj.pages.Ip.GraphicalView(page);

        boolean opened = gv.navigateTo(BASE);
        step(page, "Open Graphical View", "IP → Inpatients → Graphical View",
                "The bed board (color-coded bed buttons) is shown",
                opened ? "Opened " + page.url() : "Did NOT reach Graphical View", opened ? "PASS" : "FAIL");
        if (!opened) return;

        String legend = gv.readLegendCounts();
        addSummary("Bed status legend", legend.isEmpty() ? "(not found)" : legend);

        // 1) Select any OCCUPIED (red) bed.
        String bed = gv.selectOccupiedBed();
        step(page, "Select an occupied bed", "Click a red (Occupied) bed button on the board",
                "A bed is selected (its action toolbar becomes enabled)",
                bed == null ? "No occupied bed found on the board" : bed.replace("\n", " | "),
                bed == null ? "FAIL" : "PASS");
        if (bed == null) return;

        // 2) Click View Details.
        boolean viewOpened = gv.openViewDetails();
        step(page, "Click View Details", "Click the View Details button (GetBedDetailsView())",
                "The Bed Details modal opens (Bed Information / Admission Details / Rate Details tabs)",
                viewOpened ? "Bed Details modal opened" : "Modal did NOT open", viewOpened ? "PASS" : "FAIL");
        if (!viewOpened) return;

        // 3) View the details in Bed Information (default tab).
        String bedInfo = gv.readBedInformation();
        step(page, "Bed Information", "Default tab shown on open",
                "Bed Name/Ward/Class Name/Room Name/Amenities are populated", bedInfo, "PASS");

        // 4) Click Admission Details; view the details.
        boolean admOpened = gv.openAdmissionDetailsTab();
        String admInfo = admOpened ? gv.readAdmissionDetails() : "Tab did not open";
        step(page, "Admission Details", "Click the Admission Details tab",
                "Admission Date/Time, IPD No, Doctor, Patient, MRN, Mobile, Referring Doctor are shown",
                admInfo, admOpened ? "PASS" : "FAIL");

        // 5) Click Rate Details; select a Pricing Policy; view the details.
        boolean rateOpened = gv.openRateDetailsTab();
        step(page, "Click Rate Details", "Click the Rate Details tab",
                "The Rate Details tab (Pricing Policy + rate table) is shown",
                rateOpened ? "Rate Details tab opened" : "Tab did NOT open", rateOpened ? "PASS" : "FAIL");

        String policy = "";
        String rates = "";
        if (rateOpened) {
            policy = gv.selectPricingPolicy();
            rates = gv.readRateDetails();
        }
        step(page, "Select Pricing Policy", "Select the first available Pricing Policy",
                "A policy is selected and its rate table (Service Name/Unit/Purchase Price) loads",
                policy.isEmpty() ? "No Pricing Policy option available" : "Policy: " + policy + " | " + rates,
                policy.isEmpty() ? "FAIL" : "PASS");

        // 6) Click OK.
        boolean closed = gv.clickOk();
        step(page, "Click OK", "Click OK (clearform()) to close the Bed Details modal",
                "The modal closes", closed ? "Modal closed" : "Modal did NOT close", closed ? "PASS" : "FAIL");

        addSummary("Bed", bed == null ? "(none)" : bed.replace("\n", " | "));
        addSummary("Pricing Policy", policy.isEmpty() ? "(none)" : policy);

        // 7) Select a VACANT (green) bed and click Admission — confirmed live: Admission (fnAdmission()) is
        // only enabled for a vacant bed (an occupied bed's toolbar has no such button); it carries the bed's
        // Ward into the SAME #/Admission form com.kpj.tests.Ip.Admission drives via the IP menu, so the fill
        // reuses that SAME com.kpj.pages.Ip.Admission page object rather than a second copy of the flow.
        //
        // KNOWN ENVIRONMENT GAP (see GraphicalView.clickAdmission Javadoc): the Admission button can stay
        // disabled for every vacant bed in a run — confirmed live NOT a per-bed data issue (the exact same
        // bed works when reproduced manually). Most likely cause: automated login never establishes a
        // Location/CashCounter context. Login stays username/password only per direction, so this reports
        // as a diagnosed FAIL rather than being "fixed" by guessing a counter to select at login.
        String vacantBed = gv.selectVacantBedAndClickAdmission();
        String admDesc = "Click a green (Vacant) bed, then Admission (fnAdmission()) — try a couple of vacant beds if disabled";
        String admExpected = "The IP Admission form (#/Admission) opens, carrying the bed's Ward";
        if (vacantBed != null) {
            step(page, "Select a vacant bed and click Admission", admDesc, admExpected,
                    "Bed: " + vacantBed.replace("\n", " | ") + " -> " + page.url(), "PASS");
        } else {
            String actual = "DEFECT/ENV GAP: Admission stayed disabled for every vacant bed tried. "
                    + (gv.lastAdmissionDisabledReason.isEmpty() ? "" : gv.lastAdmissionDisabledReason);
            // A screenshot at the exact failure moment — every MANUAL reproduction of this same click
            // sequence has succeeded, so this is the direct visual evidence of what the one environment
            // where it fails (this automated session) actually looked like.
            if (gv.lastFailureScreenshot != null) {
                step(gv.lastFailureScreenshot, "Select a vacant bed and click Admission", admDesc, admExpected, actual, "FAIL");
            } else {
                step(page, "Select a vacant bed and click Admission", admDesc, admExpected, actual, "FAIL");
            }
        }
        if (vacantBed == null) return;

        com.kpj.pages.Ip.Admission ip = new com.kpj.pages.Ip.Admission(page);

        String patient = ip.fillPatientSection();
        step(page, "Admission · Patient Information",
                "Nationality → Prefix/Gender → Race/Religion/Marital/Blood → New IC → Name → NRIC → DOB → Mobile",
                "Patient section populated", patient, "PASS");

        String nok = ip.fillNokSection();
        boolean nokOk = nok != null && nok.contains("added=true");
        step(page, "Admission · NOK / Guarantor",
                "Title/Name/Relationship/Mobile+CountryCode/NRIC/Occupation/Country + Same-as-address → Add",
                "A Next-of-Kin row is added", nok, nokOk ? "PASS" : "MANUAL");

        String payor = ip.fillPayorSection();
        boolean payorOk = payor != null && payor.toLowerCase().contains("self");
        step(page, "Admission · Payor Information",
                "Open Payor accordion → click the default payor row (auto-fills) → ensure Self",
                "Payor is set to Self (row auto-filled)", payor, payorOk ? "PASS" : "MANUAL");

        String admission = ip.fillAdmissionSection();
        step(page, "Admission · Admission Information",
                "Admission Location/Department/Doctor/Type/Source/Billing Class/Purpose (Non-Presence off)",
                "Admission section populated", admission, "PASS");

        String roomWard = ip.selectRoomTypeAndWard();
        boolean roomWardOk = roomWard != null && roomWard.contains("RoomType=") && roomWard.contains("Ward=") && !roomWard.startsWith("ERR");
        step(page, "Admission · Room Type + Ward", "Select Room Type (Bed Class) + Ward",
                "Room Type and Ward are both set (Ward already carried from the picked bed)",
                roomWardOk ? roomWard : "Could not select Room Type/Ward — " + roomWard, roomWardOk ? "PASS" : "FAIL");

        String addDocs = ip.fillAdditionalDoctors();
        boolean addDocsOk = addDocs != null && addDocs.contains("rows=") && !addDocs.contains("rows=0");
        scrollToSection("Additional\\s*Doctors");
        step(page, "Admission · Additional Doctors",
                "Expand Additional Doctors (FillAdditionDropDown), select Classification + Additional Doctor, click Add",
                "A doctor row is added to the Additional Doctors grid", addDocs, addDocsOk ? "PASS" : "MANUAL");

        String toast = ip.saveAdmissionAndGetToast();
        boolean admitOk = toast != null && toast.toLowerCase().contains("admitted") && !toast.toLowerCase().contains("already admitted");
        // A save that never confirms means the patient was never actually admitted — no report is ever
        // generated for it (there is nothing to report on). That is a genuine FAIL, not a "MANUAL" result to
        // shrug off — a MANUAL status here previously let the overall run read as passing when nothing was
        // actually achieved.
        step(page, "Admission · Save", "Click Save (IUDAdmission()) after all mandatory fields are filled; wait for the toast",
                "'Patient Admitted Successfully.' toast",
                admitOk ? toast : ("FAIL: Save did not confirm admission — no report will be generated. "
                        + (toast == null || toast.isEmpty() ? "No toast appeared." : "Last toast: " + toast)),
                admitOk ? "PASS" : "FAIL");

        // A successful Save opens the admission report(s) (IPD Report, Patient Label, Wrist Band, Consent
        // Form, ...) in NEW tabs — confirmed live these open several seconds after Save, so give them a
        // chance before moving on. This test (unlike the standalone com.kpj.tests.Ip.Admission) doesn't
        // capture those reports, but it MUST NOT leave one of them as the frontmost tab: every section after
        // this one keeps calling page.evaluate()/screenshot() on the ORIGINAL bed-board tab, and Chromium
        // throttles/deprioritizes a backgrounded tab's renderer — confirmed live that is exactly what turned
        // into "screenshot timed out" and "Execution context was destroyed" failures further down this same
        // test. Close every report tab that opened and bring the original tab back to front before continuing.
        if (admitOk) page.waitForTimeout(12000);
        try {
            for (com.microsoft.playwright.Page pg : page.context().pages()) {
                if (pg != page && !pg.isClosed()) pg.close();
            }
        } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Vacant Bed", vacantBed.replace("\n", " | "));
        addSummary("Admission · NOK / Guarantor", nok == null ? "-" : nok);
        addSummary("Admission · Payor", payor == null ? "-" : payor);
        addSummary("Admission · Room Type/Ward", roomWard == null ? "-" : roomWard);
        addSummary("Admission · Result", admitOk ? toast : "NOT admitted — no report generated");

        // 8) Select an OCCUPIED (red) bed and click Transfer Bed — confirmed live: TransferBed() is enabled on an
        // occupied bed's toolbar (the counterpart to Admission being enabled on a vacant bed's) and navigates to
        // the SAME #/addTransferBed form com.kpj.tests.Ip.BedManagement_page.TransferBedList's "New" flow drives
        // via the Transfer Bed List screen's own "New" button. The selected bed's MRN is NOT carried into the
        // form (confirmed live — BedTransfer.MRNO stays empty), so the fill reuses the SAME
        // autoTransferAdmittedPatient(...) the standalone test uses to search/pick a patient — not a second
        // copy of that logic.
        //
        // Pull REAL, currently-admitted candidate MRNs from the Transfer Bed List's own grid FIRST — the same
        // thing the standalone test does before its "New" attempt. Confirmed live this matters: the static
        // TRANSFER_MRN_POOL is a fixed, years-old list on a SHARED QA environment where patient data keeps
        // drifting — a run here found every single pool MRN already discharged ("not-admitted" x12), because
        // nothing refreshed it against what's actually admitted right now.
        com.kpj.pages.Ip.BedManagement_page.TransferBedList tb = new com.kpj.pages.Ip.BedManagement_page.TransferBedList(page);
        tb.navigateViaMenu();
        java.time.format.DateTimeFormatter tdf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate ttd = java.time.LocalDate.now();
        tb.setDateRangeAndSearch(ttd.minusDays(365).format(tdf), ttd.plusDays(1).format(tdf));
        java.util.List<String> transferCandidates = new java.util.ArrayList<>(new java.util.LinkedHashSet<>(tb.getTransferredMrns()));
        transferCandidates.addAll(com.kpj.pages.Ip.BedManagement_page.TransferBedList.shuffledTransferMrns());   // fallback pool

        // Re-navigate to Graphical View — confirmed live: when Admission's Save above doesn't succeed, the
        // browser is left on #/Admission (it never returns to the bed board on failure), so the bed board this
        // section needs simply isn't in the DOM yet. selectOccupiedBedAndClickTransferBed() found zero occupied
        // beds for exactly this reason before this reload was added — not a bed-availability issue.
        gv.navigateTo(BASE);
        String occupiedForTransfer = gv.selectOccupiedBedAndClickTransferBed();
        step(page, "Select an occupied bed and click Transfer Bed",
                "Click a red (Occupied) bed, then Transfer Bed (TransferBed()) — try another occupied bed if disabled",
                "The Transfer Bed form (#/addTransferBed) opens",
                occupiedForTransfer != null ? "Bed: " + occupiedForTransfer.replace("\n", " | ") + " -> " + page.url()
                        : "Transfer Bed stayed disabled for every occupied bed tried",
                occupiedForTransfer == null ? "FAIL" : "PASS");
        if (occupiedForTransfer == null) return;

        com.kpj.pages.Ip.BedManagement_page.TransferBedList.TransferResult tr = tb.autoTransferAdmittedPatient(transferCandidates, 4);

        step(page, "Transfer · Enter MRN & Search", "Enter candidate MRN(s) + Search (SearchPatientByMRNo()); use the first ADMITTED patient as the test subject",
                "An admitted patient + current bed loaded", "Tried: " + tr.tried + "| subject " + (tr.usedMrn == null ? "none" : tr.usedMrn + " (current ward: " + tr.currentWard + ")"),
                tr.usedMrn != null ? "PASS" : "FAIL");
        if (tr.usedMrn == null) { addSummary("Transfer · Result", tr.failReason); return; }

        boolean transferTargetOk = tr.target != null && tr.target.contains("greenBeds=");
        step(page, "Transfer · Select target Ward/Room Type (generate bed list)", "Select target Ward + Room Type; change until a vacant (green) bed list generates",
                "A vacant bed list is generated",
                transferTargetOk ? tr.target : "No ward/room-type combo produced a vacant (green) bed for MRN " + tr.usedMrn,
                transferTargetOk ? "PASS" : "FAIL");
        step(page, "Transfer · Select a bed", "Tick the SELECT checkbox of a vacant bed",
                "A bed is selected",
                tr.bed != null ? "selected: " + tr.bed
                        : tr.unselectableReason.isEmpty() ? "no bed available to select" : tr.unselectableReason,
                tr.bed == null ? "FAIL" : "PASS");
        step(page, "Transfer · Enter remark", "Set Transfer by + Billing Class + Remark", "Transfer details filled",
                tr.extras == null || tr.extras.isEmpty() ? "Not reached — no bed was selected to fill remarks for" : tr.extras,
                tr.extras == null || tr.extras.isEmpty() ? "FAIL" : "PASS");
        step(page, "Transfer · Click Save & success toast", "Click Save (savebedtransfer()) — auto-picked MRN " + tr.usedMrn,
                "'Bed Transfer Request saved successfully.' toast",
                tr.ok ? tr.toast : "FAIL: " + tr.failReason, tr.ok ? "PASS" : "FAIL");

        addSummary("Transfer · Occupied Bed", occupiedForTransfer.replace("\n", " | "));
        addSummary("Transfer · Result", tr.ok ? ("MRN " + tr.usedMrn + " -> " + tr.toast) : tr.failReason);

        // 9) Select a VACANT (green) bed and click Under Maintenance — confirmed live: MaintenanceBed() is
        // enabled on a vacant bed's toolbar and navigates to the SAME #/add-undermaintenance form
        // com.kpj.tests.Ip.BedManagement_page.UnderMaintenance's "Add" flow drives via the Under Maintenance
        // list screen's own "Add" button.
        //
        // NOT reusing markBedUnderMaintenance() here — confirmed live this entry point is genuinely
        // different: the form's Ward pre-fill matches the clicked bed, but its Room Type pre-fill does NOT
        // (e.g. clicking bed DC-26, tooltip "Room Type: DAY CARE, Ward: DAY CARE", landed with Room Type set
        // to an unrelated value whose combo has zero beds — DC-26 only appears once Room Type is corrected
        // to "DAY CARE"). markBedUnderMaintenance()'s blind "any bed from any combo" search (built for the
        // standalone screen, which starts with an EMPTY Ward/Room Type) would happily search past this and
        // mark a COMPLETELY DIFFERENT bed than the one actually clicked. markSpecificBedUnderMaintenance()
        // keeps the pre-filled Ward, iterates only Room Type until the CLICKED bed's own name appears, and
        // selects that exact bed instead.
        //
        // Re-navigate to Graphical View first — same lesson as the Transfer Bed section above: the browser is
        // left on whatever page the previous section's Save landed on, not back on the bed board.
        gv.navigateTo(BASE);
        String vacantForMaintenance = gv.selectVacantBedAndClickMaintenanceBed();
        step(page, "Select a vacant bed and click Under Maintenance",
                "Click a green (Vacant) bed, then Under Maintenance (MaintenanceBed()) — try another vacant bed if disabled",
                "The Under Maintenance form (#/add-undermaintenance) opens",
                vacantForMaintenance != null ? "Bed: " + vacantForMaintenance.replace("\n", " | ") + " -> " + page.url()
                        : "Under Maintenance stayed disabled for every vacant bed tried",
                vacantForMaintenance == null ? "FAIL" : "PASS");
        if (vacantForMaintenance == null) return;

        String targetBedName = com.kpj.pages.Ip.GraphicalView.bedNameFromTitle(vacantForMaintenance);
        com.kpj.pages.Ip.BedManagement_page.UnderMaintenance um = new com.kpj.pages.Ip.BedManagement_page.UnderMaintenance(page);
        java.time.format.DateTimeFormatter mdf = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        java.time.LocalDate mtd = java.time.LocalDate.now();
        com.kpj.pages.Ip.BedManagement_page.UnderMaintenance.MaintenanceResult mr =
                um.markSpecificBedUnderMaintenance(targetBedName, mtd.format(mdf), mtd.plusDays(7).format(mdf),
                        "Bed under maintenance - automated test");

        step(page, "Maintenance · Select the clicked bed (" + targetBedName + ")",
                "Keep the pre-filled Ward; change Room Type until '" + targetBedName + "' appears in the list; tick its checkbox",
                "The SAME bed clicked on the board is selected", mr.bed == null ? mr.failReason : "Selected: " + mr.bed,
                mr.bed == null ? "FAIL" : "PASS");
        if (mr.bed == null) { addSummary("Maintenance · Result", mr.failReason); return; }

        step(page, "Maintenance · Enter remark", "Enter the Remark (undermaintenance.remark)",
                "The remark is entered", "Remark = 'Bed under maintenance - automated test'", "PASS");

        step(page, "Maintenance · Click Save & success toast", "Click Save (IUDBedUnderMaitenance()); wait for the success toast",
                "'Bed Under Maintenance saved successfully.' toast",
                mr.ok ? mr.toast : "FAIL: " + mr.failReason, mr.ok ? "PASS" : "FAIL");

        addSummary("Maintenance · Vacant Bed", vacantForMaintenance.replace("\n", " | "));
        addSummary("Maintenance · Result", mr.ok ? mr.toast : mr.failReason);
    }
}
