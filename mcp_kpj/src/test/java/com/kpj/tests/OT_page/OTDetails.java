package com.kpj.tests.OT_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page objects are also named OTDetails / OTSchedule — referenced by their fully-qualified names.

/**
 * OT &gt; <b>OT Details</b>.
 *
 * <ol>
 *   <li>Open <b>OT</b> → <b>OT Details</b>; click <b>New</b>.</li>
 *   <li>Enter the patient's MRN and click search.</li>
 *   <li>In <b>OT Sheet Details</b> select the <b>OT Result</b>, <b>OT Status</b>, <b>Procedure Type</b> and <b>Anesthesia
 *       Type</b>.</li>
 *   <li><b>Save</b> and verify the success toast.</li>
 * </ol>
 *
 * <p><b>Which patient.</b> The MRN search pre-fills the OT sheet (Date, From/To Time, OT Theatre) from the patient's
 * <b>OT schedule for today</b>; for anyone else those fields stay empty and cannot be filled by hand reliably (the times are
 * a custom time-picker). The steps above therefore need a patient who has an OT schedule today. The test takes the patients on
 * today's <b>OT Schedule list</b> (no MRN is hardcoded) and uses the first one the OT Details search pre-fills; <b>only if
 * nobody is scheduled today</b> does it book an OT schedule first (the OT Schedule flow, for a free slot today). {@code -Dmrn=}
 * pins a patient, and {@code -Dot.bookfirst=true} always books one first.</p>
 *
 * <p>The schedule is not consumed by saving OT Details — the same patient can be saved again — so repeated runs on the same
 * day reuse the same scheduled patient, each adding another OT Detail record. This test SAVES a real OT Detail.</p>
 */
public class OTDetails extends DevHisBase {

    // The OT Result master spells it "SUCESSFULL"; the match is on that text first, then on any spelling of it.
    static final String OT_RESULT = "SUCESSFULL";
    static final String OT_STATUS = "Completed";
    static final String PROCEDURE_TYPE = "Minor";
    static final String ANESTHESIA_TYPE = "Local Anesthesia";
    static final String THEATRE = "General OT";
    static final String TABLE = "test";
    /** {@code -Dot.bookfirst=true}: ignore today's existing schedules and book a new one first (also how that path is exercised). */
    static final boolean BOOK_FIRST = Boolean.getBoolean("ot.bookfirst");

    public OTDetails() { super("OT_OTDetails"); }

    public static void main(String[] args) {
        OTDetails t = new OTDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OT - OT Details", "OT > OT Details",
                "New: enter the MRN of a patient who has an OT schedule today and search (the OT sheet fills from the schedule); select OT Result, "
                        + "OT Status, Procedure Type and Anesthesia Type; Save; verify the success toast. SAVES a real OT Detail.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.OT_page.OTSchedule ots = new com.kpj.pages.OT_page.OTSchedule(page);
        com.kpj.pages.OT_page.OTDetails od = new com.kpj.pages.OT_page.OTDetails(page);

        // 1) a patient with an OT schedule today — from today's OT Schedule list; booked here only if the list is empty
        String pinned = System.getProperty("mrn");
        java.util.List<String> candidates;
        String source;
        if (pinned != null && !pinned.isBlank()) {
            candidates = java.util.List.of(pinned.trim());
            source = "MRN pinned with -Dmrn";
        } else {
            boolean list = ots.navigateViaMenu();
            candidates = list && !BOOK_FIRST ? ots.scheduledMrnsToday() : new java.util.ArrayList<>();
            source = "today's OT Schedule list";
            if (list && candidates.isEmpty()) {
                String booked = ots.scheduleForToday(THEATRE, TABLE);
                if (booked.isEmpty()) {
                    step(page, "Find a patient with an OT schedule today",
                            "OT Schedule list: look for a patient scheduled today; if none, book an OT schedule",
                            "A patient with an OT schedule today is available",
                            "Nobody is scheduled today and booking one failed — " + ots.scheduleProblem, "FAIL");
                    addSummary("Result", "FAILED — no patient with an OT schedule today: " + ots.scheduleProblem);
                    return;
                }
                candidates = new java.util.ArrayList<>(java.util.List.of(booked));
                source = BOOK_FIRST ? "an OT schedule booked just now for today (forced with -Dot.bookfirst)" : "an OT schedule booked just now for today (nobody was scheduled)";
            }
            step(page, "Find a patient with an OT schedule today",
                    "OT Schedule list: look for a patient scheduled today; if none, book an OT schedule",
                    "A patient with an OT schedule today is available",
                    list ? (candidates.isEmpty() ? "None" : candidates.size() + " scheduled patient(s) — MRNs " + candidates + " (from " + source + ")")
                         : "The OT Schedule list did not open (" + page.url() + ")",
                    candidates.isEmpty() ? "FAIL" : "PASS");
            if (candidates.isEmpty()) { addSummary("Result", "FAILED — no scheduled patient"); return; }
        }

        // 2) OT > OT Details
        boolean list = od.navigateViaMenu();
        step(page, "Open OT Details", "Click OT -> OT Details", "The OT Details list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — OT Details not reached"); return; }

