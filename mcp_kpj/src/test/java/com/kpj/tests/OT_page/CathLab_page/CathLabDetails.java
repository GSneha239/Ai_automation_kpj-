package com.kpj.tests.OT_page.CathLab_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.KeptBrowser;
import com.kpj.pages.LoginPage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named CathLabDetails — referenced by its fully-qualified name.

/**
 * OT &gt; Cath Lab &gt; <b>Cath Lab Details</b> ({@code #/CathLabDetailsList} → New → {@code #/add-CathLabDetails}) — the screen has MANY tabs and is scripted one tab at a time, in ONE continuous session.
 *
 * <p><b>Start</b>: click <b>New</b>; enter the patient's MRN ({@code 1100339649}) and click search.</p>
 *
 * <p><b>Tab 1 — OT Sheet Details</b> (a real tab of the form, open by default): click <b>OT Sheet Details</b>; enter <b>From Time</b> and <b>To Time</b>; select <b>OT Theatre</b>, <b>OT Result</b> and
 * <b>OT Status</b>; select the <b>Procedure Type</b>; select the <b>Anesthesia Type</b>; enter <b>Wheel In Time</b> and <b>Wheel Out Time</b>; enter the <b>Anesthesia Start / End Time</b> and the
 * <b>Surgery Start / End Time</b>; enter the <b>Surgical Team</b>; enter the <b>Radiation Time</b>, <b>Fluoroscopy Time(Min)</b>, <b>Radiation Dose</b> and <b>Remarks</b>.</p>
 *
 * <p><b>Continuous session.</b> The tabs arrive one script at a time and the form must never be saved, reloaded or logged out of until the last tab. So the series runs in a browser that OUTLIVES each run
 * ({@link KeptBrowser}): {@code -Dcl.keep=true} starts it (first run: login, New, MRN, tab 1) or reconnects to it, and leaves it open on the form at the end. The next tab's script is then run with
 * {@code -Dcl.from=<tab>} (it skips the login, the menu, New and the MRN, checks the form is still open with the patient, and does only that tab onwards). Tab keys, in order: {@code otsheet}, {@code surgery}, {@code docemp}, {@code otnotes}, {@code postop}, {@code precheck}, {@code signinout}. Each continued
 * run writes its own report ({@code OT_CathLabDetails_from_<tab>.html}); a plain run without {@code -Dcl.keep} does the WHOLE flow from the login in its own browser and closes it (the final end-to-end run).
 * {@code -Dcl.port=} changes the debugging port (default 9333).</p>
 *
 * <p><b>Tab 2 — Surgery Details</b> ("add another tab"; a real tab): click <b>Surgery Details</b>; click <b>Get Procedure Details</b>; in the <b>Procedures List</b> dialog select the <b>Procedure Type</b> and click
 * <b>Search</b>; tick the checkbox of one procedure; click <b>Add</b> (the procedure then shows in the Surgery Description table; a row the screen cannot add - "already present" or no service item - is replaced by the next).</p>
 *
 * <p><b>Tab 3 — Doc/Emp Details</b> ("add another tab"; a real tab): click <b>Doc/Emp Details</b>; <b>Get Doctors</b> - in the Doctor Search dialog select the <b>Location</b>, click <b>Search</b>, tick the doctor
 * (Prof 1900 Kumaravel Tan), <b>OK</b>; verify the doctor is in the Doctor List table; select the <b>Surgery Name</b> of that row; <b>Get Staff</b> - in the Staff Search dialog select the <b>Designation</b> (Admin),
 * click <b>Search</b>, tick the staff member (Doctor 1), <b>OK</b>; select the <b>Surgery Name</b> of the Employee List row. (The Surgery Name selects offer the procedures added on tab 2.)</p>
 *
 * <p><b>Tab 4 — OT Notes</b> ("add another tab"; a real tab with four SUB tabs): <b>Surgery Notes</b> - click <b>Pre Surgery Notes</b>, in the popup click <b>Search</b>, tick one row, <b>OK</b>; the same for
 * <b>Intra Surgery Notes</b>; <b>Post Surgery Notes</b> (the script omits the Search there, but its list is empty until Search, so the test clicks it and says so). <b>Anesthesia Notes</b> - the same three
 * (Pre / Intra / Post Anesthesia Notes, each with Search). <b>Perfusionist Notes</b> - type the Pre-, Intra- and Post-Perfusionist Notes. <b>Nursing Notes</b> - type the note. After each OK the column's text area must
 * hold the chosen template's text (empty = FAIL).</p>
 *
 * <p><b>Tab 5 — Post-Operative Instructions</b> (a real tab): click <b>Post-Operative Instructions</b>; select an instruction in the <b>Post-Operative Instructions</b> dropdown; click <b>Add</b> (it then shows in the
 * Instruction table).</p>
 *
 * <p><b>Tab 6 — Preoperative check List</b> (a real tab): click <b>Preoperative check List</b>; tick the checkbox of the checklist row; tick the <b>Admitting Nurse</b> radio (Yes); select the <b>Admitting Nurse</b> in
 * the dropdown; draw the <b>Admitting Nurse Sign</b> on the signature pad (the pad must then hold ink).</p>
 *
 * <p><b>Tab 7 — Sign In - Time Out - Sign Out</b> (a real tab; a board of checklist columns): in <b>SIGN IN</b> tick the checkbox of one row and enter remarks; the same in <b>TIME OUT</b>; the same in the
 * column <b>Auto procedure category 111126155149</b> (the third column). The Remarks box only appears once the row is ticked.</p>
 *
 * <p><b>Nothing is saved</b> - the Save instruction comes with the last tab.</p>
 *
 * <p>Other options (all optional): {@code -Dcl.category3=}, {@code -Dcl.nurse=}, {@code -Dcl.postinstruction=}, {@code -Dcl.mrn=}, {@code -Dcl.proctype=} (the dialog's Procedure Type), {@code -Dcl.location=}, {@code -Dcl.doctor=}, {@code -Dcl.designation=}, {@code -Dcl.staff=}, {@code -Dcl.theatre=}, {@code -Dcl.result=}, {@code -Dcl.status=}, {@code -Dcl.procedure=}, {@code -Dcl.anesthesia=}, {@code -Dcl.team=} (head count, digits only).</p>
 */
public class CathLabDetails extends DevHisBase {

    /** The tabs scripted so far, in the order they are filled. */
    private static final List<String> TABS = List.of("otsheet", "surgery", "docemp", "otnotes", "postop", "precheck", "signinout");
    private static final String FROM = System.getProperty("cl.from", "").trim().toLowerCase();
    /** Run in the browser that outlives the run (always so when continuing from a tab). */
    private static final boolean KEEP = Boolean.getBoolean("cl.keep") || !FROM.isEmpty();
    private static final int PORT = Integer.getInteger("cl.port", 9333);

    private boolean keptFresh;

    public CathLabDetails() { super(FROM.isEmpty() ? "OT_CathLabDetails" : "OT_CathLabDetails_from_" + FROM); }

    public static void main(String[] args) { go(new CathLabDetails()); }

    @org.junit.jupiter.api.Test
    void execute() { go(this); }

