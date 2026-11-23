package com.kpj.tests.AncillaryServices_page.DMSViewer_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named DMSViewer — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; <b>DMS Viewer</b> ({@code #/DMSViewer}).
 *
 * <ol>
 *   <li>Click the SECOND (right-hand) symbol beside the MRN box - the patient-search symbol, not the find-patient magnifier - to open the <b>Patient Search</b> popup.</li>
 *   <li>In the popup, the <b>Visit Date</b> row (ticked): enter the From date 1 month 15 days before the To date (inside "1 or 2 months"), click <b>Search</b>.</li>
 *   <li>In <b>Advance Filters</b>, tick the Select box of the first row.</li>
 *   <li>Under <b>Files Found</b>: enter a unique <b>Remark</b>, click <b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p>This test SAVES A REAL REMARK for the chosen patient on every run, replacing the remark held before (which the report names).</p>
 */
public class DMSViewer extends DevHisBase {

    public DMSViewer() { super("AncillaryServices_DMSViewer"); }

    public static void main(String[] args) {
        DMSViewer t = new DMSViewer();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - DMS Viewer", "Ancillary Services > DMS Viewer",
                "Patient-search symbol beside MRN -> Patient Search popup; Visit Date From 1.5 months before To; Search; select a patient; under Files Found enter a remark; Save; verify the success toast. SAVES A REAL REMARK.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.DMSViewer_page.DMSViewer dv = new com.kpj.pages.AncillaryServices_page.DMSViewer_page.DMSViewer(page);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        boolean on = dv.navigateViaMenu();
        List<String> buttons = on ? dv.mrnButtons() : List.of();
        step("Open DMS Viewer", "Click Ancillary Services -> DMS Viewer", "The DMS Viewer screen is shown",
                on ? "Opened " + page.url() + "; the buttons beside the MRN box, left to right: " + buttons : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — DMS Viewer not reached"); return; }

        boolean popup = dv.openPatientSearch();
        step("Click the patient-search symbol", "In the MRN column, click the second (right-hand) symbol beside the MRN box - the patient-search symbol, not the find-patient magnifier", "The Patient Search popup opens",
                popup ? "The right-hand symbol (" + (buttons.size() > 1 ? buttons.get(buttons.size() - 1) : "OpenPopupScreen()") + ") was clicked and the Patient Search popup opened" : "Problem — " + dv.problems, popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED — popup: " + dv.problems); return; }

        LocalDate to = dv.defaultToDate();
        LocalDate from = to.minusMonths(1).minusDays(15);
        boolean ticked = dv.ensureVisitDateTicked();
        boolean fromOk = ticked && dv.enterVisitFrom(from);
        step("Enter the visit date From", "In the Visit Date row (the ticked one), enter From " + from.format(dmy) + " (1 month 15 days before To " + to.format(dmy) + ")", "From is 1 to 2 months before To",
                fromOk ? "Visit Date is ticked; From \"" + dv.lastFrom + "\" entered through the calendar and To is \"" + dv.lastTo + "\" (From is 1 month and 15 days before To, inside the 1 to 2 months asked); the boxes and the screen's model hold them"
                        : "Problem — " + (ticked ? dv.problems : "the Visit Date tick box is not ticked " + dv.problems), fromOk ? "PASS" : "FAIL");
        if (!fromOk) { addSummary("Result", "FAILED — visit date: " + dv.problems); return; }

        boolean searched = dv.clickSearch();
        List<String[]> rows = dv.resultRows();
        step("Click Search", "Click Search in the popup", "The Advance Filters table lists patients",
                searched && !rows.isEmpty() ? "Search clicked; Advance Filters lists " + rows.size() + " row(s): " + rows.stream().limit(3).map(r -> r[0] + " / " + r[1] + " " + r[2]).toList() + (rows.size() > 3 ? " ..." : "")
                        : searched ? "FAILS because Search listed no patient for " + dv.lastFrom + " - " + dv.lastTo : "Problem — " + dv.problems, searched && !rows.isEmpty() ? "PASS" : "FAIL");
        if (!searched || rows.isEmpty()) { addSummary("Result", "FAILED — search: " + (dv.problems.isEmpty() ? "no patient listed" : dv.problems)); return; }

        String[] row = rows.get(0);
        boolean picked = dv.selectRow(0);
        step("Tick the checkbox to select a patient", "In Advance Filters, tick the Select box of row 1 (" + row[0] + " " + row[2] + ")", "The patient is selected: the popup closes and the patient loads",
                picked ? "Row 1 ticked (MRN " + row[0] + ", " + row[1] + " " + row[2] + "); the popup closed, the MRN box holds \"" + dv.lastMrn + "\" and the screen shows \"" + dv.lastBanner + "\"" : "Problem — " + dv.problems, picked ? "PASS" : "FAIL");
        if (!picked) { addSummary("Result", "FAILED — patient: " + dv.problems); return; }

        String remark = "Scanned records reviewed in DMS Viewer, QA check " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean typed = dv.enterRemark(remark);
        step("Enter remark", "Under Files Found, enter the Remarks (" + remark + ")", "The remark is accepted",
                typed ? "Remark \"" + remark + "\" entered under Files Found, replacing the remark the patient had before (\"" + (dv.lastRemarkBefore.isEmpty() ? "none" : dv.lastRemarkBefore) + "\")" : "Problem — " + dv.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — remark: " + dv.problems); return; }

        String toast = dv.saveAndGetToast();
        boolean ok = dv.ok() && dv.saveSucceeded();
        step("Click Save", "Click Save", "The remark is saved",
                dv.problems.isEmpty() ? "Save clicked" + (dv.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + dv.lastDialog + "\"") : "Problem — " + dv.problems, dv.problems.isEmpty() ? "PASS" : "FAIL");

        boolean savedByServer = dv.savedByServer();
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dv.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dv.lastSaveMessage.trim() + "\"")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the remark (ResultStatus 1, \"" + dv.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dv.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dv.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // not a step: read the remark the server holds for the patient with the call the screen itself makes
        String reloaded = (ok || savedByServer) ? dv.savedRemark() : "(not checked)";
        boolean kept = reloaded.equals(remark);

        addSummary("Visit date From / To", dv.lastFrom + " / " + dv.lastTo + " (From is 1 month 15 days before To)");
        addSummary("Patient", dv.lastMrn + " - " + dv.lastBanner);
        addSummary("Remark before", dv.lastRemarkBefore.isEmpty() ? "(none)" : dv.lastRemarkBefore);
        addSummary("Remark saved", remark);
        addSummary("Save request", dv.lastSaveRequest.isEmpty() ? "(not seen)" : dv.lastSaveRequest);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus 1" + (dv.lastSaveMessage.isEmpty() ? "" : ", \"" + dv.lastSaveMessage.trim() + "\"") + ")" : "(no save confirmed" + (dv.lastSaveMessage.isEmpty() ? "" : ": " + dv.lastSaveMessage) + ")");
        addSummary("Remark the server holds afterwards (FetchRemark)", reloaded.isEmpty() ? "(empty)" : reloaded + (kept ? "  [= the remark saved]" : "  [differs from the remark saved]"));
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast)));
    }
}
