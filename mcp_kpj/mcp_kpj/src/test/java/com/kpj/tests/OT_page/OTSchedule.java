package com.kpj.tests.OT_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OTSchedule — referenced by its fully-qualified name.

/**
 * OT &gt; <b>OT Schedule</b>.
 *
 * <ol>
 *   <li>Open <b>OT</b> → <b>OT Schedule</b>; click <b>New</b>.</li>
 *   <li>Procedure Details: <b>Get Procedure Details</b> → choose a procedure type, <b>Search</b> → tick a procedure →
 *       <b>Add</b>.</li>
 *   <li><b>OT Details</b>: choose the <b>OT Theatre</b> (General OT) and <b>OT Table</b> (test); in the Current Schedule tick
 *       a free slot, which sets From Time and To Time.</li>
 *   <li>Doctor: Department (Physician), Specialty (Specialist), Doctor, Procedure → <b>Add</b>.</li>
 *   <li>Staff: Designation, Procedure, Staff → <b>Add</b>.</li>
 *   <li><b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p><b>Patient.</b> The form will not go on without a patient (<b>MRN*</b>), which the steps above do not mention. The test
 * takes the patients the shared <b>Patient Search</b> popup lists and uses the first one the screen accepts (it must have a
 * visit) — no MRN is hardcoded; {@code -Dmrn=} pins one.</p>
 *
 * <p><b>Slots.</b> A booked or elapsed slot is disabled, and the "test" table has only a few slots a day, so each run uses
 * one up. The first FREE slot is ticked; if today has none left the OT Date moves on a day at a time (up to 30) — which is
 * also why the run date, not just the time, is shown in the report; {@code -Dot.daysahead=N} starts the search N days out. Theatre and table fall back to the first available
 * option if "General OT" / "test" do not exist on the environment.</p>
 *
 * <p>This test SAVES a real OT schedule for the patient.</p>
 */
public class OTSchedule extends DevHisBase {

    static final String THEATRE = "General OT";
    static final String TABLE = "test";
    static final String PROCEDURE_TYPE = "Minor";
    static final String DEPARTMENT = "PHYSICIAN";
    static final String SPECIALTY_RE = "specialist";
    static final int MAX_DAYS_AHEAD = 30;
    /** Start the slot search this many days from today ({@code -Dot.daysahead=}); 0 = today. */
    static final int START_DAYS_AHEAD = Math.max(0, Integer.getInteger("ot.daysahead", 0));

    public OTSchedule() { super("OT_OTSchedule"); }

    public static void main(String[] args) {
        OTSchedule t = new OTSchedule();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OT - OT Schedule", "OT > OT Schedule",
                "New: pick a patient, Get Procedure Details (type, Search, tick, Add), OT Details (theatre, table, a free "
                        + "Current Schedule slot), doctor (Physician / Specialist) + Add, staff + Add, Save; verify the success toast. "
                        + "SAVES a real OT schedule.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.OT_page.OTSchedule ot = new com.kpj.pages.OT_page.OTSchedule(page);

        // 1) OT > OT Schedule
        boolean list = ot.navigateViaMenu();
        step(page, "Open OT Schedule", "Click OT -> OT Schedule", "The OT Schedule list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — OT Schedule not reached"); return; }