    private static void go(CathLabDetails t) {
        if (!KEEP) {
            try { t.run(); } finally { t.stop(); }
            return;
        }
        KeptBrowser.Session s = KeptBrowser.open(PORT);
        try {
            t.attach(s.pw, s.browser, s.ctx, s.page);
            t.keptFresh = s.fresh;
            t.run();
        } finally {
            s.close();   // drops the connection only: the browser stays open on the form
        }
    }

    private final String mrn = System.getProperty("cl.mrn", "1100339649");
    // The OT Theatre master offers General OT / test / test11 (and automation leftovers) - no Cath Lab theatre exists.
    private final String theatre = System.getProperty("cl.theatre", "General OT");
    private final String result = System.getProperty("cl.result", "Successful");
    private final String status = System.getProperty("cl.status", "Completed");
    private final String procedure = System.getProperty("cl.procedure", "Minor");
    private final String anesthesia = System.getProperty("cl.anesthesia", "Local");
    private final String team = System.getProperty("cl.team", "4");
    // Surgery Details: the Procedures List dialog's Procedure Type (the master offers Minor / Major / Auto ...); Minor is also the OT sheet's Procedure Type.
    private final String procType = System.getProperty("cl.proctype", "Minor");
    // Doc/Emp Details: the Doctor Search dialog (Location: the only one offered, the logged-in hospital, unless -Dcl.location) and the Staff Search dialog.
    private final String location = System.getProperty("cl.location", "");
    private final String doctor = System.getProperty("cl.doctor", "Prof 1900 Kumaravel Tan");
    private final String designation = System.getProperty("cl.designation", "Admin");
    private final String staff = System.getProperty("cl.staff", "Doctor 1");
    // Post-Operative Instructions: the instruction chosen in the dropdown (a real entry of the master; the first one is used if it is missing).
    private final String postInstruction = System.getProperty("cl.postinstruction", "Monitor Vital Signs Every 15 Minutes for First Hour");
    // Preoperative check List: the Admitting Nurse of the dropdown (the master offers ~40 nurses; the first named one is used if this one is missing) and the radio answered.
    private final String nurse = System.getProperty("cl.nurse", "Nurse Adeline");
    private final String nurseAnswer = System.getProperty("cl.nurseanswer", "Yes");
    // Sign In - Time Out - Sign Out: the third column the script names (an automation-created procedure category; the board holds eight columns).
    private final String category3 = System.getProperty("cl.category3", "Auto procedure category 111126155149");

    // A plausible cath-lab procedure, in the order the boxes are read: wheel in 09:05, anesthesia 09:15 - 11:45, surgery 09:30 - 11:30, wheel out 11:55, sheet 09:00 - 12:00.
    private static final String FROM_TIME = "09:00 AM", TO_TIME = "12:00 PM", WHEEL_IN = "09:05 AM", WHEEL_OUT = "11:55 AM",
            ANES_START = "09:15 AM", ANES_END = "11:45 AM", SURG_START = "09:30 AM", SURG_END = "11:30 AM";
    private static final String RADIATION_TIME = "12", FLUORO_TIME = "8", RADIATION_DOSE = "150";

    /** True when the tab with this key is part of this run (a plain run does every tab; a continued run starts at {@code cl.from}). */
    private static boolean runs(String tab) { return FROM.isEmpty() || TABS.indexOf(tab) >= TABS.indexOf(FROM); }

    private boolean sessionAlive() {
        try {
            Object r = page.evaluate("() => !!window.angular && [...document.querySelectorAll('a')].some(x=>(x.textContent||'').replace(/\\s+/g,' ').trim()==='OT')");
            return Boolean.TRUE.equals(r) && page.url().startsWith(BASE);
        } catch (Exception e) { return false; }
    }

    @Override
    protected void body() {
        meta("OT - Cath Lab - Cath Lab Details", "OT > Cath Lab > Cath Lab Details",
                "Click New; enter the MRN (" + mrn + ") and click search. Tab OT Sheet Details: click it; enter From Time / To Time; select OT Theatre, OT Result, OT Status; select Procedure Type; select Anesthesia Type; "
                        + "enter Wheel In / Wheel Out Time; enter Anesthesia Start / End Time and Surgery Start / End Time; enter Surgical Team; enter Radiation Time, Fluoroscopy Time, Radiation Dose and Remarks. "
                        + "NOTHING IS SAVED, reloaded or logged out of (the Save instruction comes with the last tab)"
                        + (KEEP ? "; the browser is kept open on the form for the next tab." : "."));

        if (!FROM.isEmpty() && !TABS.contains(FROM)) {
            step("Start", "-Dcl.from=" + FROM, "A known tab key (" + TABS + ")", "Unknown tab key \"" + FROM + "\"", "FAIL");
            addSummary("Result", "FAILED — unknown -Dcl.from=" + FROM);
            return;
        }

        com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl = new com.kpj.pages.OT_page.CathLab_page.CathLabDetails(page);

        if (FROM.isEmpty()) { if (!prelude(cl)) return; }
        else if (!continueOnForm(cl)) return;

        if (runs("otsheet")) otSheetDetails(cl);
        if (runs("surgery")) surgeryDetails(cl);
        if (runs("docemp")) docEmpDetails(cl);
        if (runs("otnotes")) otNotes(cl);
        if (runs("postop")) postOp(cl);
        if (runs("precheck")) preOp(cl);
        if (runs("signinout")) signInOut(cl);

        if (KEEP) addSummary("Browser", "KEPT OPEN on the Cath Lab Details form (debugging port " + PORT + "): not saved, not reloaded, not logged out - the next tab continues from here with -Dcl.from=<tab>");
    }

    // =====================================================================================================================
    // Start: login, menu, New, MRN
    // =====================================================================================================================

