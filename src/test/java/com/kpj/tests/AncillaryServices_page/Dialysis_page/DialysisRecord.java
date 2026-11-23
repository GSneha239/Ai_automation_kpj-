package com.kpj.tests.AncillaryServices_page.Dialysis_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// The page object is also named DialysisRecord — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Dialysis &gt; <b>Dialysis Record</b> ({@code #/DialysisRecord} -&gt; Add {@code #/add-DialysisRecord}).
 *
 * <ol>
 *   <li>Click <b>Add</b>; open the <b>Dialysis Record Details</b> tab (a real tab; it is the active one when the form opens).</li>
 *   <li>Enter the MRN (default 1100339415, pin another with {@code -Ddialysisrecord.mrn=}) and look it up.</li>
 *   <li>Enter Pre BP / Pre Weight / Post BP / Post Weight, Dialyzer, weight loss, weight gain, hours of dialysis, heparin, blood flow, dialysate, special instructions, ultrafiltration, TBP, transfusion and
 *       the dialysis membrane; select Done By and Department; check the Doctor was autofilled (select one when not); enter the remark.</li>
 *   <li>Click <b>Save</b>, verify the success toast, and verify the new row is in the list - if it does NOT appear, that last step (and so the test) FAILS.</li>
 * </ol>
 *
 * <p>This test SAVES A REAL DIALYSIS RECORD on every run. Note the screen's own save sends no MRN or patient id (only a visit/admission id that its MRN lookup leaves empty), so the saved record may carry no
 * patient; the last step reports what the server holds.</p>
 */
public class DialysisRecord extends DevHisBase {

    public DialysisRecord() { super("AncillaryServices_Dialysis_DialysisRecord"); }

