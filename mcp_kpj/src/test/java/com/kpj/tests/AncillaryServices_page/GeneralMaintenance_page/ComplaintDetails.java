package com.kpj.tests.AncillaryServices_page.GeneralMaintenance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;

// The page object is also named ComplaintDetails (and so is the Equipment/Asset test) — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; General Maintenance &gt; <b>Complaint Details</b>.
 *
 * <ol>
 *   <li>Open the screen and read the complaints already listed; click <b>New</b> (the form opens with the next Complaint No.).</li>
 *   <li>Select the <b>Complaint Type</b> ("Damage") and the <b>Department</b> ("MEDICAL WARD").</li>
 *   <li>In the Complaint Details block: select <b>Reported To</b> and <b>Verified By</b>, enter the <b>Complaint Details</b> text, select <b>Reported By</b> and <b>Status</b>.</li>
 *   <li>Click <b>Add</b> (the entry appears in the Complaint Details table), then <b>Save</b>.</li>
 *   <li>Verify the success toast, and that the new Complaint No. is in the list.</li>
 * </ol>
 *
 * <p>Dates and times are left at their defaults (today / now), and so is the optional allocation (In house, no employee). Override the choices with {@code -Dcomplaint.type=},
 * {@code -Dcomplaint.department=} and {@code -Dcomplaint.details=}.</p>
 *
 * <p>This test SAVES A REAL MAINTENANCE COMPLAINT on every run; it stays (it is dealt with on the Complaint Resolution screen).</p>
 */
public class ComplaintDetails extends DevHisBase {

    public ComplaintDetails() { super("AncillaryServices_GeneralMaintenance_ComplaintDetails"); }

    public static void main(String[] args) {
        ComplaintDetails t = new ComplaintDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - General Maintenance - Complaint Details", "Ancillary Services > General Maintenance > Complaint Details",
                "Open; New; select complaint type and department; select reported to, verified by; enter the complaint details; select reported by and status; Add; Save; verify the success toast. SAVES A REAL MAINTENANCE COMPLAINT.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintDetails cd = new com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintDetails(page);

        boolean on = cd.navigateViaMenu();
        step("Open Complaint Details", "Click Ancillary Services -> General Maintenance -> Complaint Details", "The General Maintenance Complaint Details screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Complaint Details not reached"); return; }

        List<String[]> rows = cd.awaitExisting();
        boolean ready = cd.gridReady();
        step("Read the complaints already listed", "Read the complaint list", "The existing complaints are known",
                !ready ? "The list did not load" : rows.isEmpty() ? "The list is empty (pager: \"" + cd.pagerText() + "\")"
                        : rows.size() + " complaint(s) listed (pager: \"" + cd.pagerText() + "\"): " + rows.stream().map(r -> r[0] + " (" + r[1] + ", " + r[2] + ")").toList(),
                ready ? "PASS" : "FAIL");
        if (!ready) { addSummary("Result", "FAILED — the list did not load"); return; }

        boolean opened = cd.clickNew();
        step("Click New", "Click New", "The complaint form opens with the next Complaint No.",
                opened ? "Form opened (" + page.url() + "), Complaint No. " + cd.lastComplaintNo : "Problem — " + cd.problems, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — New: " + cd.problems); return; }

        String type = cd.selectComplaintType(System.getProperty("complaint.type", com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintDetails.DEFAULT_TYPE));
        step("Select complaint type", "Select the Complaint Type", "A complaint type is selected",
                cd.ok() ? "Complaint Type \"" + type + "\" selected (one of two human-named types; the other 36 are generated \"Auto complaint type …\" names)" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — complaint type: " + cd.problems); return; }

        String dept = cd.selectDepartment(System.getProperty("complaint.department", com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintDetails.DEFAULT_DEPARTMENT));
        step("Select department", "Select the Department", "A department is selected",
                cd.ok() ? "Department \"" + dept + "\" selected (a ward, where a maintenance complaint such as a leak would be raised)" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — department: " + cd.problems); return; }

        String to = cd.selectReportedTo();
        step("Select reported to", "In Complaint Details, select Reported To", "A person is selected", cd.ok() ? "Reported To \"" + to + "\" selected" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — reported to: " + cd.problems); return; }

        String ver = cd.selectVerifiedBy();
        step("Select verified by", "Select Verified By", "A person is selected", cd.ok() ? "Verified By \"" + ver + "\" selected" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — verified by: " + cd.problems); return; }

        String text = System.getProperty("complaint.details", com.kpj.pages.AncillaryServices_page.GeneralMaintenance_page.ComplaintDetails.DEFAULT_DETAILS);
        boolean typed = cd.enterDetails(text);
        step("Enter complaint details", "Enter the Complaint Details text", "The text is accepted", typed ? "Entered: \"" + text + "\"" : "Problem — " + cd.problems, typed ? "PASS" : "FAIL");
        if (!typed) { addSummary("Result", "FAILED — complaint details: " + cd.problems); return; }

        String by = cd.selectReportedBy();
        step("Select reported by", "Select Reported By", "A person is selected", cd.ok() ? "Reported By \"" + by + "\" selected (different from Reported To and Verified By)" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — reported by: " + cd.problems); return; }

        String status = cd.selectStatus();
        step("Select status", "Select Status", "A status is selected",
                cd.ok() ? "Status \"" + status + "\" selected (all 30 statuses are generated \"Auto complaint status …\" names, so the oldest = lowest-numbered one is used)" : "Problem — " + cd.problems, cd.ok() ? "PASS" : "FAIL");
        if (!cd.ok()) { addSummary("Result", "FAILED — status: " + cd.problems); return; }

        boolean added = cd.clickAdd();
        step("Click Add", "Click Add", "The entry is added to the Complaint Details table",
                added ? "Add put this row into the table: " + cd.lastAddedRow : "Problem — " + cd.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — Add: " + cd.problems); return; }

        String toast = cd.saveAndGetToast();
        boolean ok = cd.ok() && cd.saveSucceeded();
        step("Click Save", "Click Save", "The complaint is saved",
                cd.problems.isEmpty() ? "Save clicked" + (cd.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + cd.lastDialog + "\"") : "Problem — " + cd.problems,
                cd.problems.isEmpty() ? "PASS" : "FAIL");

        // the screen returns to the list after a save: the new complaint should be in it
        String listed = "";
        boolean back = false;
        if (ok) {
            back = cd.returnToList();
            for (int attempt = 0; attempt < 2 && listed.isEmpty(); attempt++) {
                long end = System.currentTimeMillis() + 10000;
                while (System.currentTimeMillis() < end && listed.isEmpty()) {
                    for (String[] r : cd.existing()) if (r[0].equalsIgnoreCase(cd.lastComplaintNo)) listed = r[0] + " (" + r[1] + ", " + r[2] + ")";
                    if (listed.isEmpty()) page.waitForTimeout(500);
                }
                if (listed.isEmpty()) cd.clickSearch();
            }
        }
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (cd.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + cd.lastSaveMessage + "\"")
                        + (back ? "; back on the list" : "; the list was not reached")
                        + (listed.isEmpty() ? "; the new complaint " + cd.lastComplaintNo + " was not found in the list afterwards" : "; the list now shows " + listed)
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + cd.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + cd.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        addSummary("Complaint No.", cd.lastComplaintNo);
        addSummary("Complaint Type / Department", type + " / " + dept);
        addSummary("Reported By / To / Verified By", by + " / " + to + " / " + ver);
        addSummary("Status", status);
        addSummary("Complaint Details", text);
        addSummary("In the list afterwards", listed.isEmpty() ? "(not found)" : listed);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