        // 3) New
        boolean form = od.clickNew();
        step(page, "Click New", "Click New (OTDetails)", "The OT details form is shown",
                form ? "Form opened (" + page.url() + ")" : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the OT details form did not open"); return; }

        // 4) MRN + search: the first patient whose OT sheet comes back pre-filled from today's schedule
        String mrn = null;
        StringBuilder tried = new StringBuilder();
        for (String c : candidates) {
            boolean loaded = od.searchPatient(c);
            boolean prefilled = loaded && od.sheetPrefilled();
            tried.append(tried.length() == 0 ? "" : "; ").append(c).append(!loaded ? " (patient not loaded)" : prefilled ? " (OT sheet pre-filled)" : " (no OT schedule today)");
            if (prefilled) { mrn = c; break; }
        }
        step(page, "Enter MRN and click search", "Enter the patient's MRN and click search",
                "The patient is loaded and the OT sheet is filled from the patient's OT schedule",
                mrn != null ? "Loaded " + od.lastPatient + ". The OT sheet came pre-filled: Date " + od.lastDate + ", From " + od.lastFrom + ", To "
                                + od.lastTo + " (" + od.lastMinutes + " min), OT Theatre " + od.lastTheatre + ". Tried: " + tried
                            : "No candidate gave a pre-filled OT sheet — " + tried,
                mrn != null ? "PASS" : "FAIL");
        if (mrn == null) { addSummary("Result", "FAILED — no patient with an OT schedule today could be loaded: " + tried); return; }

        // 5) the four OT sheet selections
        String result = od.selectOtResult(OT_RESULT);
        step(page, "Select OT Result", "Select the OT Result", "An OT Result is selected",
                result.isEmpty() ? "Not selected — " + od.problems : "Selected \"" + result + "\"" + (od.ok() ? "" : " | " + od.problems),
                result.isEmpty() || !od.ok() ? "FAIL" : "PASS");
        String status = od.selectOtStatus(OT_STATUS);
        step(page, "Select OT Status", "Select the OT Status", "An OT Status is selected",
                status.isEmpty() ? "Not selected — " + od.problems : "Selected \"" + status + "\"" + (od.ok() ? "" : " | " + od.problems),
                status.isEmpty() || !od.ok() ? "FAIL" : "PASS");
        String ptype = od.selectProcedureType(PROCEDURE_TYPE);
        step(page, "Select Procedure Type", "Select the Procedure Type", "A Procedure Type is selected",
                ptype.isEmpty() ? "Not selected — " + od.problems : "Selected \"" + ptype + "\"" + (od.ok() ? "" : " | " + od.problems),
                ptype.isEmpty() || !od.ok() ? "FAIL" : "PASS");
        String anaes = od.selectAnesthesiaType(ANESTHESIA_TYPE);
        step(page, "Select Anesthesia Type", "Select the Anesthesia Type", "An Anesthesia Type is selected",
                anaes.isEmpty() ? "Not selected — " + od.problems : "Selected \"" + anaes + "\"" + (od.ok() ? "" : " | " + od.problems),
                anaes.isEmpty() || !od.ok() ? "FAIL" : "PASS");
        if (result.isEmpty() || status.isEmpty() || ptype.isEmpty() || anaes.isEmpty()) {
            addSummary("Result", "FAILED — an OT sheet selection could not be made: " + od.problems);
            return;
        }

        // 6) Save + success toast
        String toast = od.clickSaveAndGetToast();
        boolean ok = od.saveSucceeded();
        step(page, "Click Save", "Click Save (fnIUDOTSchedules)", "The OT detail is saved",
                od.lastSave + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\"")
                        + (od.onList() ? "; the app then returned to the OT Details list" : ""),
                toast.isEmpty() ? "FAIL" : "PASS");
        step(page, "Verify success toast message", "Read the toast shown after Save", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        addSummary("Patient", od.lastPatient);
        addSummary("Patient source", source);
        addSummary("OT schedule used", od.lastDate + ", " + od.lastFrom + " – " + od.lastTo + ", " + od.lastTheatre);
        addSummary("OT Result / Status", od.lastResult + " / " + od.lastStatus);
        addSummary("Procedure Type / Anesthesia Type", od.lastProcedureType + " / " + od.lastAnesthesia);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }
}