    private boolean prelude(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        if (KEEP && !keptFresh && sessionAlive()) {
            step("Login", "The kept browser is already logged in", "Authenticated; no new login", "Already logged in (kept session) - no login, no reload", "PASS");
        } else {
            new LoginPage(page).login(BASE, USER, PASS);
            step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");
        }

        boolean list = cl.navigateViaMenu();
        step("Open Cath Lab Details", "Click OT -> Cath Lab -> Cath Lab Details", "The Cath Lab Details list is shown",
                list ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", list ? "PASS" : "FAIL");
        if (!list) { addSummary("Result", "FAILED — Cath Lab Details not reached"); return false; }

        boolean form = cl.clickNew();
        step("Click New", "Click New", "The Cath Lab Details form is shown",
                form ? "Form opened (" + page.url() + "); its tabs: " + cl.tabNames() : "The form did not open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED — the Cath Lab Details form did not open"); return false; }

        boolean found = cl.searchPatient(mrn);
        step("Enter MRN and click search", "Enter MRN " + mrn + " and click search", "The patient is loaded into the form",
                found ? "MRN " + mrn + " searched; the form loaded the patient: " + cl.lastPatient
                        + (cl.timeModel("fromtime").isEmpty() ? " (no OT schedule for the patient today, so the OT sheet stays empty)" : "; the OT sheet came pre-filled from the patient's OT schedule")
                      : "Problem — " + cl.problems,
                found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED — the patient was not loaded: " + cl.problems); return false; }
        addSummary("Patient", cl.lastPatient);
        return true;
    }

    /** A continued run: no login, no menu, no reload - the form the previous run left open must still be there with the patient in it. */
    private boolean continueOnForm(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        boolean form = cl.onForm();
        boolean patient = form && cl.patientLoaded(mrn);
        step("Continue on the open Cath Lab Details form", "No reload, no save, no login: carry on on the form the previous run left open",
                "The form is open with the patient loaded and the earlier tabs' values in place",
                !form ? "NOT on the form (" + page.url() + ") - nothing to continue"
                      : !patient ? "The form is open but patient " + mrn + " is not in it (" + cl.patientSummary() + ")"
                      : "Still on " + page.url() + " (tab \"" + cl.activeTab() + "\"); patient: " + cl.patientSummary() + "; the OT sheet holds: " + cl.sheetValues(),
                patient ? "PASS" : "FAIL");
        if (!patient) { addSummary("Result", "FAILED — the form from the previous run is not open with the patient"); return false; }
        addSummary("Patient", cl.patientSummary());
        return true;
    }

    // =====================================================================================================================
    // Tab 1 - OT Sheet Details
    // =====================================================================================================================

    private void otSheetDetails(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[OT Sheet Details] ";
        int failed = 0;

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("OT Sheet Details");
        boolean controls = opened && cl.sheetControlsVisible();
        step(P + "Click OT Sheet Details", "Add a new tab - OT Sheet Details; click OT Sheet Details", "The OT Sheet Details tab is open",
                opened ? "OT Sheet Details is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + ")"
                        + (controls ? " and its fields are on screen: From Time, To Time, OT Theatre, OT Result, OT Status, Procedure Type, Anesthesia Type, the radiation boxes and Remarks" : " but its fields are not all on screen")
                       : "Problem — " + cl.problems,
                opened && controls ? "PASS" : "FAIL");
        if (!opened) { addSummary("[OT Sheet] Result", "FAILED — the tab did not open: " + cl.problems); return; }

        // 2) From Time / To Time
        cl.resetProblems();
        boolean f = cl.enterTime("fromtime", FROM_TIME);
        boolean t = cl.enterTime("totime", TO_TIME);
        boolean ok = f && t;
        step(P + "Enter From Time and To Time", "Enter From Time " + FROM_TIME + " and To Time " + TO_TIME, "Both times are accepted",
                ok ? "From Time " + FROM_TIME + " and To Time " + TO_TIME + " entered; the screen holds both and the boxes show them. Total Minutes stays \"" + cl.totalMinutes()
                        + "\" (the app only calculates it on a blur event the time boxes never send - not part of the spec)"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 3) OT Theatre / OT Result / OT Status
        cl.resetProblems();
        String th = cl.choose("ottheatreid", theatre, null);
        String rs = cl.choose("otresultid", result, "^succe\\w+$");
        String st = cl.choose("otstatusid", status, "^completed$|^successful$");
        ok = !th.isEmpty() && !rs.isEmpty() && !st.isEmpty();
        step(P + "Select OT Theatre, OT Result and OT Status", "Select OT Theatre \"" + theatre + "\", OT Result \"" + result + "\", OT Status \"" + status + "\"", "The three selections are made",
                ok ? "OT Theatre \"" + th + "\" (of " + cl.options("ottheatreid").size() + "), OT Result \"" + rs + "\" (of " + cl.options("otresultid").size() + "), OT Status \"" + st + "\" (of " + cl.options("otstatusid").size()
                        + ") selected; the boxes show \"" + cl.shown("ottheatreid") + "\" / \"" + cl.shown("otresultid") + "\" / \"" + cl.shown("otstatusid") + "\" and the screen's model holds them"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 4) Procedure Type
        cl.resetProblems();
        String pt = cl.choose("proceduretypeid", procedure, null);
        ok = !pt.isEmpty();
        step(P + "Select Procedure Type", "Select Procedure Type \"" + procedure + "\"", "A procedure type is selected",
                ok ? "Procedure Type \"" + pt + "\" selected (of " + cl.options("proceduretypeid").size() + "); the box shows \"" + cl.shown("proceduretypeid") + "\" and the screen's model holds it" : "Problem — " + cl.problems,
                ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 5) Anesthesia Type
        cl.resetProblems();
        String an = cl.choose("anesthesiatypeid", anesthesia, "^local\\b.*");
        ok = !an.isEmpty();
        step(P + "Select Anesthesia Type", "Select Anesthesia Type \"" + anesthesia + "\"", "An anesthesia type is selected",
                ok ? "Anesthesia Type \"" + an + "\" selected (of " + cl.options("anesthesiatypeid").size() + "); the box shows \"" + cl.shown("anesthesiatypeid") + "\" and the screen's model holds it" : "Problem — " + cl.problems,
                ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 6) Wheel In / Wheel Out
        cl.resetProblems();
        boolean wi = cl.enterTime("wheelintime", WHEEL_IN);
        boolean wo = cl.enterTime("wheelouttime", WHEEL_OUT);
        ok = wi && wo;
        step(P + "Enter Wheel In Time and Wheel Out Time", "Enter Wheel In Time " + WHEEL_IN + " and Wheel Out Time " + WHEEL_OUT, "Both times are accepted",
                ok ? "Wheel In Time " + WHEEL_IN + " and Wheel Out Time " + WHEEL_OUT + " entered; the screen holds both and the boxes show them" : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 7) Anesthesia Start / End and Surgery Start / End
        cl.resetProblems();
        boolean as = cl.enterTime("anesthesiastarttime", ANES_START);
        boolean ae = cl.enterTime("anesthesiaendtime", ANES_END);
        boolean ss = cl.enterTime("surgerystarttime", SURG_START);
        boolean se = cl.enterTime("surgeryendtime", SURG_END);
        ok = as && ae && ss && se;
        step(P + "Enter Anesthesia Start/End Time and Surgery Start/End Time",
                "Enter Anesthesia Start Time " + ANES_START + ", Anesthesia End Time " + ANES_END + ", Surgery Start Time " + SURG_START + ", Surgery End Time " + SURG_END, "All four times are accepted",
                ok ? "Anesthesia Start " + ANES_START + ", Anesthesia End " + ANES_END + ", Surgery Start " + SURG_START + ", Surgery End " + SURG_END + " entered; the screen holds all four and the boxes show them"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 8) Surgical Team (a head count: the box takes digits only)
        cl.resetProblems();
        boolean tm = cl.enterText("manpower", team);
        step(P + "Enter Surgical Team", "Enter Surgical Team " + team, "The surgical team is accepted",
                tm ? "Surgical Team \"" + team + "\" entered (the box takes digits only - a head count); the screen's model holds it" : "Problem — " + cl.problems, tm ? "PASS" : "FAIL");
        if (!tm) failed++;

        // 9) Radiation Time, Fluoroscopy Time, Radiation Dose, Remarks
        cl.resetProblems();
        String remarks = "QA Cath Lab OT sheet remarks - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));
        boolean rt = cl.enterText("RadiationTime", RADIATION_TIME);
        boolean ft = cl.enterText("FluoroscopyTime", FLUORO_TIME);
        boolean rd = cl.enterText("RadiationDose", RADIATION_DOSE);
        boolean rm = cl.enterText("remarks", remarks);
        ok = rt && ft && rd && rm;
        step(P + "Enter Radiation Time, Fluoroscopy Time, Radiation Dose and Remarks",
                "Enter Radiation Time(Min) " + RADIATION_TIME + ", Fluoroscopy Time(Min) " + FLUORO_TIME + ", Radiation Dose " + RADIATION_DOSE + " and Remarks", "All four are accepted",
                ok ? "Radiation Time(Min) " + RADIATION_TIME + ", Fluoroscopy Time(Min) " + FLUORO_TIME + ", Radiation Dose " + RADIATION_DOSE + " and Remarks \"" + remarks + "\" entered; the screen's model holds all four"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        addSummary("[OT Sheet] From / To Time", cl.timeModel("fromtime") + " / " + cl.timeModel("totime") + " (Total Minutes box: \"" + cl.totalMinutes() + "\")");
        addSummary("[OT Sheet] OT Theatre / Result / Status", cl.shown("ottheatreid") + " / " + cl.shown("otresultid") + " / " + cl.shown("otstatusid"));
        addSummary("[OT Sheet] Procedure Type / Anesthesia Type", cl.shown("proceduretypeid") + " / " + cl.shown("anesthesiatypeid"));
        addSummary("[OT Sheet] Wheel In / Out", cl.timeModel("wheelintime") + " / " + cl.timeModel("wheelouttime"));
        addSummary("[OT Sheet] Anesthesia Start / End", cl.timeModel("anesthesiastarttime") + " / " + cl.timeModel("anesthesiaendtime"));
        addSummary("[OT Sheet] Surgery Start / End", cl.timeModel("surgerystarttime") + " / " + cl.timeModel("surgeryendtime"));
        addSummary("[OT Sheet] Surgical Team", cl.sheet("manpower"));
        addSummary("[OT Sheet] Radiation Time / Fluoroscopy Time / Radiation Dose", cl.sheet("RadiationTime") + " / " + cl.sheet("FluoroscopyTime") + " / " + cl.sheet("RadiationDose"));
        addSummary("[OT Sheet] Remarks", cl.sheet("remarks"));
        addSummary("Saved", "NO — nothing is saved, reloaded or logged out of (the Save instruction comes with the last tab)");
        addSummary("[OT Sheet] Result", failed == 0 ? "All 9 OT Sheet Details steps passed; the tab is filled and left unsaved" : failed + " of 9 OT Sheet Details steps failed");
    }

    // =====================================================================================================================
    // Tab 2 - Surgery Details
    // =====================================================================================================================

    private void surgeryDetails(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[Surgery Details] ";
        final int maxAttempts = 6;

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("Surgery Details");
        int rowsBefore = opened ? cl.surgeryRows().size() : 0;
        step(P + "Click Surgery Details", "Add another tab - Surgery Details; click Surgery Details", "The Surgery Details tab is open",
                opened ? "Surgery Details is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); it shows the Surgery Description table ("
                        + rowsBefore + " row(s) so far) and the Get Procedure Details button"
                       : "Problem — " + cl.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[Surgery] Result", "FAILED — the tab did not open: " + cl.problems); return; }

        // 2) Get Procedure Details
        boolean dlg = cl.clickGetProcedureDetails();
        step(P + "Click Get Procedure Details", "Click Get Procedure Details", "The Procedures List dialog is shown",
                dlg ? "The dialog \"" + cl.dialogTitle() + "\" opened (Procedure Type, Procedure Name, Search; the Select | Procedure Name | Group | Sub Group table; Add and Close)" : "Problem — " + cl.problems,
                dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("[Surgery] Result", "FAILED — the Procedures List dialog did not open: " + cl.problems); return; }

        // 3) Procedure Type + Search
        List<String> types = cl.procedureTypes();
        String type = cl.selectProcedureType(procType);
        int rows = type.isEmpty() ? 0 : cl.clickSearch();
        // the list holds ~1,900 rows: the framework's 10 s screenshot sometimes times out on it, so this one is taken here with a longer limit
        byte[] listShot = null;
        if (rows > 0) { try { listShot = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { listShot = null; } }
        String searchActual = rows > 0 ? "Procedure Type \"" + type + "\" selected (of " + types.size() + " types); Search clicked - " + rows + " procedure(s) listed, first: \"" + cl.dialogRow(0)[0] + "\" (" + cl.dialogRow(0)[1] + ")"
                : "Problem — " + (type.isEmpty() ? "no Procedure Type selected: " : "Search listed nothing: ") + cl.problems;
        if (listShot != null)
            step(listShot, P + "Select Procedure Type and click Search", "Select Procedure Type \"" + procType + "\" and click Search", "The procedures of that type are listed", searchActual, rows > 0 ? "PASS" : "FAIL");
        else
            step(P + "Select Procedure Type and click Search", "Select Procedure Type \"" + procType + "\" and click Search", "The procedures of that type are listed", searchActual, rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("[Surgery] Result", "FAILED — no procedures listed: " + cl.problems); return; }

        // 4) tick one row; 5) Add - a row the screen cannot add ("already present", or one with no service item) is replaced by the next one
        List<String> notes = new java.util.ArrayList<>();
        String added = "";
        String[] first = null;
        int idx = 0;
        boolean ticked = cl.tickProcedure(idx);
        String[] row = cl.dialogRow(idx);
        first = row;
        step(P + "Tick the checkbox to select one of the procedures", "Tick the checkbox of one procedure in the list", "One procedure is selected",
                ticked ? "Row " + (idx + 1) + " of " + rows + " ticked: \"" + row[0] + "\" (Group " + row[1] + ", Sub Group " + row[2] + "); the screen marks only that row as selected" : "Problem — " + cl.problems,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("[Surgery] Result", "FAILED — the procedure could not be ticked: " + cl.problems); return; }

        boolean gained = false;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            int before = cl.surgeryRows().size();
            gained = cl.clickAdd(before);
            if (gained) { added = row[0]; break; }
            notes.add("row " + (idx + 1) + " \"" + row[0] + "\" was not added (" + cl.problems + ")");
            if (attempt == maxAttempts) break;
            // reopen the dialog (Add emptied it), pick the type, search, tick the next row
            idx++;
            boolean reopened = cl.clickGetProcedureDetails() && !cl.selectProcedureType(procType).isEmpty() && cl.clickSearch() > idx && cl.tickProcedure(idx);
            if (!reopened) { notes.add("could not reselect a procedure (" + cl.problems + ")"); break; }
            row = cl.dialogRow(idx);
        }
        List<String> table = cl.surgeryRows();
        step(P + "Click Add", "Click Add", "The selected procedure is added to the Surgery Description table",
                gained ? "Add clicked; the dialog closed and \"" + added + "\" now shows in the Surgery Description table (" + table.size() + " row(s): " + table + ")"
                        + (notes.isEmpty() ? "" : ". Before that: " + String.join("; ", notes))
                       : "Problem — " + String.join("; ", notes),
                gained ? "PASS" : "FAIL");

        addSummary("[Surgery] Procedure Type", type);
        addSummary("[Surgery] Procedures listed", rows + (first == null ? "" : " (first: " + first[0] + ")"));
        addSummary("[Surgery] Procedure added", gained ? added : "(none)");
        addSummary("[Surgery] Surgery Description table", table.toString());
        addSummary("[Surgery] Result", gained ? "All 5 Surgery Details steps passed; the procedure is in the table and the form is left unsaved" : "FAILED — no procedure could be added");
    }

    // =====================================================================================================================
    // Tab 3 - Doc/Emp Details
    // =====================================================================================================================

    /** A step whose screenshot is taken here with a 40 s limit (the framework's 10 s one times out on the big dialog lists). */
    private void stepL(String name, String desc, String expected, String actual, String status) {
        byte[] png = null;
        try { png = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { png = null; }
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(name, desc, expected, actual, status);
    }

    private static int indexOfContaining(List<String> rows, String name) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).toLowerCase().contains(name.trim().toLowerCase())) return i;
        return -1;
    }