        // 2) New
        boolean form = ot.clickNew();
        step(page, "Click New", "Click New (AddOTSchedule)", "The new OT schedule form is shown",
                form ? "Form opened (" + page.url() + ")" : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the new OT schedule form did not open"); return; }

        // 2b) a patient — the form needs one (MRN is mandatory) although the steps do not mention it
        String pinned = System.getProperty("mrn");
        java.util.List<String> cands = pinned != null && !pinned.isBlank() ? java.util.List.of(pinned.trim()) : ot.candidateMrns(10);
        String mrn = null;
        StringBuilder tried = new StringBuilder();
        for (String c : cands) {
            boolean accepted = ot.searchPatient(c);
            tried.append(tried.length() == 0 ? "" : ", ").append(c).append(accepted ? " (accepted)" : " (no visit / not accepted)");
            if (accepted) { mrn = c; break; }
        }
        step(page, "Select the patient (MRN)", "Enter a patient's MRN (from Patient Search) and click search — the form needs a patient",
                "A patient with a visit is loaded",
                mrn != null ? "Loaded " + ot.lastPatient + " — tried: " + tried
                            : (cands.isEmpty() ? "Patient Search listed no patients" : "No patient could be loaded — tried: " + tried),
                mrn != null ? "PASS" : "FAIL");
        if (mrn == null) { addSummary("Result", "FAILED — no patient could be loaded"); return; }

        // 3) Procedure Details: Get Procedure Details
        boolean popup = ot.clickGetProcedureDetails();
        step(page, "Click Get Procedure Details", "Click Get Procedure Details", "The Procedures List popup opens",
                popup ? "The Procedures List popup is open" : "The popup did not open", popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED — the Procedures List popup did not open"); return; }

        String searched = ot.searchProcedures(PROCEDURE_TYPE);
        step(page, "Select procedure type and click Search", "Select a procedure type and click Search",
                "The procedures of that type are listed",
                searched.isEmpty() ? "No procedures were listed — " + ot.problems : searched, searched.isEmpty() ? "FAIL" : "PASS");
        if (searched.isEmpty()) { addSummary("Result", "FAILED — no procedures could be listed"); return; }

        String proc = ot.tickFirstProcedure();
        step(page, "Select the procedure (tick the checkbox)", "Tick the checkbox of a procedure", "The procedure is ticked",
                proc.isEmpty() ? "Not ticked — " + ot.problems : "Ticked \"" + proc + "\"", proc.isEmpty() ? "FAIL" : "PASS");
        if (proc.isEmpty()) { addSummary("Result", "FAILED — no procedure could be ticked"); return; }

        boolean added = ot.clickAddProcedure();
        step(page, "Click Add (procedure)", "Click Add in the Procedures List popup",
                "The popup closes and the procedure is listed under Procedure Details",
                added ? "\"" + proc + "\" is now in the Procedure Name grid" : "Not added — " + ot.problems, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the procedure was not added"); return; }

        // 4) OT Details: theatre, table, a free slot
        boolean tab = ot.openOtDetailsTab();
        step(page, "Click OT Details", "Click the OT Details tab", "The OT Details pane is shown",
                tab ? "OT Details shown" : "Not shown — " + ot.problems, tab ? "PASS" : "FAIL");
        if (!tab) { addSummary("Result", "FAILED — the OT Details tab did not open"); return; }

        String theatre = ot.selectTheatre(THEATRE);
        step(page, "Select OT Theatre", "Select the OT Theatre (" + THEATRE + ")", "The theatre is selected",
                theatre.isEmpty() ? "Not selected — " + ot.problems
                        : "Selected \"" + theatre + "\"" + (theatre.equalsIgnoreCase(THEATRE) ? "" : " (\"" + THEATRE + "\" is not on this environment)"),
                theatre.isEmpty() ? "FAIL" : "PASS");
        if (theatre.isEmpty()) { addSummary("Result", "FAILED — no OT theatre could be selected"); return; }

        String table = ot.selectTable(TABLE);
        step(page, "Select OT Table", "Select the OT Table (" + TABLE + ")", "The table is selected and the Current Schedule shows its slots",
                table.isEmpty() || !ot.ok() ? "Problem — " + ot.problems
                        : "Selected \"" + table + "\"" + (table.equalsIgnoreCase(TABLE) ? "" : " (\"" + TABLE + "\" is not under this theatre)")
                          + "; Current Schedule slots: " + ot.slots(),
                table.isEmpty() || !ot.ok() ? "FAIL" : "PASS");
        if (table.isEmpty() || !ot.ok()) { addSummary("Result", "FAILED — no OT table / slots"); return; }

        String slot = ot.tickFreeSlot(START_DAYS_AHEAD, MAX_DAYS_AHEAD);
        step(page, "Tick a Current Schedule row (From / To time)", "Tick the checkbox of a free row in the Current Schedule",
                "From Time and To Time are set from the row",
                slot.isEmpty() ? "No slot could be ticked — " + ot.problems : slot + (ot.ok() ? "" : " | " + ot.problems),
                slot.isEmpty() || !ot.ok() ? "FAIL" : "PASS");
        if (slot.isEmpty()) { addSummary("Result", "FAILED — no free slot"); return; }

        // 5) doctor
        String doc = ot.selectDoctorDetails(DEPARTMENT, SPECIALTY_RE);
        step(page, "Select department, specialty, doctor and procedure",
                "Select Department (Physician), Specialty (Specialist), a Doctor and the Procedure",
                "The doctor details are selected", doc + (ot.ok() ? "" : " | " + ot.problems), ot.ok() ? "PASS" : "FAIL");
        if (!ot.ok()) { addSummary("Result", "FAILED — doctor details: " + ot.problems); return; }

        boolean docAdded = ot.clickAddDoctor();
        ot.scrollToText("^doctor type");
        step(page, "Click Add (doctor)", "Click Add", "The doctor is listed in the Doctor grid",
                docAdded ? "Doctor grid: " + ot.gridRows("doctor type", "doctor") : "Not added — " + ot.problems, docAdded ? "PASS" : "FAIL");
        if (!docAdded) { addSummary("Result", "FAILED — the doctor was not added"); return; }

        // 6) staff
        String staff = ot.selectStaffDetails();
        step(page, "Select designation, procedure and staff", "Select a Designation, the Procedure and a Staff member",
                "The staff details are selected", staff + (ot.ok() ? "" : " | " + ot.problems), ot.ok() ? "PASS" : "FAIL");
        if (!ot.ok()) { addSummary("Result", "FAILED — staff details: " + ot.problems); return; }

        boolean staffAdded = ot.clickAddStaff();
        ot.scrollToText("^designation");
        step(page, "Click Add (staff)", "Click Add", "The staff member is listed in the Staff grid",
                staffAdded ? "Staff grid: " + ot.gridRows("designation", "staff") : "Not added — " + ot.problems, staffAdded ? "PASS" : "FAIL");
        if (!staffAdded) { addSummary("Result", "FAILED — the staff member was not added"); return; }

        // 7) Save + success toast
        String toast = ot.clickSaveAndGetToast();
        boolean ok = ot.saveSucceeded();
        step(page, "Click Save", "Click Save (fnIUDOTSchedules)", "The OT schedule is saved",
                ot.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\""),
                toast.isEmpty() ? "FAIL" : "PASS");
        step(page, "Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        addSummary("Patient", ot.lastPatient);
        addSummary("Procedure", ot.lastProcedureType + " — " + ot.lastProcedure);
        addSummary("OT", ot.lastTheatre + " / " + ot.lastTable + " on " + ot.lastDate + ", " + ot.lastFrom + " – " + ot.lastTo);
        addSummary("Doctor", ot.lastDoctorDept + " / " + ot.lastDoctorSpec + " / " + ot.lastDoctor);
        addSummary("Staff", ot.lastDesignation + " / " + ot.lastStaff);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