    public static void main(String[] args) {
        DialysisRecord t = new DialysisRecord();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Dialysis - Dialysis Record", "Ancillary Services > Dialysis > Dialysis Record",
                "Add; Dialysis Record Details tab; MRN; vitals, dialyzer, weights, hours, heparin, blood flow, dialysate, instructions, ultrafiltration, TBP, transfusion, membrane; done by; department; doctor (autofilled?); remark; Save; verify the success toast; verify the new row is in the table (fail if it is not). SAVES A REAL RECORD.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Dialysis_page.DialysisRecord dr = new com.kpj.pages.AncillaryServices_page.Dialysis_page.DialysisRecord(page);
        String mrn = System.getProperty("dialysisrecord.mrn", "1100339415");
        LocalDate today = LocalDate.now();
        String yearStart = com.kpj.pages.AncillaryServices_page.Dialysis_page.DialysisRecord.dmy(today.withDayOfYear(1));
        String todayText = com.kpj.pages.AncillaryServices_page.Dialysis_page.DialysisRecord.dmy(today);

        boolean on = dr.navigateViaMenu();
        List<String[]> before = on ? dr.serverRows(yearStart, todayText) : null;
        step("Open Dialysis Record", "Click Ancillary Services -> Dialysis -> Dialysis Record", "The Dialysis Record list is shown",
                on ? "Opened " + page.url() + (before == null ? "" : "; the server holds " + before.size() + " dialysis record(s) for " + yearStart + " - " + todayText) : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Dialysis Record not reached"); return; }
        Set<String> idsBefore = new HashSet<>();
        if (before != null) for (String[] r : before) idsBefore.add(r[0]);

        boolean added = dr.clickAdd();
        step("Click Add", "Click Add", "The Dialysis Record form opens", added ? "The form is open (" + page.url() + "); tabs: " + dr.tabs() : "Problem — " + dr.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — Add: " + dr.problems); return; }

        boolean tab = dr.clickTab("Dialysis Record Details");
        step("Open the Dialysis Record Details tab", "Click the Dialysis Record Details tab (a real tab of the form; the other is Timewise Parameter Details)", "The Dialysis Record Details tab is shown",
                tab ? "The Dialysis Record Details tab is the active one (tabs: " + dr.tabs() + ")" : "Problem — " + dr.problems, tab ? "PASS" : "FAIL");
        if (!tab) { addSummary("Result", "FAILED — tab: " + dr.problems); return; }

        boolean typed = dr.enterMrn(mrn);
        boolean found = typed && dr.clickMrnSearch();
        step("Enter MRN", "Enter the MRN " + mrn + " and click the lookup button beside it", "The patient is loaded",
                found ? "MRN \"" + mrn + "\" entered and looked up: patient id " + dr.lastPatientId + ", \"" + dr.lastPatient + "\"; the visit/admission id the screen will send is " + (dr.visitAdmissionId().isEmpty() ? "EMPTY (the lookup does not fill it)" : dr.visitAdmissionId())
                        : "Problem — " + dr.problems, found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — MRN: " + dr.problems); return; }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean vit = dr.enterAll(new String[][]{{"preBP", "130/85", "Pre BP"}, {"preweight", "72.5", "Pre Weight"}, {"postBP", "120/80", "Post BP"}, {"postweight", "70.8", "Post Weight"}});
        step("Enter Pre BP, Pre Weight, Post BP, Post Weight", "Enter Pre BP 130/85 mm/Hg, Pre Weight 72.5 Kg, Post BP 120/80 mm/Hg, Post Weight 70.8 Kg", "The four values are accepted",
                vit ? "Pre BP \"130/85\", Pre Weight \"72.5\", Post BP \"120/80\", Post Weight \"70.8\" entered; each box and the screen's model hold its value" : "Problem — " + dr.problems, vit ? "PASS" : "FAIL");
        if (!vit) { addSummary("Result", "FAILED — vitals: " + dr.problems); return; }

        String[][] singles = {
                {"dialyzer", "FX80", "Dialyzer", "Enter Dialyzer", "Dialyzer \"FX80\""},
                {"weightlose", "1.7", "Weight Loss", "Enter weight loss", "Weight Loss \"1.7\" Kg (Pre Weight 72.5 - Post Weight 70.8)"},
                {"weightgain", "2.0", "Weight Gain", "Enter weight gain", "Weight Gain \"2.0\" Kg"},
                {"hrofdailysis", "4", "Hrs. of Dialysis", "Enter hours of dialysis", "Hrs. of Dialysis \"4\""},
                {"heparin", "5000", "Heparin", "Enter heparin", "Heparin \"5000\""}};
        for (String[] s : singles) {
            boolean ok = dr.enterAll(new String[][]{{s[0], s[1], s[2]}});
            step(s[3], s[3] + " (" + s[1] + ")", "The value is accepted", ok ? s[4] + " entered; the box and the screen's model hold it" : "Problem — " + dr.problems, ok ? "PASS" : "FAIL");
            if (!ok) { addSummary("Result", "FAILED — " + s[2] + ": " + dr.problems); return; }
        }

        boolean flow = dr.enterAll(new String[][]{{"bloodflow", "300", "Blood Flow"}, {"dialysate", "Bicarbonate", "Dialysate"}});
        step("Enter blood flow and dialysate", "Enter Blood Flow 300 ml and Dialysate Bicarbonate", "Both values are accepted",
                flow ? "Blood Flow \"300\" and Dialysate \"Bicarbonate\" entered" : "Problem — " + dr.problems, flow ? "PASS" : "FAIL");
        if (!flow) { addSummary("Result", "FAILED — blood flow / dialysate: " + dr.problems); return; }

        boolean more = dr.enterAll(new String[][]{{"specialinstruction", "Monitor blood pressure hourly", "Special Instructions"}, {"ultrafiltration", "1.5", "Ultrafiltration"},
                {"tbp", "72", "TBP"}, {"trasfusion", "None", "Transfusion"}});
        step("Enter special instructions, ultrafiltration, TBP and transfusion", "Enter Special Instructions \"Monitor blood pressure hourly\", Ultrafiltration 1.5 Kg, TBP 72 Ltrs and Transfusion \"None\"", "The four values are accepted",
                more ? "Special Instructions, Ultrafiltration \"1.5\", TBP \"72\" and Transfusion \"None\" entered" : "Problem — " + dr.problems, more ? "PASS" : "FAIL");
        if (!more) { addSummary("Result", "FAILED — instructions / ultrafiltration / TBP / transfusion: " + dr.problems); return; }

        boolean mem = dr.enterAll(new String[][]{{"dialysismembrane", "Polysulfone", "Dialysis Membrane"}});
        step("Enter dialysis membrane", "Enter the Dialysis Membrane (Polysulfone)", "The value is accepted", mem ? "Dialysis Membrane \"Polysulfone\" entered" : "Problem — " + dr.problems, mem ? "PASS" : "FAIL");
        if (!mem) { addSummary("Result", "FAILED — dialysis membrane: " + dr.problems); return; }

        String by = dr.selectDoneBy(List.of("Nursing Staff_Raihan"));
        step("Select done by", "Select Done By", "A person is selected", !by.isEmpty() ? "Done By \"" + by + "\" selected (of " + dr.optionNames("DialysisRecord.donebyid").size() + ")" : "Problem — " + dr.problems, !by.isEmpty() ? "PASS" : "FAIL");
        if (by.isEmpty()) { addSummary("Result", "FAILED — done by: " + dr.problems); return; }

        String dept = dr.selectDepartment(List.of("HAEMODIALYSIS"));
        step("Select department", "Select the Department", "A department is selected and the screen loads its doctors",
                !dept.isEmpty() ? "Department \"" + dept + "\" selected (of " + dr.optionNames("DialysisRecord.departmentid").size() + "); the Doctor dropdown now offers " + dr.doctorOptions() : "Problem — " + dr.problems, !dept.isEmpty() ? "PASS" : "FAIL");
        if (dept.isEmpty()) { addSummary("Result", "FAILED — department: " + dr.problems); return; }

        boolean doc = dr.ensureDoctor();
        step("Check the doctor field is autofilled", "Check whether the Doctor field was filled by the screen after the department was chosen; select a doctor if not", "The doctor is set",
                doc ? (dr.doctorWasAutofilled ? "The Doctor was AUTOFILLED by the screen: \"" + dr.lastDoctor + "\" (the first doctor of " + dept + "); no selection was needed"
                                              : "The Doctor was NOT autofilled, so \"" + dr.lastDoctor + "\" was selected") : "Problem — " + dr.problems, doc ? "PASS" : "FAIL");
        if (!doc) { addSummary("Result", "FAILED — doctor: " + dr.problems); return; }

        String remark = "Routine haemodialysis session completed without complications, QA " + stamp;
        boolean rem = dr.enterAll(new String[][]{{"remark", remark, "Remark"}});
        step("Enter remark", "Enter the Remark (" + remark + ")", "The remark is accepted", rem ? "Remark \"" + remark + "\" entered" : "Problem — " + dr.problems, rem ? "PASS" : "FAIL");
        if (!rem) { addSummary("Result", "FAILED — remark: " + dr.problems); return; }

        String toast = dr.saveAndGetToast();
        boolean ok = dr.problems.isEmpty() && dr.saveSucceeded();
        boolean savedByServer = dr.savedByServer();
        step("Click Save", "Click Save", "The record is saved",
                dr.problems.isEmpty() ? "Save clicked" + (dr.lastDialog.isEmpty() ? " (no confirmation was asked)" : "; confirmation answered with \"" + dr.lastDialog + "\"") : "Problem — " + dr.problems, dr.problems.isEmpty() ? "PASS" : "FAIL");

        boolean back = (ok || savedByServer) && dr.awaitList(15000);
        step("Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\"" + (dr.lastSaveMessage.isEmpty() ? "" : " and the server answered \"" + dr.lastSaveMessage.trim() + "\"") + (back ? "; the screen returned to the list" : "; the screen did not return to the list")
                   : savedByServer ? "FAILS because the toast shown on screen is \"" + toast + "\", which is not a success message - although the SERVER SAVED the record (ResultStatus " + dr.lastSaveStatus + ", \"" + dr.lastSaveMessage.trim() + "\")"
                   : toast.isEmpty() ? "FAILS because no toast appeared — " + dr.saveDiagnosis()
                                     : "FAILS because the toast is not a success message: \"" + toast + "\" — " + dr.saveDiagnosis(),
                ok ? "PASS" : "FAIL");

        // RULE (the user's): the new row must appear in the table; if it does not, this step - and so the test - is a FAIL.
        boolean pressedSearch = false;
        List<String[]> rows = new ArrayList<>();
        String[] mine = null;
        if (ok || savedByServer) {
            for (int i = 0; i < 24 && rows.isEmpty(); i++) { rows = dr.rows(); if (rows.isEmpty()) page.waitForTimeout(500); }
            if (rows.isEmpty() && dr.onList()) { pressedSearch = dr.clickListSearch(); rows = dr.rows(); }
            for (String[] r : rows) if (r[0].contains(mrn) && r[2].contains(todayText)) mine = r;
        }
        List<String[]> after = (ok || savedByServer) ? dr.serverRows(yearStart, todayText) : null;
        List<String[]> fresh = new ArrayList<>();
        if (after != null) for (String[] r : after) if (!idsBefore.contains(r[0])) fresh.add(r);
        String freshText = fresh.isEmpty() ? "the server lists no new record for " + yearStart + " - " + todayText
                : "the server DOES hold " + fresh.size() + " new record(s): " + fresh.stream().map(r -> "id " + r[0] + " MRN \"" + (r[1].isEmpty() ? "(blank)" : r[1]) + "\" patient \"" + (r[2].isEmpty() ? "(blank)" : r[2]) + "\" date " + r[3]).toList();
        step("Verify the data row added in the table", "Look for the new row (MRN " + mrn + ", Dialysis Date " + todayText + ") in the Dialysis Record table", "The new row is listed in the table",
                mine != null ? "PASSES because the table shows the new row: MRN " + mine[0] + " / " + mine[1] + " / " + mine[2] + (pressedSearch ? " (after pressing Search; the list did not fill by itself)" : "")
                        : "FAILS because the new row does not appear: the table " + (rows.isEmpty() ? "is BLANK" : "shows " + rows.size() + " row(s) but none for MRN " + mrn + " dated " + todayText + " (" + rows.stream().limit(3).map(r -> r[0] + "/" + r[1] + "/" + r[2]).toList() + ")")
                          + (pressedSearch ? " even after pressing Search" : "") + "; " + freshText
                          + (dr.visitAdmissionId().isEmpty() ? " - the screen's save sent an EMPTY visit/admission id and no MRN or patient id, so the record cannot be tied to the patient" : ""),
                mine != null ? "PASS" : "FAIL");

        addSummary("MRN / Patient", mrn + " / " + dr.lastPatient + " (patient id " + dr.lastPatientId + ")");
        addSummary("Values entered", "Pre BP 130/85, Pre Weight 72.5, Post BP 120/80, Post Weight 70.8, Dialyzer FX80, Weight Loss 1.7, Weight Gain 2.0, Hrs 4, Heparin 5000, Blood Flow 300, Dialysate Bicarbonate, Ultrafiltration 1.5, TBP 72, Transfusion None, Membrane Polysulfone");
        addSummary("Done By / Department / Doctor", dr.lastDoneBy + " / " + dr.lastDepartment + " / " + dr.lastDoctor + (dr.doctorWasAutofilled ? " (autofilled)" : " (selected)"));
        addSummary("Remark", remark);
        addSummary("Save request", dr.lastSaveRequest.isEmpty() ? "(not seen)" : dr.lastSaveRequest);
        addSummary("Server answer", savedByServer ? "saved (ResultStatus " + dr.lastSaveStatus + (dr.lastSaveMessage.isEmpty() ? "" : ", \"" + dr.lastSaveMessage.trim() + "\"") + ")" : "(no save confirmed" + (dr.lastSaveMessage.isEmpty() ? "" : ": " + dr.lastSaveMessage) + ")");
        addSummary("Records on the server before / after", (before == null ? "?" : String.valueOf(before.size())) + " / " + (after == null ? "?" : String.valueOf(after.size())));
        addSummary("New record(s) on the server", freshText);
        addSummary("Table", mine != null ? "shows the new row" : (rows.isEmpty() ? "BLANK - the new row is not populated" : "does not show the new row"));
        addSummary("Toast shown", toast.isEmpty() ? "(none)" : toast);
        addSummary("Result", ok && mine != null ? toast : (mine == null ? "FAIL - the new row does not appear in the table" + (ok ? "; the save itself worked (\"" + toast + "\")" : "")
                : (toast.isEmpty() ? "Not confirmed" : (savedByServer ? "Saved, but the toast is \"" + toast + "\"" : toast))));
    }
}