    private void docEmpDetails(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[Doc/Emp Details] ";
        int failed = 0;
        boolean ok;

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("Doc/Emp Details");
        stepL(P + "Click Doc/Emp Details", "Add another tab - Doc/Emp Details; click Doc/Emp Details", "The Doc/Emp Details tab is open",
                opened ? "Doc/Emp Details is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); it holds the Doctor List ("
                        + cl.tableRows("Doctor Name").size() + " row(s)) with Get Doctors, and the Employee List (" + cl.tableRows("Employee Name").size() + " row(s)) with Get Staff"
                       : "Problem — " + cl.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[Doc/Emp] Result", "FAILED — the tab did not open: " + cl.problems); return; }

        // 2) Get Doctors
        boolean dlg = cl.clickGetDoctors();
        stepL(P + "Click Get Doctors", "Click Get Doctors", "The Doctor Search dialog is shown",
                dlg ? "The Doctor Search dialog opened (Location, Department, Classification, Search; the Select | Classification Name | Doctor table; OK and Close)" : "Problem — " + cl.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("[Doc/Emp] Result", "FAILED — the Doctor Search dialog did not open: " + cl.problems); return; }

        // 3) Location + Search
        List<String> locs = cl.doctorLocations();
        String loc = cl.selectDoctorLocation(location);
        int rows = loc.isEmpty() ? 0 : cl.searchDoctors();
        stepL(P + "Select Location and click Search", "Select Location" + (location.isEmpty() ? "" : " \"" + location + "\"") + " and click Search", "The doctors of that location are listed",
                rows > 0 ? "Location \"" + loc + "\" selected (of " + locs.size() + "); Search clicked - " + rows + " doctor(s) listed, first: \"" + cl.doctorRow(0)[1] + "\" (" + cl.doctorRow(0)[0] + ")"
                         : "Problem — " + (loc.isEmpty() ? "no Location selected: " : "Search listed nothing: ") + cl.problems,
                rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("[Doc/Emp] Result", "FAILED — no doctors listed: " + cl.problems); return; }

        // 4) tick the doctor
        int di = cl.doctorRowIndex(doctor);
        boolean ticked = di >= 0 && cl.tickDoctor(di);
        stepL(P + "Tick the checkbox to select a doctor", "Tick the checkbox of the doctor \"" + doctor + "\"", "The doctor is selected",
                ticked ? "Row " + (di + 1) + " of " + rows + " ticked: \"" + cl.doctorRow(di)[1] + "\" (" + cl.doctorRow(di)[0] + "); the screen marks only that row as selected"
                       : di < 0 ? "Problem — no doctor named \"" + doctor + "\" among the " + rows + " listed" : "Problem — " + cl.problems,
                ticked ? "PASS" : "FAIL");
        if (!ticked) { addSummary("[Doc/Emp] Result", "FAILED — the doctor could not be ticked: " + (di < 0 ? "not listed" : cl.problems)); return; }

        // 5) OK
        int doctorsBefore = cl.tableRows("Doctor Name").size();
        boolean closed = cl.clickDoctorOk();
        stepL(P + "Click OK", "Click OK in the Doctor Search dialog", "The dialog closes and the doctor is taken over",
                closed ? "OK clicked; the Doctor Search dialog closed" + (cl.alerts().isEmpty() ? "" : " (the screen said " + cl.alerts() + ")") : "Problem — " + cl.problems, closed ? "PASS" : "FAIL");
        if (!closed) { addSummary("[Doc/Emp] Result", "FAILED — OK: " + cl.problems); return; }

        // 6) verify the doctor is in the Doctor List table
        List<String> doctorRows = cl.tableRows("Doctor Name");
        int dri = indexOfContaining(doctorRows, doctor);
        ok = dri >= 0;
        stepL(P + "Verify the doctor is populated in the Doctor List", "Check the Doctor List table for \"" + doctor + "\"", "The selected doctor is listed in the Doctor List table",
                ok ? "PASSES because the Doctor List table (" + doctorsBefore + " -> " + doctorRows.size() + " row(s)) shows: \"" + doctorRows.get(dri) + "\" (Classification | Doctor Name | Surgery Name)"
                   : "FAILS because the Doctor List table (" + doctorRows + ") does not show \"" + doctor + "\"" + (cl.alerts().isEmpty() ? "" : "; the screen said " + cl.alerts()),
                ok ? "PASS" : "FAIL");
        if (!ok) { addSummary("[Doc/Emp] Result", "FAILED — the doctor is not in the Doctor List"); return; }

        // 7) Surgery Name of the doctor's row
        List<String> surgeries = cl.surgeryNameOptions("Doctor Name", dri);
        String sg = cl.selectSurgeryName("Doctor Name", dri, null);
        ok = !sg.isEmpty();
        stepL(P + "Select Surgery Name (doctor)", "Select the Surgery Name of the doctor's row", "A surgery is selected",
                ok ? "Surgery Name \"" + sg + "\" selected in the doctor's row (the select offers " + surgeries + "); the row now reads \"" + cl.tableRows("Doctor Name").get(dri) + "\" and the screen's model holds it"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        // 8) Get Staff
        dlg = cl.clickGetStaff();
        stepL(P + "Click Get Staff", "Click Get Staff", "The Staff Search dialog is shown",
                dlg ? "The Staff Search dialog opened (Designation, Staff Name, Search; the Select | Staff Name table; OK and Close)" : "Problem — " + cl.problems, dlg ? "PASS" : "FAIL");
        if (!dlg) { addSummary("[Doc/Emp] Result", "FAILED — the Staff Search dialog did not open: " + cl.problems); return; }

        // 9) Designation + Search
        List<String> desigs = cl.staffDesignations();
        String des = cl.selectDesignation(designation);
        int srows = des.isEmpty() ? 0 : cl.searchStaff();
        stepL(P + "Select Designation and click Search", "Select Designation \"" + designation + "\" and click Search", "The staff of that designation are listed",
                srows > 0 ? "Designation \"" + des + "\" selected (of " + desigs.size() + "); Search clicked - " + srows + " staff member(s) listed, first: \"" + cl.staffRow(0)[0] + "\""
                          : "Problem — " + (des.isEmpty() ? "no Designation selected: " : "Search listed nothing: ") + cl.problems,
                srows > 0 ? "PASS" : "FAIL");
        if (srows == 0) { addSummary("[Doc/Emp] Result", "FAILED — no staff listed: " + cl.problems); return; }

        // 10) tick the staff member
        int si = cl.staffRowIndex(staff);
        boolean sticked = si >= 0 && cl.tickStaff(si);
        stepL(P + "Tick the checkbox to select a staff member", "Tick the checkbox of \"" + staff + "\"", "The staff member is selected",
                sticked ? "Row " + (si + 1) + " of " + srows + " ticked: \"" + cl.staffRow(si)[0] + "\"; the screen marks only that row as selected"
                        : si < 0 ? "Problem — no staff member named \"" + staff + "\" among the " + srows + " listed" : "Problem — " + cl.problems,
                sticked ? "PASS" : "FAIL");
        if (!sticked) { addSummary("[Doc/Emp] Result", "FAILED — the staff member could not be ticked: " + (si < 0 ? "not listed" : cl.problems)); return; }

        // 11) OK
        int staffBefore = cl.tableRows("Employee Name").size();
        boolean sclosed = cl.clickStaffOk();
        List<String> empRows = cl.tableRows("Employee Name");
        int eri = indexOfContaining(empRows, staff);
        ok = sclosed && eri >= 0;
        stepL(P + "Click OK", "Click OK in the Staff Search dialog", "The dialog closes and the staff member is listed in the Employee List",
                ok ? "OK clicked; the Staff Search dialog closed and the Employee List table (" + staffBefore + " -> " + empRows.size() + " row(s)) shows: \"" + empRows.get(eri) + "\" (Employee Name | Surgery Name)"
                   : "Problem — " + (sclosed ? "the Employee List (" + empRows + ") does not show \"" + staff + "\"" : cl.problems) + (cl.alerts().isEmpty() ? "" : "; the screen said " + cl.alerts()),
                ok ? "PASS" : "FAIL");
        if (!ok) { addSummary("[Doc/Emp] Result", "FAILED — the staff member is not in the Employee List"); return; }

        // 12) Surgery Name of the employee's row
        List<String> surgeries2 = cl.surgeryNameOptions("Employee Name", eri);
        String sg2 = cl.selectSurgeryName("Employee Name", eri, null);
        ok = !sg2.isEmpty();
        stepL(P + "Select Surgery Name (staff)", "Select the Surgery Name of the staff member's row", "A surgery is selected",
                ok ? "Surgery Name \"" + sg2 + "\" selected in the staff member's row (the select offers " + surgeries2 + "); the row now reads \"" + cl.tableRows("Employee Name").get(eri) + "\" and the screen's model holds it"
                   : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
        if (!ok) failed++;

        addSummary("[Doc/Emp] Doctors listed / chosen", rows + " / " + doctor);
        addSummary("[Doc/Emp] Doctor List table", cl.tableRows("Doctor Name").toString());
        addSummary("[Doc/Emp] Staff listed / chosen", srows + " (Designation " + des + ") / " + staff);
        addSummary("[Doc/Emp] Employee List table", cl.tableRows("Employee Name").toString());
        addSummary("[Doc/Emp] Result", failed == 0 ? "All 12 Doc/Emp Details steps passed; the doctor and the staff member are listed with their surgery and the form is left unsaved" : failed + " of 12 Doc/Emp Details steps failed");
    }

    // =====================================================================================================================
    // Tab 4 - OT Notes
    // =====================================================================================================================

    private int noteFailures;

    /** Short form of a note for the report: the first 70 characters and the length. */
    private static String brief(String t) {
        String s = t == null ? "" : t.replaceAll("\\s+", " ").trim();
        return "\"" + (s.length() > 70 ? s.substring(0, 70) + "..." : s) + "\" (" + (t == null ? 0 : t.length()) + " characters)";
    }

    /**
     * One note of Surgery Notes / Anesthesia Notes: click its button, Search, tick the first template, OK - each its own step - and check the column's text area was filled.
     *
     * @param searchInScript false for the one note whose Search the script leaves out (the list is empty until Search, so it is clicked anyway and the step says so)
     * @return true when all four steps passed
     */
    private boolean noteFlow(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl, String P, String button, boolean searchInScript) {
        // click the button
        boolean open = cl.clickNoteButton(button);
        stepL(P + "Click " + button, "Click " + button, "The note search dialog is shown",
                open ? "The dialog \"" + cl.noteDialogTitle() + "\" opened; its list holds " + cl.noteRowCount() + " row(s) until Search is clicked" : "Problem — " + cl.problems, open ? "PASS" : "FAIL");
        if (!open) { noteFailures++; return false; }

        // Search
        int rows = cl.clickNoteSearch();
        stepL(P + button + " - click Search", searchInScript ? "In the popup, click Search" : "Click Search (the script leaves it out, but the list is empty until Search is clicked)", "The note templates are listed",
                rows > 0 ? "Search clicked - " + rows + " template(s) listed, first: \"" + cl.noteRowName(0) + "\"" : "Problem — " + cl.problems, rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { cl.closeNoteDialog(); noteFailures++; return false; }

        // tick one row
        boolean ticked = cl.tickNote(0);
        stepL(P + button + " - tick a row", "Tick the checkbox of one row", "One template is selected",
                ticked ? "Row 1 of " + rows + " ticked: \"" + cl.noteRowName(0) + "\"; the screen marks only that row as selected" : "Problem — " + cl.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) { cl.closeNoteDialog(); noteFailures++; return false; }

        // OK
        String chosen = cl.noteRowName(0);
        boolean closed = cl.clickNoteOk();
        String text = closed ? cl.noteTextOf(button) : "";
        boolean filled = closed && !text.trim().isEmpty();
        stepL(P + button + " - click OK", "Click OK", "The dialog closes and the chosen template's text fills the note box",
                filled ? "OK clicked; the dialog closed and the " + button + " box now holds the text of \"" + chosen + "\": " + brief(text)
                       : closed ? "FAILS because the dialog closed but the " + button + " box is still empty (blank)" : "Problem — " + cl.problems,
                filled ? "PASS" : "FAIL");
        if (!filled) { noteFailures++; return false; }
        addSummary("[OT Notes] " + button, chosen + " -> " + brief(text));
        return true;
    }

    private void otNotes(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[OT Notes] ";
        noteFailures = 0;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("OT Notes");
        stepL(P + "Click OT Notes", "Add another tab - OT Notes; click OT Notes", "The OT Notes tab is open",
                opened ? "OT Notes is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); it has its own sub tabs: " + cl.subTabNames() + " (open: " + cl.activeSubTab() + ")"
                       : "Problem — " + cl.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[OT Notes] Result", "FAILED — the tab did not open: " + cl.problems); return; }

        // 2) Surgery Notes
        boolean sub = cl.clickTab("Surgery Notes");
        stepL(P + "Click Surgery Notes", "Add a sub tab under OT Notes - Surgery Notes; click it", "The Surgery Notes sub tab is open",
                sub ? "Surgery Notes is a sub tab of OT Notes (sub tabs: " + cl.subTabNames() + "); clicked it - it is open (pane " + cl.lastTabPane + ") with Pre / Intra / Post Surgery Notes"
                    : "Problem — " + cl.problems, sub ? "PASS" : "FAIL");
        if (sub) {
            noteFlow(cl, P, "Pre Surgery Notes", true);
            noteFlow(cl, P, "Intra Surgery Notes", true);
            noteFlow(cl, P, "Post Surgery Notes", false);
        } else noteFailures++;

        // 3) Anesthesia Notes
        sub = cl.clickTab("Anesthesia Notes");
        stepL(P + "Click Anesthesia Notes", "Add a sub tab - Anesthesia Notes; click Anesthesia Notes", "The Anesthesia Notes sub tab is open",
                sub ? "Anesthesia Notes is a sub tab of OT Notes; clicked it - it is open (pane " + cl.lastTabPane + ") with Pre / Intra / Post Anesthesia Notes" : "Problem — " + cl.problems, sub ? "PASS" : "FAIL");
        if (sub) {
            noteFlow(cl, P, "Pre Anesthesia Notes", true);
            noteFlow(cl, P, "Intra Anesthesia Notes", true);
            noteFlow(cl, P, "Post Anesthesia Notes", true);
        } else noteFailures++;

        // 4) Perfusionist Notes
        sub = cl.clickTab("Perfusionist Notes");
        stepL(P + "Click Perfusionist Notes", "Add a sub tab - Perfusionist Notes; click Perfusionist Notes", "The Perfusionist Notes sub tab is open",
                sub ? "Perfusionist Notes is a sub tab of OT Notes; clicked it - it is open (pane " + cl.lastTabPane + ") with the Pre-, Intra- and Post-Perfusionist Notes boxes" : "Problem — " + cl.problems, sub ? "PASS" : "FAIL");
        if (sub) {
            cl.resetProblems();
            String pre = "QA Cath Lab pre-perfusionist note - circuit primed, heparin plan reviewed - " + stamp;
            String intra = "QA Cath Lab intra-perfusionist note - flows and pressures stable, ACT in range - " + stamp;
            String post = "QA Cath Lab post-perfusionist note - weaned from support, handover given - " + stamp;
            boolean a = cl.typeNote("PerfnistNotes.PerfnistnoteDescription", pre);
            boolean b = cl.typeNote("PerfnistNotes.intraperfusionistnotes", intra);
            boolean c = cl.typeNote("PerfnistNotes.postperfusionistnotes", post);
            boolean ok = a && b && c;
            stepL(P + "Enter Pre-, Intra- and Post-Perfusionist Notes", "Type a note in Pre-Perfusionist Notes, Intra-Perfusionist Notes and Post-Perfusionist Notes", "All three notes are accepted",
                    ok ? "Typed: Pre-Perfusionist \"" + pre + "\"; Intra-Perfusionist \"" + intra + "\"; Post-Perfusionist \"" + post + "\" - the boxes and the screen's model hold all three" : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
            if (!ok) noteFailures++;
            else { addSummary("[OT Notes] Pre-Perfusionist Notes", pre); addSummary("[OT Notes] Intra-Perfusionist Notes", intra); addSummary("[OT Notes] Post-Perfusionist Notes", post); }
        } else noteFailures++;

        // 5) Nursing Notes
        sub = cl.clickTab("Nursing Notes");
        stepL(P + "Click Nursing Notes", "Add a sub tab - Nursing Notes; click Nursing Notes", "The Nursing Notes sub tab is open",
                sub ? "Nursing Notes is a sub tab of OT Notes; clicked it - it is open (pane " + cl.lastTabPane + ") with one note box" : "Problem — " + cl.problems, sub ? "PASS" : "FAIL");
        if (sub) {
            cl.resetProblems();
            String nurse = "QA Cath Lab nursing note - identity confirmed, vitals stable, access site checked, handed over to recovery - " + stamp;
            boolean ok = cl.typeNote("NursingNotes.Nursingnotedescription", nurse);
            stepL(P + "Enter Nursing Notes", "Type a nursing note", "The note is accepted",
                    ok ? "Typed \"" + nurse + "\" - the box and the screen's model hold it" : "Problem — " + cl.problems, ok ? "PASS" : "FAIL");
            if (!ok) noteFailures++; else addSummary("[OT Notes] Nursing Notes", nurse);
        } else noteFailures++;

        addSummary("[OT Notes] Result", noteFailures == 0 ? "All OT Notes steps passed (Surgery, Anesthesia, Perfusionist and Nursing notes are filled); the form is left unsaved" : noteFailures + " OT Notes part(s) failed");
    }

    // =====================================================================================================================
    // Tab 5 - Post-Operative Instructions
    // =====================================================================================================================

    private void postOp(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[Post-Operative Instructions] ";
        int failed = 0;

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("Post-Operative Instructions");
        stepL(P + "Click Post-Operative Instructions", "Add next tab - Post-Operative Instructions; click Post-Operative Instructions", "The Post-Operative Instructions tab is open",
                opened ? "Post-Operative Instructions is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); it has the Post-Operative Instructions dropdown, Add and the Instruction table ("
                        + cl.postInstructionRows().size() + " row(s) so far)"
                       : "Problem — " + cl.problems,
                opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("[Post-Op] Result", "FAILED — the tab did not open: " + cl.problems); return; }

        // 2) the dropdown
        List<String> options = cl.postInstructionOptions();
        String chosen = cl.selectPostInstruction(postInstruction);
        stepL(P + "Select Post-Operative Instructions", "Select an instruction in the Post-Operative Instructions dropdown (\"" + postInstruction + "\")", "An instruction is selected",
                !chosen.isEmpty() ? "Instruction \"" + chosen + "\" selected (of " + options.size() + "); the box shows \"" + cl.postInstructionShown() + "\"" : "Problem — " + cl.problems,
                !chosen.isEmpty() ? "PASS" : "FAIL");
        if (chosen.isEmpty()) { addSummary("[Post-Op] Result", "FAILED — no instruction selected: " + cl.problems); return; }

        // 3) Add
        int before = cl.postInstructionRows().size();
        boolean added = cl.clickAddPostInstruction(before);
        List<String> rows = cl.postInstructionRows();
        boolean listed = added && rows.stream().anyMatch(r -> r.equalsIgnoreCase(chosen));
        if (!added) cl.dismissAlertDialog();
        stepL(P + "Click Add", "Click Add", "The instruction is added to the Instruction table",
                listed ? "Add clicked; the Instruction table (" + before + " -> " + rows.size() + " row(s)) now shows \"" + chosen + "\"" : "Problem — " + (added ? "the table (" + rows + ") does not show \"" + chosen + "\"" : cl.problems),
                listed ? "PASS" : "FAIL");
        if (!listed) failed++;

        addSummary("[Post-Op] Instruction chosen", chosen + " (of " + options.size() + " in the dropdown)");
        addSummary("[Post-Op] Instruction table", rows.toString());
        addSummary("[Post-Op] Result", failed == 0 ? "All 3 Post-Operative Instructions steps passed; the instruction is listed and the form is left unsaved" : "FAILED — the instruction was not added");
    }

    // =====================================================================================================================
    // Tab 6 - Preoperative check List
    // =====================================================================================================================

    private void preOp(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[Preoperative check List] ";
        int failed = 0;
        boolean ok;

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("Preoperative check List");
        List<String> rows = opened ? cl.checklistRows() : List.of();
        stepL(P + "Click Preoperative check List", "Add next tab - Preoperative Check list; click Preoperative check List", "The Preoperative check List tab is open",
                opened ? "Preoperative check List is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); the checklist table (Select | Id | Criteria | Admitting Nurse | OT Nurse) holds "
                        + rows.size() + " row(s): " + rows + "; below it the Admitting Nurse dropdown with its sign pad (and the OT Nurse ones)"
                       : "Problem — " + cl.problems,
                opened && !rows.isEmpty() ? "PASS" : "FAIL");
        if (!opened || rows.isEmpty()) { addSummary("[Pre-Op] Result", "FAILED — " + (opened ? "the checklist table is empty" : "the tab did not open: " + cl.problems)); return; }

        // 2) the checklist row
        boolean ticked = cl.tickChecklistRow(0);
        stepL(P + "Tick the checkbox to select", "Tick the checkbox of the checklist row", "The row is selected",
                ticked ? "Row 1 (" + rows.get(0) + ") ticked; the screen marks it as selected" : "Problem — " + cl.problems, ticked ? "PASS" : "FAIL");
        if (!ticked) failed++;

        // 3) the Admitting Nurse radio
        boolean radio = cl.tickAdmittingNurseRadio(0, nurseAnswer);
        stepL(P + "Tick the Admitting Nurse radio button", "Tick the radio button of the Admitting Nurse column (" + nurseAnswer + ")", "The radio button is selected",
                radio ? "Admitting Nurse \"" + nurseAnswer + "\" ticked on row 1; the screen's model holds it" : "Problem — " + cl.problems, radio ? "PASS" : "FAIL");
        if (!radio) failed++;

        // 4) the Admitting Nurse dropdown
        List<String> nurses = cl.admittingNurseOptions();
        String chosen = cl.selectAdmittingNurse(nurse);
        stepL(P + "Select Admitting Nurse", "Select the Admitting Nurse in the dropdown (\"" + nurse + "\")", "A nurse is selected",
                !chosen.isEmpty() ? "Admitting Nurse \"" + chosen + "\" selected (of " + nurses.size() + " named nurses); the box shows \"" + cl.admittingNurseShown() + "\"" : "Problem — " + cl.problems,
                !chosen.isEmpty() ? "PASS" : "FAIL");
        if (chosen.isEmpty()) failed++;

        // 5) the Admitting Nurse Sign
        int before = cl.padInk("#wPaint");
        boolean signed = cl.signPad("#wPaint");
        int after = cl.padInk("#wPaint");
        stepL(P + "Sign the Admitting Nurse Sign", "Sign in the Admitting Nurse Sign pad", "The signature is drawn on the pad",
                signed ? "A signature was drawn on the Admitting Nurse Sign pad with the mouse; the pad went from " + before + " to " + after + " non-blank pixels (it holds ink)" : "Problem — " + cl.problems,
                signed ? "PASS" : "FAIL");
        if (!signed) failed++;

        addSummary("[Pre-Op] Checklist row", rows.get(0) + " - ticked");
        addSummary("[Pre-Op] Admitting Nurse radio", nurseAnswer);
        addSummary("[Pre-Op] Admitting Nurse", chosen);
        addSummary("[Pre-Op] Admitting Nurse Sign", signed ? "drawn (" + after + " non-blank pixels)" : "(not drawn)");
        addSummary("[Pre-Op] Result", failed == 0 ? "All 5 Preoperative check List steps passed; the form is left unsaved" : failed + " of 5 Preoperative check List steps failed");
    }

    // =====================================================================================================================
    // Tab 7 - Sign In - Time Out - Sign Out
    // =====================================================================================================================

    /** One column of the board: tick answer row 1 (the first group's first answer) and type remarks - a single step like the script's line. Returns true when both held. */
    private boolean checklistColumn(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl, String P, String heading, String scriptName, String stamp) {
        int count = cl.checkItemCount(heading);
        if (count == 0) {
            stepL(P + scriptName + ": tick a row and enter remarks", "In " + scriptName + ", tick the checkbox of one of the rows and enter remarks", "A row is ticked and its remarks entered",
                    "Problem — the board has no column headed \"" + heading + "\" with rows (columns: " + cl.checklistSections() + ")", "FAIL");
            return false;
        }
        String label = cl.checkItemLabel(heading, 0);
        boolean ticked = cl.tickCheckItem(heading, 0);
        String remark = ticked ? "QA Cath Lab " + scriptName.toLowerCase() + " remark - " + stamp : "";
        boolean typed = ticked && cl.enterCheckRemark(heading, 0, remark);
        stepL(P + scriptName + ": tick a row and enter remarks", "In " + scriptName + ", tick the checkbox of one of the rows and enter remarks", "A row is ticked and its remarks entered",
                typed ? "Column \"" + heading + "\" (" + count + " row(s)): row 1 (" + label + ") ticked - the screen holds it as ticked and the Remarks box appeared; remarks \"" + remark + "\" typed - the box and the screen's model hold them"
                      : "Problem — " + cl.problems + (ticked ? " (the row was ticked)" : ""),
                typed ? "PASS" : "FAIL");
        if (typed) { addSummary("[Sign In/Out] " + heading, label + " ticked; remarks: " + remark); }
        return typed;
    }

    private void signInOut(com.kpj.pages.OT_page.CathLab_page.CathLabDetails cl) {
        final String P = "[Sign In - Time Out - Sign Out] ";
        int failed = 0;
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddMMyy HHmmss"));

        // 1) the tab
        List<String> tabs = cl.tabNames();
        boolean opened = cl.clickTab("Sign In - Time Out - Sign Out");
        List<String> cols = opened ? cl.checklistSections() : List.of();
        stepL(P + "Click Sign In - Time Out - Sign Out", "Add another tab - Sign In - Time Out - Sign Out; click it", "The Sign In - Time Out - Sign Out tab is open",
                opened ? "Sign In - Time Out - Sign Out is a tab of the form (tabs: " + tabs + "); clicked it - it is the open tab (pane " + cl.lastTabPane + "); its checklist board has " + cols.size() + " columns: " + cols
                        + " and the Surgeon's / Anesthetist's / Scrub Nurse's / Circulating Nurse's sign pads below"
                       : "Problem — " + cl.problems,
                opened && !cols.isEmpty() ? "PASS" : "FAIL");
        if (!opened || cols.isEmpty()) { addSummary("[Sign In/Out] Result", "FAILED — " + (opened ? "the board is empty" : "the tab did not open: " + cl.problems)); return; }

        // 2) Sign-in, Time-out, the third column
        if (!checklistColumn(cl, P, "SIGN IN", "Sign-in", stamp)) failed++;
        if (!checklistColumn(cl, P, "TIME OUT", "Time-out", stamp)) failed++;
        if (!checklistColumn(cl, P, category3, category3, stamp)) failed++;

        addSummary("[Sign In/Out] Result", failed == 0 ? "All 4 Sign In - Time Out - Sign Out steps passed; one row ticked with remarks in each of the three columns; the form is left unsaved" : failed + " of 3 columns failed");
    }
}
