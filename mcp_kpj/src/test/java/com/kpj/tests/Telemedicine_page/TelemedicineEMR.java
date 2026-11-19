package com.kpj.tests.Telemedicine_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Telemedicine_page.TelemedicineEmrPage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ConsoleMessage;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.Timing;
import com.microsoft.playwright.options.WaitForSelectorState;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Telemedicine &gt; <b>EMR</b> ({@code #/EMRTele}) — READ-ONLY checks EMR-01 … EMR-13, plus API and console monitoring.
 *
 * <p>Converted from the manual check of 19/11/2026 on devhis. Every case reopens the screen through the menu
 * (Telemedicine &rarr; EMR) so it does not depend on the one before it; a failing case is recorded and the next one
 * still runs. Known defects are reported as FAIL when they reproduce — they are never passed just because the page
 * loaded.</p>
 *
 * <p><b>Safety.</b> Nothing is saved, sent, created or deleted: no Save / OK / Send button is ever clicked; dialogs
 * are closed with Close / Cancel; {@code window.print} is replaced by a no-op in this browser context so Print can
 * be checked without paper; and, as a safety net, any {@code /api/} call whose name looks like a write
 * (Save / Insert / Update / Delete / IUD / Send / Mail / ...) is <b>aborted</b> and listed in the report
 * ({@code -Demr.blockWrites=false} turns the net off).</p>
 *
 * <p>Report: {@code test-output/reports/Telemedicine_EMR.html}. Test data defaults (override with -D):
 * {@code emr.mrn=1100339387}, {@code emr.patient=Ahmad bin Ali}, {@code emr.visit=OP-00001089},
 * {@code emr.case=EMRCASE01923}, {@code emr.invalidMrn=XX99999999}, {@code emr.searchName=Ahmad bin Ali}.</p>
 */
public class TelemedicineEMR extends DevHisBase {

    // ---- test data (verified on devhis 19/11/2026; override per environment with -D) -----------------------------
    private static final String MRN          = System.getProperty("emr.mrn", "1100339387");
    private static final String PATIENT      = System.getProperty("emr.patient", "Ahmad bin Ali");
    private static final String VISIT_NO     = System.getProperty("emr.visit", "OP-00001089");
    private static final String CASE_NO      = System.getProperty("emr.case", "EMRCASE01923");
    private static final String INVALID_MRN  = System.getProperty("emr.invalidMrn", "XX99999999");
    private static final String SEARCH_NAME  = System.getProperty("emr.searchName", PATIENT);
    private static final String DEPARTMENT   = System.getProperty("emr.department", "PHYSICIAN");
    private static final String DOCTOR       = System.getProperty("emr.doctor", "Demo Doctor");
    private static final int    DUP_LIMIT    = Integer.getInteger("emr.duplicateThreshold", 10);
    private static final boolean BLOCK_WRITES = !"false".equalsIgnoreCase(System.getProperty("emr.blockWrites", "true"));

    /** The four sections the Telemedicine EMR keeps on screen (their tiles carry no TeleEMRHide). */
    private static final List<String> TELE_SECTIONS = List.of("Complaint", "LocalExamination", "Vital", "CPOEPrescription");

    /** /api/ calls that look like writes. Fetch / Get calls never match. */
    private static final Pattern WRITE_CALL = Pattern.compile(
            "/api/[^?]*?(?<!Fetch)(Save|Insert|Update|Delete|IUD|Send|Mail|Remove|Cancel|Finali[sz]e|Create|WebCam|VideoChat)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern FETCH_CALL = Pattern.compile("/api/[^?]*(Fetch|Get)", Pattern.CASE_INSENSITIVE);

    private TelemedicineEmrPage emr;
    private boolean patientAvailable = false;

    // case tallies for the summary (EMR-xx rows only)
    private int casesPass, casesFail, casesSkipped, casesBlocked;
    private final List<String> defectsSeen = new ArrayList<>();

    // monitoring
    private final ApiMonitor api = new ApiMonitor();
    private final Map<String, Integer> consoleErrors = new LinkedHashMap<>();
    private final List<String> dialogsSeen = new ArrayList<>();
    private final List<String> blockedWrites = new ArrayList<>();
    private String apiPhase = "setup";

    public TelemedicineEMR() { super("Telemedicine_EMR"); }

    public static void main(String[] args) {
        TelemedicineEMR t = new TelemedicineEMR();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // =============================================================================================================
    @Override
    protected void body() {
        meta("Telemedicine - EMR", "Telemedicine > EMR", "Read-only checks of Telemedicine &gt; EMR (#/EMRTele).");
        addSummary("Environment", BASE);
        addSummary("Test patient", MRN + " / " + PATIENT + " / visit " + VISIT_NO + " / case " + CASE_NO);

        if (!credentialsProvided()) {
            String why = "DEVHIS_USER and DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass) are not set - nothing was run";
            blockAll(why);
            finishReport();
            return;
        }
        addSummary("User", USER);

        emr = new TelemedicineEmrPage(page);
        Monitors monitors = installMonitors();
        try {
            LoginResult lr = loginWithEnvironmentAccount();
            String loginStep = "LOGIN - Log in with the DEVHIS_USER / DEVHIS_PASS account";
            String loginDesc = "Open " + BASE + ", enter the account from the environment (password not shown), click Login once";
            String loginExpected = "Login completes and the application shell with its menu loads";
            if (!lr.ok) {
                step(lr.shot, loginStep, loginDesc, loginExpected, "Not logged in: " + lr.reason
                        + (lr.diagnostics.isEmpty() ? "" : " — LOGIN PAGE STATE: " + lr.diagnostics), "BLOCKED");
                addSummary("Login", "NOT completed - " + lr.reason);
                blockAll("Login did not complete - " + lr.reason);
                return;
            }
            step(page, loginStep, loginDesc, loginExpected, "Logged in as " + USER + "; landed on " + lr.landedUrl
                    + (lr.landedUrl.contains("#/PatientDashboard") ? " (Patient Dashboard)" : "") + "; application menu loaded", "PASS");
            addSummary("Login", "Completed - landed on " + lr.landedUrl);
            monitors.armWriteGuard();

            runCase("EMR-01", "Verify EMR screen navigation",
                    "Open Telemedicine > EMR from the side menu; check the route and the screen's controls",
                    "Route #/EMRTele; MRN box, Search, Find Patient, Video Meeting, Visits, Cases, Department, Attend By and footer buttons visible",
                    this::verifyEmrScreenNavigation);
            runCase("EMR-02", "Verify empty MRN validation",
                    "Leave MRN empty; click Search",
                    "Message 'Please Enter MRN No!'",
                    this::verifyEmptyMrnValidation);
            runCase("EMR-03", "Verify invalid MRN",
                    "Enter MRN " + INVALID_MRN + "; click Search",
                    "Message 'Patient Not Found!'",
                    this::verifyInvalidMrn);
            runCase("EMR-04", "Verify valid MRN search (+ API monitoring of the patient load)",
                    "Enter MRN " + MRN + "; click Search; read the header, Visits and Cases",
                    "Patient " + PATIENT + " with NRIC; visit " + VISIT_NO + "; case " + CASE_NO,
                    this::verifyValidMrnSearch);
            runPatientCase("EMR-05", "Verify Find Patient popup",
                    "Click Find Patient; search name '" + SEARCH_NAME + "'; select MRN " + MRN + "; read the header, Visits and Cases",
                    "Popup opens; patient, visits and cases populate; NRIC populated (same as an MRN search)",
                    this::verifyFindPatientPopup);
            runPatientCase("EMR-06", "Verify Enter key search",
                    "Type MRN " + MRN + "; press Enter in the MRN box",
                    "Enter triggers the search and the patient loads",
                    this::verifyEnterKeySearch);
            runPatientCase("EMR-07", "Verify clinical sections visibility",
                    "Load the patient; read every clinical section tile, its hide flag and on-screen state (no setting changed)",
                    "Complaints, Examination, Vitals and CPOE Prescription visible on the Telemedicine EMR",
                    this::verifyClinicalSectionsVisibility);
            runPatientCase("EMR-08", "Verify Video Meeting button",
                    "Check the button with no patient; load the patient; click Video Meeting; read the e-mail; Cancel (OK is never pressed)",
                    "Disabled without a patient; enabled after loading; e-mail dialog shows the patient's address; closes on Cancel",
                    this::verifyVideoMeetingButton);
            runPatientCase("EMR-09", "Verify View Case",
                    "Load the patient; click View on case " + CASE_NO + "; read MRN, Episode No, NRIC, Department, Doctor; check labels; Close (never Save)",
                    "MRN " + MRN + ", Episode " + VISIT_NO + ", NRIC filled, Department " + DEPARTMENT + ", Doctor " + DOCTOR + "; no clipped labels",
                    this::verifyViewCase);
            runPatientCase("EMR-10", "Verify Email To Refer Doctor",
                    "Load the patient; inspect the button's handler; click only when it cannot send anything; watch for a dialog, route change or request",
                    "The button opens the refer-doctor e-mail form (nothing is sent)",
                    this::verifyEmailToReferDoctor);
            runPatientCase("EMR-11", "Verify Discharge Summary (outpatient)",
                    "Load the outpatient test patient; check the Discharge Summary button",
                    "Disabled for an OPD visit",
                    this::verifyDischargeSummaryForOutpatient);
            runPatientCase("EMR-12a", "Verify navigation: ICU Management",
                    "Load the patient; click ICU Management",
                    "ICU Management opens (new tab) and loads without an error page",
                    this::verifyIcuManagementNavigation);
            runPatientCase("EMR-12b", "Verify navigation: OT Room",
                    "Load the patient; click OT Room",
                    "Route #/OTDetailsList loads without an error message",
                    () -> verifyInAppNavigation("OT Room", "#/OTDetailsList"));
            runPatientCase("EMR-12c", "Verify navigation: Bills",
                    "Load the patient; click Bills",
                    "Route #/BillList loads without an error message",
                    () -> verifyInAppNavigation("Bills", "#/BillList"));
            runPatientCase("EMR-13", "Verify Print",
                    "Load the patient; click Print (window.print is stubbed - nothing reaches a printer); read the report tab",
                    "EMR report opens in a new tab without a server error",
                    this::verifyPrint);

            recordApiMonitoringRow();
        } finally {
            monitors.remove();
            finishReport();
        }
    }

    // =============================================================================================================
    //  Cases
    // =============================================================================================================

    private Outcome verifyEmrScreenNavigation() {
        String prefilled = emr.openViaMenu(BASE);
        List<String> missing = new ArrayList<>();
        Map<String, Locator> expected = new LinkedHashMap<>();
        expected.put("MRN box", emr.mrnInput());
        expected.put("Search button", emr.searchButton());
        expected.put("Find Patient button", emr.findPatientButton());
        expected.put("Video Meeting button", emr.videoMeetingButton());
        expected.put("Visits panel", page.locator("main").getByText("Visits", new Locator.GetByTextOptions().setExact(true)).first());
        expected.put("Cases panel", page.locator("main").getByText("Cases", new Locator.GetByTextOptions().setExact(true)).first());
        expected.put("Department", page.locator("main").getByText("Department", new Locator.GetByTextOptions().setExact(true)).first());
        expected.put("Attend By", page.locator("main").getByText("Attend By", new Locator.GetByTextOptions().setExact(true)).first());
        for (String b : List.of("ICU Management", "OT Room", "Email To Refer Doctor", "Bills", "Discharge Summary", "Print", "Back"))
            expected.put(b + " button", emr.footerButton(b));
        for (Map.Entry<String, Locator> e : expected.entrySet()) if (!isVisible(e.getValue())) missing.add(e.getKey());

        String actual = "Menu Telemedicine > EMR opened " + hashOf(page.url()) + "; "
                + (missing.isEmpty() ? "all " + expected.size() + " expected controls visible" : "missing: " + String.join(", ", missing));
        String remarks = prefilled.isEmpty() ? "" : "MRN box already held '" + prefilled + "' when the screen opened";
        return missing.isEmpty() && hashOf(page.url()).equals(TelemedicineEmrPage.ROUTE)
                ? Outcome.pass(actual, remarks) : Outcome.fail(actual, remarks);
    }

    private Outcome verifyEmptyMrnValidation() {
        emr.openViaMenu(BASE);
        emr.typeMrn("");
        emr.armAlerts();
        emr.searchButton().click();
        boolean shown = emr.waitForAlert("please\\s*enter.*mr", 8000);
        byte[] shot = page.screenshot();
        String msgs = String.join(" | ", emr.alerts());
        return shown ? Outcome.pass("Message: " + msgs, "").withShot(shot)
                     : Outcome.fail("No 'Please Enter MRN' message" + (msgs.isEmpty() ? "" : " (messages: " + msgs + ")"), "").withShot(shot);
    }

    private Outcome verifyInvalidMrn() {
        emr.openViaMenu(BASE);
        emr.typeMrn(INVALID_MRN);
        emr.armAlerts();
        emr.searchButton().click();
        boolean shown = emr.waitForAlert("patient\\s*not\\s*found", 20000);
        byte[] shot = page.screenshot();
        String msgs = String.join(" | ", emr.alerts());
        String name = emr.patientHeader().getOrDefault("name", "");
        String actual = (shown ? "Message: " + msgs : "No 'Patient Not Found' message" + (msgs.isEmpty() ? "" : " (messages: " + msgs + ")"))
                + (name.isEmpty() ? "; header stays empty" : "; header shows '" + name + "'");
        return shown && name.isEmpty() ? Outcome.pass(actual, "").withShot(shot) : Outcome.fail(actual, "").withShot(shot);
    }

    private Outcome verifyValidMrnSearch() {
        emr.openViaMenu(BASE);
        emr.typeMrn(MRN);
        api.start();
        apiPhase = "patient load (EMR-04)";
        emr.armAlerts();
        emr.searchButton().click();
        emr.waitForPatientOrMessage(30000);
        boolean idle = emr.waitForHttpIdle(90000);
        api.stop();
        apiPhase = "after patient load";

        List<String> msgs = emr.alerts();
        String setData = "Set the test data for " + BASE + " with -Demr.mrn / -Demr.patient / -Demr.visit / -Demr.case"
                + " (an OPD patient with a visit and an EMR case on that environment)";
        if (msgs.stream().anyMatch(m -> m.matches("(?is).*patient\\s*not\\s*found.*"))) {
            patientBlockReason = "test data unavailable - MRN " + MRN + " does not exist on " + BASE;
            return Outcome.blocked("Patient Not Found for MRN " + MRN + " on " + BASE, setData);
        }
        Map<String, String> h = emr.patientHeader();
        List<String> visits = emr.visitRows();
        List<String> cases = emr.caseRows();
        String name = h.getOrDefault("name", "");
        boolean nameOk = name.equalsIgnoreCase(PATIENT);
        boolean visitOk = visits.stream().anyMatch(v -> v.contains(VISIT_NO));
        boolean caseOk = cases.stream().anyMatch(c -> c.contains(CASE_NO));
        directSearchNric = h.getOrDefault("nric", "");

        // Application messages raised while the patient loaded (e.g. "Error in getting record!") - real findings either way.
        List<String> appErrors = msgs.stream().filter(m -> m.matches("(?is).*(error|fail|exception|invalid).*")).distinct().toList();
        String remarks = (appErrors.isEmpty() ? "" : "Application error messages during the load: " + String.join(" | ", appErrors) + ". ")
                + (idle ? "" : "Screen still had requests in flight after 90 s. ")
                + api.headline();
        String found = "visits listed " + recordIds(visits, "(OP|IP|ER)-\\d+") + ", cases listed " + recordIds(cases, "EMRCASE\\d+");

        if (name.isEmpty()) {
            patientBlockReason = "the MRN search in EMR-04 showed no patient";
            return Outcome.fail("Search for MRN " + MRN + " showed no patient in the header and no 'Patient Not Found' message; " + found, remarks);
        }
        if (!nameOk) {
            // The search worked, but this MRN is a different patient on this environment: the configured test data does
            // not exist here. That is a missing prerequisite, not an application defect (no patient details are reported).
            patientBlockReason = "test data mismatch - MRN " + MRN + " is a different patient on " + BASE + " (not '" + PATIENT + "')";
            return Outcome.blocked("Search worked, but MRN " + MRN + " on this environment belongs to a different patient (not '"
                    + PATIENT + "'); " + found + "; expected visit " + VISIT_NO + " and case " + CASE_NO, setData + ". " + remarks);
        }
        String actual = "Patient '" + name + "' loaded (NRIC " + (directSearchNric.isEmpty() ? "BLANK" : "present") + "); "
                + found + "; expected visit " + VISIT_NO + (visitOk ? " listed" : " NOT listed") + ", case " + CASE_NO + (caseOk ? " listed" : " NOT listed");
        if (visitOk && caseOk) {
            patientAvailable = true;
            return Outcome.pass(actual, remarks);
        }
        if (visits.isEmpty() || cases.isEmpty()) {
            patientBlockReason = "the test patient loaded in EMR-04 without its " + (visits.isEmpty() ? "visits" : "cases");
            return Outcome.fail(actual, "The " + (visits.isEmpty() ? "Visits" : "Cases") + " table is empty for a patient that has records. " + remarks);
        }
        patientBlockReason = "test data mismatch - visit " + VISIT_NO + " / case " + CASE_NO + " not found for MRN " + MRN + " on " + BASE;
        return Outcome.blocked(actual, setData + ". " + remarks);
    }

    /** Why the patient-dependent cases cannot run (set by EMR-04). */
    private String patientBlockReason = "the test patient could not be loaded in EMR-04";

    /** Record numbers (visit / case IDs) found in table rows - identifiers only, no patient details. */
    private static String recordIds(List<String> rows, String regex) {
        java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
        java.util.regex.Matcher m = Pattern.compile(regex).matcher(String.join(" ", rows));
        while (m.find()) ids.add(m.group());
        return ids.isEmpty() ? "none" : ids.toString();
    }

    /** NRIC seen after the direct MRN search (EMR-04), for comparison with the popup path. */
    private String directSearchNric = "";

    private Outcome verifyFindPatientPopup() {
        String prefilled = emr.openViaMenu(BASE);
        emr.findPatientButton().click();
        Locator popup = emr.patientSearchPopup();
        popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(15000));
        String title = emr.dialogTitle(popup);
        emr.closePatientSearchPopup();

        String problem = emr.selectPatientFromPopup(SEARCH_NAME, MRN, 5);
        if (!problem.isEmpty()) return Outcome.fail("Popup '" + title + "' opened; " + problem, "");

        Map<String, String> h = emr.patientHeader();
        boolean visitOk = emr.visitRows().stream().anyMatch(v -> v.contains(VISIT_NO));
        boolean caseOk = emr.caseRows().stream().anyMatch(c -> c.contains(CASE_NO));
        boolean nameOk = h.getOrDefault("name", "").equalsIgnoreCase(PATIENT);
        String nric = h.getOrDefault("nric", "");
        String actual = "Popup '" + title + "' opened; selected " + MRN + "; header name '" + h.get("name") + "', NRIC " + (nric.isEmpty() ? "BLANK" : "present")
                + "'; visit " + (visitOk ? VISIT_NO + " listed" : VISIT_NO + " missing") + "; case " + (caseOk ? CASE_NO + " listed" : CASE_NO + " missing");
        String remarks = !prefilled.isEmpty() ? "MRN box already held '" + prefilled + "' when the screen opened, so this was not a clean start. " : "";
        if (nric.isEmpty()) {
            defectsSeen.add("EMR-05 NRIC blank after Find Patient");
            remarks += "KNOWN DEFECT REPRODUCED: NRIC is blank after selecting through Find Patient"
                    + (directSearchNric.isEmpty() ? "" : " (a direct MRN search shows it)");
            return Outcome.fail(actual, remarks);
        }
        if (!nameOk || !visitOk || !caseOk) return Outcome.fail(actual, remarks);
        return Outcome.pass(actual, remarks + "Known defect (blank NRIC) did NOT reproduce in this run");
    }

    private Outcome verifyEnterKeySearch() {
        emr.openViaMenu(BASE);
        emr.typeMrn(MRN);
        emr.armAlerts();
        boolean triggered;
        try {
            page.waitForResponse(r -> r.url().contains("FetchPatientData"), new Page.WaitForResponseOptions().setTimeout(6000),
                    () -> emr.mrnInput().press("Enter"));
            triggered = true;
        } catch (PlaywrightException timeout) {
            triggered = false;
        }
        if (triggered) { emr.waitForPatientOrMessage(20000); emr.waitForHttpIdle(60000); }
        String name = emr.patientHeader().getOrDefault("name", "");
        String actual = triggered ? "Enter started the search; header name '" + name + "'"
                                  : "Enter did nothing within 6 s: no patient request was sent and the header stayed empty";
        if (!triggered || name.isEmpty()) {
            defectsSeen.add("EMR-06 Enter does not search");
            return Outcome.fail(actual, "KNOWN DEFECT REPRODUCED: Enter in the MRN box does not trigger Search (the box has no key handler / form submit)");
        }
        return Outcome.pass(actual, "Known defect (Enter ignored) did NOT reproduce in this run");
    }

    private Outcome verifyClinicalSectionsVisibility() {
        loadTestPatient();
        Map<String, Object> s = emr.clinicalSections();
        List<String> hiddenTele = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        Object list = s.get("sections");
        if (list instanceof List) {
            for (Object o : (List<?>) list) {
                if (!(o instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) o;
                String name = String.valueOf(m.get("name"));
                boolean visible = Boolean.TRUE.equals(m.get("visible"));
                if (all.length() > 0) all.append("; ");
                all.append(name).append(visible ? " VISIBLE" : " hidden")
                   .append(" (").append(m.get("flag")).append("=").append(m.get("flagValue"));
                String hideExpr = String.valueOf(m.get("hideExpr"));
                if (!hideExpr.isEmpty()) all.append(", ng-hide ").append(hideExpr).append("=").append(m.get("hideValue"));
                all.append(")");
                if (TELE_SECTIONS.contains(name) && !visible) hiddenTele.add(name);
            }
        }
        String extras = "Allergies " + describeBlock(s.get("allergies")) + "; Medical/Family History " + describeBlock(s.get("history"))
                + "; TeleEMRHide=" + s.get("teleEmrHide") + ", OPDEMRHide=" + s.get("opdEmrHide");
        addSummary("EMR-07 section flags", all + " | " + extras);
        String actual = hiddenTele.isEmpty() ? "Telemedicine sections visible: " + String.join(", ", TELE_SECTIONS)
                : "Hidden although expected on Telemedicine EMR: " + String.join(", ", hiddenTele);
        String remarks = "All tiles: " + all + ". " + extras + ". Read only - no configuration changed.";
        if (!hiddenTele.isEmpty()) {
            defectsSeen.add("EMR-07 Telemedicine clinical sections hidden");
            return Outcome.fail(actual, "KNOWN OBSERVATION REPRODUCED (check against the FSD / a doctor login - may be role configuration). " + remarks);
        }
        return Outcome.pass(actual, remarks);
    }

    private Outcome verifyVideoMeetingButton() {
        String prefilled = emr.openViaMenu(BASE);
        emr.typeMrn("");
        boolean disabledWithoutPatient = emr.videoMeetingButton().isDisabled();
        loadPatientOnCurrentScreen();
        boolean enabledWithPatient = emr.videoMeetingButton().isEnabled();
        if (!enabledWithPatient) return Outcome.fail("Video Meeting still disabled after loading " + MRN, "");

        emr.videoMeetingButton().click();
        Locator popup = emr.videoEmailPopup();
        popup.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
        String title = emr.dialogTitle(popup);
        String email = popup.locator("input[ng-model='VideoChat.EmailID']").inputValue().trim();
        byte[] shot = page.screenshot();
        emr.cancelVideoEmailPopup();                       // never OK - OK sends the invitation
        boolean closed = emr.videoEmailPopup().count() == 0;

        String actual = "Without patient: " + (disabledWithoutPatient ? "disabled" : "ENABLED")
                + "; with patient: enabled; dialog '" + title + "' e-mail '" + email + "'; Cancel " + (closed ? "closed it" : "did NOT close it");
        String remarks = "OK was not pressed - no invitation sent."
                + (prefilled.isEmpty() ? "" : " MRN box already held '" + prefilled + "' on open.")
                + " The enable rule is ng-disabled=\"EMR.mrno == null || EMR.mrno ==''\": it follows the MRN TEXT, not a loaded patient.";
        if (!title.isEmpty() && !title.contains(" ")) remarks += " Dialog title '" + title + "' is an internal field name (no spaces).";
        boolean ok = disabledWithoutPatient && !email.isEmpty() && closed;
        return (ok ? Outcome.pass(actual, remarks) : Outcome.fail(actual, remarks)).withShot(shot);
    }

    private Outcome verifyViewCase() {
        loadTestPatient();
        emr.openViewCase(CASE_NO);
        Map<String, Object> d = emr.viewCaseDetails();
        byte[] shot = page.screenshot();
        emr.closeViewCase();                               // never Save

        Map<?, ?> in = d.get("inputs") instanceof Map ? (Map<?, ?>) d.get("inputs") : Map.of();
        String mrn = str(in.get("EMRCase.mrno")), episode = str(in.get("EMRCase.EPisodeNo")), nric = str(in.get("EMRCase.NRIC")),
               name = str(in.get("EMRCase.PatientName"));
        List<String> selectTexts = new ArrayList<>();
        if (d.get("selects") instanceof List) for (Object o : (List<?>) d.get("selects"))
            if (o instanceof Map) selectTexts.add(str(((Map<?, ?>) o).get("text")));
        boolean deptOk = selectTexts.stream().anyMatch(t -> t.equalsIgnoreCase(DEPARTMENT));
        boolean docOk = selectTexts.stream().anyMatch(t -> t.equalsIgnoreCase(DOCTOR));
        List<String> clipped = new ArrayList<>();
        if (d.get("labels") instanceof List) for (Object o : (List<?>) d.get("labels"))
            if (o instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) o).get("clipped"))) clipped.add(str(((Map<?, ?>) o).get("text")));

        String actual = "MRN '" + mrn + "', Episode '" + episode + "', NRIC " + (nric.isEmpty() ? "BLANK" : "present") + ", Name '" + name + "', dropdowns " + selectTexts
                + "; labels " + (clipped.isEmpty() ? "all within the dialog" : "CLIPPED: " + clipped) + "; closed with Close (no Save)";
        List<String> wrong = new ArrayList<>();
        if (!MRN.equals(mrn)) wrong.add("MRN");
        if (!VISIT_NO.equals(episode)) wrong.add("Episode No");
        if (nric.isEmpty()) wrong.add("NRIC blank");
        if (!deptOk) wrong.add("Department");
        if (!docOk) wrong.add("Doctor");
        if (!clipped.isEmpty()) wrong.add("clipped labels");
        String remarks = "The dialog is titled View Case but its fields can be edited and it has a Save button.";
        return (wrong.isEmpty() ? Outcome.pass(actual, remarks) : Outcome.fail(actual, "Mismatch: " + wrong + ". " + remarks)).withShot(shot);
    }

    private Outcome verifyEmailToReferDoctor() {
        loadTestPatient();
        Locator btn = emr.footerButton("Email To Refer Doctor");
        String handler = btn.getAttribute("ng-click");
        String h = handler == null ? "" : handler.trim();
        if (h.matches("(?i).*(send|mail|save|submit).*")) {
            return Outcome.skipped("Handler '" + h + "' may send an e-mail - not clicked", "Check this button manually");
        }
        int dialogsBefore = emr.openDialogCount();
        String hashBefore = hashOf(page.url());
        int callsBefore = api.totalSeen();
        int pagesBefore = context().pages().size();
        btn.click();
        boolean reacted;
        try {
            page.waitForFunction("([d, h]) => [...document.querySelectorAll('.modal.in')].filter(m => m.offsetWidth || m.offsetHeight).length > d"
                            + " || location.hash !== h", List.of(dialogsBefore, hashBefore),
                    new Page.WaitForFunctionOptions().setTimeout(4000).setPollingInterval(200));
            reacted = true;
        } catch (PlaywrightException none) {
            reacted = false;
        }
        boolean newTab = context().pages().size() > pagesBefore;
        boolean newCalls = api.totalSeen() > callsBefore;
        byte[] shot = page.screenshot();
        if (reacted && emr.openDialogCount() > dialogsBefore) page.keyboard().press("Escape");   // close whatever opened, never send
        String actual = "ng-click=\"" + h + "\"; after the click: " + (reacted || newTab || newCalls
                ? (reacted ? "a dialog / route change appeared" : "") + (newTab ? " a new tab opened" : "") + (newCalls ? " requests were sent" : "")
                : "nothing happened (no dialog, no route change, no tab, no request)");
        if (!reacted && !newTab && !newCalls) {
            defectsSeen.add("EMR-10 Email To Refer Doctor does nothing");
            String remarks = "KNOWN DEFECT REPRODUCED" + ("()".equals(h) ? ": the handler ng-click=\"()\" is not a valid AngularJS expression"
                    + " (it also raises the $parse:syntax console error on every load)" : "");
            return Outcome.fail(actual, remarks).withShot(shot);
        }
        return Outcome.pass(actual, "Nothing was sent").withShot(shot);
    }

    private Outcome verifyDischargeSummaryForOutpatient() {
        loadTestPatient();
        String visitRow = emr.visitRows().stream().filter(v -> v.contains(VISIT_NO)).findFirst().orElse("");
        boolean opd = visitRow.matches(".*\\bOPD\\b.*");
        Locator btn = emr.footerButton("Discharge Summary");
        boolean disabled = btn.isDisabled();
        String rule = String.valueOf(btn.getAttribute("ng-disabled"));
        String actual = "Visit " + VISIT_NO + " is " + (opd ? "OPD" : "not OPD (" + visitRow + ")") + "; Discharge Summary is "
                + (disabled ? "disabled" : "ENABLED") + " (ng-disabled=\"" + rule + "\")";
        if (!opd) return Outcome.blocked(actual, "The test visit is not an outpatient visit - pick an OPD visit with -Demr.visit");
        return disabled ? Outcome.pass(actual, "") : Outcome.fail(actual, "");
    }

    private Outcome verifyIcuManagementNavigation() {
        loadTestPatient();
        emr.armAlerts();
        Page tab;
        try {
            tab = context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(15000),
                    () -> emr.footerButton("ICU Management").click());
        } catch (PlaywrightException none) {
            String msgs = String.join(" | ", emr.alerts());
            return Outcome.fail("No ICU Management tab opened within 15 s" + (msgs.isEmpty() ? "" : "; message: " + msgs),
                    "ICU Management reads sessionStorage 'AncillaryConfigApp'; a missing value is reported as a message");
        }
        try {
            try { tab.waitForLoadState(LoadState.DOMCONTENTLOADED, new Page.WaitForLoadStateOptions().setTimeout(30000)); } catch (PlaywrightException ignore) { }
            String url = tab.url();
            String err = reportPageError(tab);
            byte[] shot = screenshotOf(tab);
            String actual = "New tab " + url + (err.isEmpty() ? " loaded" : " shows an error: " + err);
            return (err.isEmpty() && !url.startsWith("about:blank") ? Outcome.pass(actual, "") : Outcome.fail(actual, "")).withShot(shot);
        } finally {
            closeQuietly(tab);
            page.bringToFront();
        }
    }

    private Outcome verifyInAppNavigation(String buttonName, String expectedRoute) {
        loadTestPatient();
        emr.armAlerts();
        emr.footerButton(buttonName).click();
        boolean arrived;
        try {
            page.waitForURL(u -> u.contains(expectedRoute), new Page.WaitForURLOptions().setTimeout(20000));
            arrived = true;
        } catch (PlaywrightException e) {
            arrived = false;
        }
        boolean idle = arrived && emr.waitForHttpIdle(45000);
        List<String> msgs = emr.alerts();
        String errors = msgs.stream().filter(m -> m.matches("(?is).*(error|fail|not found|exception|invalid).*")).reduce((a, b) -> a + " | " + b).orElse("");
        String actual = (arrived ? "Reached " + hashOf(page.url()) + " (" + page.title() + ")" : "Stayed on " + hashOf(page.url()))
                + (arrived && !idle ? "; still loading after 45 s" : "")
                + (msgs.isEmpty() ? "" : "; messages: " + String.join(" | ", msgs));
        return arrived && idle && errors.isEmpty() ? Outcome.pass(actual, "Opened only - nothing created or changed")
                                                   : Outcome.fail(actual, "");
    }

    private Outcome verifyPrint() {
        loadTestPatient();
        Locator btn = emr.footerButton("Print");
        if (btn.isDisabled()) return Outcome.fail("Print is disabled with the test patient loaded", "");
        Page tab;
        try {
            tab = context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(15000), btn::click);
        } catch (PlaywrightException none) {
            return Outcome.fail("Print opened no report tab within 15 s", "");
        }
        try {
            try { tab.waitForLoadState(LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(45000)); } catch (PlaywrightException ignore) { }
            String url = tab.url();
            String err = reportPageError(tab);
            Object printed = safeEval(tab, "() => window.__emrPrintCalled === true");
            byte[] shot = screenshotOf(tab);
            String actual = "Report tab " + url + (err.isEmpty() ? " rendered" : " shows an error: " + err)
                    + (Boolean.TRUE.equals(printed) ? "; the page called window.print() (stubbed - nothing printed)" : "");
            boolean ok = err.isEmpty() && url.contains("EmrReport");
            return (ok ? Outcome.pass(actual, "Preview only - window.print is a no-op in this test") : Outcome.fail(actual, "")).withShot(shot);
        } finally {
            closeQuietly(tab);
            page.bringToFront();
        }
    }

    private void recordApiMonitoringRow() {
        String id = "API-MON";
        String steps = "Network listener on every /api/ call while EMR-04 loaded patient " + MRN;
        String expected = "No HTTP 4xx/5xx; no endpoint called more than " + DUP_LIMIT + " times for one load";
        if (!api.hasData()) {
            step((byte[]) null, id + " - API monitoring during patient load", steps, expected,
                    "Not run: no patient load was captured because EMR-04 did not load the test patient", "BLOCKED");
            return;
        }
        List<String> failed = api.failedSummary();
        List<String> dups = api.duplicatesOver(DUP_LIMIT);
        String actual = api.headline() + (failed.isEmpty() ? "" : " | FAILED: " + String.join("; ", failed))
                + (dups.isEmpty() ? "" : " | REPEATED: " + String.join("; ", dups));
        boolean ok = failed.isEmpty() && dups.isEmpty();
        if (!ok) defectsSeen.add("API: failed or repeated requests during patient load");
        step(id + " - API monitoring during patient load", steps, expected,
                actual + " — REMARKS: recorded, not retried; the full per-endpoint table is at the top of this report", ok ? "PASS" : "FAIL");
    }

    // =============================================================================================================
    //  Login (single account, one Login click)
    // =============================================================================================================

    /*
     * Why this test does not call DevHisBase.login():
     *  - After clicking Login it waits a fixed 2 s, clicks Login a SECOND time if the form is still up, waits 15 s for
     *    a URL ending in /PatientDashboard (the app redirects to its shell, e.g. #/LandingPage, so this wait only times
     *    out) and then gives up ~17.6 s after the first click with "check credentials" and no reason.
     *  - hisdemo (LoginController v2026.11.18) logs in asynchronously: the slow login SP, then three parallel loads
     *    (labels, session config, ancillary config), and only then the redirect. LoginPage already documents this slow
     *    path ("a fixed 15s URL wait ... was too short") and waits up to 60 s; DevHisBase.login() was never updated.
     *  - The server allows one session per account ("User Already Logged in."), and five rejected Login clicks lock the
     *    account, so a blind second click is risky on a shared demo account.
     * This method does the same steps as LoginPage's attempt for ONE account (no fallback accounts), types the password
     * with real key presses (as LoginPage does), clicks Login exactly once, waits on the app's own responses, and
     * returns the login page's actual state when it does not get in. The password is never logged or reported.
     */
    private static final class LoginResult {
        boolean ok; String reason = ""; String landedUrl = ""; String diagnostics = ""; byte[] shot;
    }

    private static final String LOGIN_STATE_JS = "() => {"
            + " const host = document.querySelector('[ng-controller]'); let s = null;"
            + " try { s = (window.angular && host) ? angular.element(host).scope() : null; } catch (e) {}"
            + " const vis = e => !!(e.offsetWidth || e.offsetHeight || e.getClientRects().length);"
            + " const sel = id => { const el = document.getElementById(id); return el && el.selectedIndex >= 0 ? (el.options[el.selectedIndex].text || '').trim() : ''; };"
            + " const msgs = [...document.querySelectorAll('.alert, .toast-message, .help-block, .text-danger')].filter(vis)"
            + "   .map(e => (e.innerText || '').replace(/\\s+/g, ' ').trim()).filter(t => t && !/^passwords are case-sensitive\\.?$/i.test(t));"
            + " return { url: location.href, loginError: s && s.loginError ? String(s.loginError) : '', invalidUser: s && s.InvalidUsername ? String(s.InvalidUsername) : '',"
            + "   locations: s && s.drpLocationList ? s.drpLocationList.length : 0, location: sel('LocationID'),"
            + "   cashCounters: s && s.drpCashCounterList ? s.drpCashCounterList.length : 0, cashCounter: sel('CashCounterID'),"
            + "   loggingIn: !!(s && s.isLoggingIn), userFilled: !!((document.getElementById('Username') || {}).value),"
            + "   passwordFilled: !!((document.getElementById('Password') || {}).value), messages: msgs.join(' | ') }; }";

    private LoginResult loginWithEnvironmentAccount() {
        LoginResult r = new LoginResult();
        int consoleBefore = consoleErrors.size();
        String locLookup = "not called", loginPost = "not sent";
        try {
            apiPhase = "login";
            page.navigate(DASHBOARD);
            Locator userBox = page.locator("#Username, input[placeholder='Login Name']").first();
            Locator passBox = page.locator("#Password, input[type='password']").first();
            try {
                userBox.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE).setTimeout(20000));
            } catch (PlaywrightException noForm) {
                if (!page.url().toLowerCase().contains("/account")) {       // this run's own context is already signed in
                    r.ok = true; r.landedUrl = page.url(); return r;
                }
                r.reason = "the login form did not appear within 20 s";
                return withLoginDiagnostics(r, locLookup, loginPost, consoleBefore);
            }

            userBox.fill(USER);
            passBox.click();
            passBox.fill("");
            passBox.pressSequentially(PASS, new Locator.PressSequentiallyOptions().setDelay(40).setTimeout(120000));

            loginTrace = new LoginTrace(page);
            // Leaving the password box fires getLocations() -> POST /api/Auth/GetLoginLoc (synchronous XHR). It is the
            // server's first verdict on the account: a location list, or InvalidUser / LockedUser.
            try {
                Response loc = page.waitForResponse(res -> res.url().contains("/api/Auth/GetLoginLoc"),
                        new Page.WaitForResponseOptions().setTimeout(60000), passBox::blur);
                locLookup = "HTTP " + loc.status() + accountMarker(loc);
            } catch (PlaywrightException e) {
                locLookup = "no answer within 60 s";
            }
            Map<String, Object> st = loginState();
            if (num(st.get("locations")) == 0) {
                r.reason = "the server returned no Organization/Department for this account at the location lookup (" + locLookup
                        + ") - Login was NOT clicked, so no failed attempt was counted against the account";
                return withLoginDiagnostics(r, locLookup, loginPost, consoleBefore);
            }
            // LoginPage fix: newer builds can wipe the form when the blur-time refresh finishes.
            if (!Boolean.TRUE.equals(st.get("userFilled"))) userBox.fill(USER);
            if (!Boolean.TRUE.equals(st.get("passwordFilled"))) {
                passBox.click();
                passBox.pressSequentially(PASS, new Locator.PressSequentiallyOptions().setDelay(40).setTimeout(120000));
            }

            // ONE Login click; wait for the login POST itself.
            Locator loginButton = page.getByRole(com.microsoft.playwright.options.AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Login"));
            try {
                Response post = page.waitForResponse(res -> res.url().contains("/Account/Login") && "POST".equals(res.request().method()),
                        new Page.WaitForResponseOptions().setTimeout(90000), loginButton::click);
                loginPost = "HTTP " + post.status() + loginAnswer(post);
            } catch (PlaywrightException e) {
                loginPost = "no POST /Account/Login answer within 90 s (Login click not processed)";
            }

            // The app now loads labels, session and ancillary config, then redirects to its shell. Poll the page state
            // (URL left /Account, or the app showed a login error) for up to 90 s.
            long end = System.currentTimeMillis() + 90000;
            while (System.currentTimeMillis() < end) {
                if (!page.url().toLowerCase().contains("/account")) break;
                Map<String, Object> now = loginState();
                if (!String.valueOf(now.getOrDefault("loginError", "")).isEmpty()) break;
                page.waitForTimeout(500);
            }
            if (page.url().toLowerCase().contains("/account")) {
                Map<String, Object> now = loginState();
                String err = String.valueOf(now.getOrDefault("loginError", ""));
                r.reason = err.isEmpty() ? "still on the login page 90 s after Login (no error shown by the app)" : "the app answered \"" + err + "\"";
                return withLoginDiagnostics(r, locLookup, loginPost, consoleBefore);
            }

            // Signed in: wait for the application shell (its side menu) before going anywhere.
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForFunction("() => !!window.angular && [...document.querySelectorAll('a')]"
                            + ".some(a => (a.textContent || '').trim() === 'Telemedicine' && a.offsetParent !== null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(60000));
            r.ok = true;
            r.landedUrl = page.url();
            return r;
        } catch (RuntimeException e) {
            r.reason = "login could not be completed: " + errorText(e);
            return withLoginDiagnostics(r, locLookup, loginPost, consoleBefore);
        } finally {
            apiPhase = "setup";
            if (loginTrace != null) { loginTrace.stop(); loginTrace = null; }
        }
    }

    /** Post-login trace; set while loginWithEnvironmentAccount() runs. */
    private LoginTrace loginTrace;

    /** Branch flags and stored session config that decide whether hisdemo's login redirects (no values that identify the user). */
    private static final String LOGIN_FLOW_JS = "() => { const host = document.querySelector('[ng-controller]'); let s = null;"
            + " try { s = (window.angular && host) ? angular.element(host).scope() : null; } catch (e) {}"
            + " const has = k => { try { return sessionStorage.getItem(k) !== null; } catch (e) { return 'n/a'; } };"
            + " return { isLocationNotRequired: s ? String(s.isLocationnotReqiured) : 'no scope',"
            + "   languageIdSet: !!(s && s.languageId !== undefined && s.languageId !== null),"
            + "   languageOptions: s && s.drpLanguageList ? s.drpLanguageList.length : 0,"
            + "   sessionAncillaryConfigStored: has('AncillaryConfigApp'), sessionConfigAppStored: has('ConfigApp'),"
            + "   appVersion: String(window.__appVer || '') }; }";

    private String loginFlowState() {
        try { return String.valueOf(page.evaluate(LOGIN_FLOW_JS)); } catch (RuntimeException e) { return "unavailable (" + firstLine(e.getMessage()) + ")"; }
    }

    /**
     * Records what happens on the login page from the moment the password box is left: every request (method, path
     * with query, status — never a request body), the SHAPE of the auth / config answers (null, empty, keys — never
     * their values), every console message and page error, and every main-frame navigation.
     */
    private static final class LoginTrace {
        private static final Pattern KEY = Pattern.compile(
                "/api/Auth/|/api/CommonDropdown/FetchAllLabelHeader|/api/UserDetailsModified|/Account/Login", Pattern.CASE_INSENSITIVE);
        private final Page p;
        private final long t0 = System.currentTimeMillis();
        private final List<String> events = new ArrayList<>();
        private final List<Response> keyResponses = new ArrayList<>();
        private final Consumer<Response> onResp;
        private final Consumer<Request> onFail;
        private final Consumer<ConsoleMessage> onCons;
        private final Consumer<String> onErr;
        private final Consumer<com.microsoft.playwright.Frame> onNav;

        LoginTrace(Page p) {
            this.p = p;
            onResp = r -> {
                add(r.request().method() + " " + shortUrl(r.url()) + " -> " + r.status());
                if (KEY.matcher(r.url()).find()) synchronized (keyResponses) { keyResponses.add(r); }
            };
            onFail = q -> add(q.method() + " " + shortUrl(q.url()) + " -> FAILED " + q.failure());
            onCons = m -> add("console." + m.type() + ": " + clip(firstLine(m.text()), 200)
                    + (m.location() == null || m.location().isEmpty() ? "" : " @" + shortUrl(m.location())));
            onErr = e -> add("PAGE ERROR: " + clip(firstLine(e), 240));
            onNav = f -> { if (f == p.mainFrame()) add("NAVIGATED " + f.url()); };
            p.onResponse(onResp);
            p.onRequestFailed(onFail);
            p.onConsoleMessage(onCons);
            p.onPageError(onErr);
            p.onFrameNavigated(onNav);
        }

        private synchronized void add(String e) {
            if (events.size() < 120) events.add("+" + (System.currentTimeMillis() - t0) + "ms " + e);
        }

        void stop() {
            try { p.offResponse(onResp); p.offRequestFailed(onFail); p.offConsoleMessage(onCons); p.offPageError(onErr); p.offFrameNavigated(onNav); }
            catch (RuntimeException ignore) { }
        }

        String summary() {
            List<String> shapes = new ArrayList<>();
            synchronized (keyResponses) {
                for (Response r : keyResponses) shapes.add(r.request().method() + " " + shortUrl(r.url()) + " " + r.status() + " = " + bodyShape(r));
            }
            synchronized (this) {
                return "POST-LOGIN TRACE (from leaving the password box): " + String.join(" | ", events)
                        + "; AUTH/CONFIG RESPONSE SHAPES: " + (shapes.isEmpty() ? "none" : String.join(" | ", shapes));
            }
        }

        /** null / empty / InvalidUser / LockedUser / array of N with keys / object keys - never the values. */
        private static String bodyShape(Response r) {
            String b;
            try { b = r.text(); } catch (RuntimeException e) { return "body unavailable"; }
            if (b == null) return "no body";
            String t = b.trim();
            if (t.isEmpty()) return "empty";
            if (t.equals("null")) return "null";
            if (t.equals("[]")) return "empty array";
            if (t.contains("\"InvalidUser\"")) return "InvalidUser";
            if (t.contains("\"LockedUser\"")) return "LockedUser";
            if (!(t.startsWith("[") || t.startsWith("{"))) return "non-JSON (" + t.length() + " chars)";
            java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
            java.util.regex.Matcher m = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:").matcher(t);
            while (m.find() && keys.size() < 14) keys.add(m.group(1));
            String count = t.startsWith("[") ? "array of ~" + (t.split("\\}\\s*,\\s*\\{", -1).length) : "object";
            return count + " keys " + keys;
        }

        private static String shortUrl(String u) { return clip(u == null ? "" : u.replaceFirst("^https?://[^/]+", ""), 160); }

        private static String clip(String s, int n) { return s == null ? "" : s.length() > n ? s.substring(0, n) + "..." : s; }
    }

    private Map<String, Object> loginState() {
        try {
            Object o = page.evaluate(LOGIN_STATE_JS);
            if (o instanceof Map) {
                @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) o;
                return m;
            }
        } catch (RuntimeException ignore) { /* page navigating */ }
        return new LinkedHashMap<>();
    }

    /** Fill the result with the login page's state (never the password), the server answers and new console errors. */
    private LoginResult withLoginDiagnostics(LoginResult r, String locLookup, String loginPost, int consoleBefore) {
        Map<String, Object> st = loginState();
        StringBuilder d = new StringBuilder();
        d.append("URL ").append(page.url());
        d.append("; app error '").append(st.getOrDefault("loginError", "")).append("'");
        String msgs = String.valueOf(st.getOrDefault("messages", ""));
        if (!msgs.isEmpty()) d.append("; on-screen messages '").append(msgs).append("'");
        if (!String.valueOf(st.getOrDefault("invalidUser", "")).isEmpty()) d.append("; InvalidUsername=").append(st.get("invalidUser"));
        d.append("; Organization/Department options ").append(num(st.get("locations"))).append(" (selected '").append(st.getOrDefault("location", "")).append("')");
        d.append("; cash counters ").append(num(st.get("cashCounters"))).append(" (selected '").append(st.getOrDefault("cashCounter", "")).append("')");
        d.append("; login name box ").append(Boolean.TRUE.equals(st.get("userFilled")) ? "filled" : "EMPTY");
        d.append(", password box ").append(Boolean.TRUE.equals(st.get("passwordFilled")) ? "filled" : "EMPTY");
        d.append(", request in progress ").append(Boolean.TRUE.equals(st.get("loggingIn")));
        d.append("; GetLoginLoc ").append(locLookup).append("; POST /Account/Login ").append(loginPost);
        d.append("; LOGIN FLOW STATE ").append(loginFlowState());
        if (loginTrace != null) d.append("; ").append(loginTrace.summary());
        List<String> newConsole = new ArrayList<>(consoleErrors.keySet());
        if (newConsole.size() > consoleBefore) d.append("; console errors during login: ").append(String.join(" | ", newConsole.subList(consoleBefore, newConsole.size())));
        r.diagnostics = d.toString();
        try { r.shot = page.screenshot(); } catch (RuntimeException ignore) { }
        System.out.println("TelemedicineEMR login: " + r.reason + " | " + r.diagnostics);
        return r;
    }

    /** InvalidUser / LockedUser marker from a GetLoginLoc answer (only the marker; the body is not kept). */
    private static String accountMarker(Response res) {
        try {
            String b = res.text();
            if (b.contains("\"InvalidUser\"")) return " - server marked the account InvalidUser (wrong login name or password)";
            if (b.contains("\"LockedUser\"")) return " - server marked the account LockedUser (locked)";
            if (b.trim().equals("[]") || b.trim().equals("null")) return " - empty answer";
            return " - location list returned";
        } catch (RuntimeException e) { return ""; }
    }

    /** What the login POST answered, classified the way LoginController does (the body is not kept). */
    private static String loginAnswer(Response res) {
        try {
            String b = res.text().trim();
            if (b.isEmpty() || b.equals("null")) return " - no user returned (the app shows 'User Already Logged in.' - an active session for this account)";
            if (b.contains("Expired")) return " - 'Expired' (trial period expired)";
            return " - user details returned (accepted)";
        } catch (RuntimeException e) { return ""; }
    }

    private static int num(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }

    // =============================================================================================================
    //  Case plumbing
    // =============================================================================================================

    @FunctionalInterface private interface CaseBody { Outcome run(); }

    private void runPatientCase(String id, String scenario, String steps, String expected, CaseBody body) {
        if (!patientAvailable) {
            recordCase(id, scenario, steps, expected, Outcome.blocked("Not run: " + patientBlockReason, "Prerequisite: EMR-04 must load the configured test patient"));
            return;
        }
        runCase(id, scenario, steps, expected, body);
    }

    private void runCase(String id, String scenario, String steps, String expected, CaseBody body) {
        Outcome o;
        try {
            o = body.run();
        } catch (Throwable e) {
            byte[] shot = null;
            try { shot = page.screenshot(); } catch (Exception ignore) { }
            o = Outcome.fail("AUTOMATION ERROR (" + e.getClass().getSimpleName() + "): " + errorText(e),
                    "The script stopped before its checks completed - this is not an application result").withShot(shot);
            closeLeftoverDialogs();
        }
        recordCase(id, scenario, steps, expected, o);
    }

    private void recordCase(String id, String scenario, String steps, String expected, Outcome o) {
        switch (o.status) {
            case "PASS" -> casesPass++;
            case "SKIPPED" -> casesSkipped++;
            case "BLOCKED" -> casesBlocked++;
            default -> casesFail++;
        }
        String actual = o.actual + (o.remarks == null || o.remarks.isBlank() ? "" : " — REMARKS: " + o.remarks);
        String name = id + " - " + scenario;
        if (o.shot != null) step(o.shot, name, steps, expected, actual, o.status);
        else if ("BLOCKED".equals(o.status) || "SKIPPED".equals(o.status)) step((byte[]) null, name, steps, expected, actual, o.status);
        else step(page, name, steps, expected, actual, o.status);
    }

    /** Open the screen through the menu and load the test patient by MRN. */
    private void loadTestPatient() {
        emr.openViaMenu(BASE);
        loadPatientOnCurrentScreen();
    }

    private void loadPatientOnCurrentScreen() {
        emr.typeMrn(MRN);
        emr.clickSearch();
        String name = emr.patientHeader().getOrDefault("name", "");
        if (!name.equalsIgnoreCase(PATIENT))
            throw new IllegalStateException("Test patient did not load (header name '" + name + "', messages " + emr.alerts() + ")");
    }

    private void blockAll(String why) {
        for (String[] c : new String[][]{
                {"EMR-01", "Verify EMR screen navigation"}, {"EMR-02", "Verify empty MRN validation"}, {"EMR-03", "Verify invalid MRN"},
                {"EMR-04", "Verify valid MRN search"}, {"EMR-05", "Verify Find Patient popup"}, {"EMR-06", "Verify Enter key search"},
                {"EMR-07", "Verify clinical sections visibility"}, {"EMR-08", "Verify Video Meeting button"}, {"EMR-09", "Verify View Case"},
                {"EMR-10", "Verify Email To Refer Doctor"}, {"EMR-11", "Verify Discharge Summary (outpatient)"},
                {"EMR-12a", "Verify navigation: ICU Management"}, {"EMR-12b", "Verify navigation: OT Room"},
                {"EMR-12c", "Verify navigation: Bills"}, {"EMR-13", "Verify Print"}})
            recordCase(c[0], c[1], "-", "-", Outcome.blocked("Not run", why));
    }

    private void closeLeftoverDialogs() {
        try {
            for (int i = 0; i < 3 && emr != null && emr.openDialogCount() > 0; i++) {
                Locator close = page.locator(".modal.in button[data-dismiss='modal'], .modal.in button.close").first();
                if (close.count() == 0) break;
                close.click(new Locator.ClickOptions().setTimeout(3000));
            }
        } catch (Exception ignore) { }
        try { for (Page p : new ArrayList<>(context().pages())) if (p != page) closeQuietly(p); } catch (Exception ignore) { }
    }

    // =============================================================================================================
    //  Monitoring: network, console, dialogs, write guard
    // =============================================================================================================

    private final class Monitors {
        final Consumer<ConsoleMessage> onConsole = m -> {
            if ("error".equals(m.type())) {
                String where = m.location() == null ? "" : m.location().replaceFirst("^https?://[^/]+", "");
                countConsole(m.text() + (where.isEmpty() ? "" : " @ " + where));
            }
        };
        final Consumer<String> onPageError = err -> countConsole("Uncaught: " + err);
        final Consumer<Dialog> onDialog = d -> {
            dialogsSeen.add(apiPhase + ": " + d.type() + " \"" + firstLine(d.message()) + "\" - dismissed");
            try { d.dismiss(); } catch (Exception ignore) { }
        };
        final Consumer<Response> onResponse = api::onResponse;
        final Consumer<Request> onFinished = api::onFinished;
        final Consumer<Request> onFailed = api::onFailed;
        final Consumer<Page> onNewPage = p -> p.onDialog(onDialog);
        final Consumer<Route> guard = route -> {
            String url = route.request().url();
            if (BLOCK_WRITES && WRITE_CALL.matcher(url).find() && !FETCH_CALL.matcher(url).find()) {
                blockedWrites.add(apiPhase + ": " + route.request().method() + " " + pathOf(url));
                route.abort();
            } else {
                route.resume();
            }
        };

        /** Abort /api/ write calls from here on (armed after login, so sign-in itself is never touched). */
        void armWriteGuard() { context().route("**/api/**", guard); }

        void remove() {
            try { page.offConsoleMessage(onConsole); page.offPageError(onPageError); page.offDialog(onDialog); } catch (Exception ignore) { }
            try { context().offResponse(onResponse); context().offRequestFinished(onFinished); context().offRequestFailed(onFailed); context().offPage(onNewPage); } catch (Exception ignore) { }
            try { context().unroute("**/api/**", guard); } catch (Exception ignore) { }
        }
    }

    private Monitors installMonitors() {
        Monitors m = new Monitors();
        page.onConsoleMessage(m.onConsole);
        page.onPageError(m.onPageError);
        page.onDialog(m.onDialog);
        context().onResponse(m.onResponse);
        context().onRequestFinished(m.onFinished);
        context().onRequestFailed(m.onFailed);
        context().onPage(m.onNewPage);
        // Print safety: the browser runs with --kiosk-printing, so a real window.print() would go straight to the
        // default printer. In this context it only records that it was called.
        context().addInitScript("window.print = function () { window.__emrPrintCalled = true; };");
        return m;
    }

    private synchronized void countConsole(String text) {
        String key = firstLine(text);
        if (key.length() > 300) key = key.substring(0, 300) + "...";
        consoleErrors.merge(key, 1, Integer::sum);
    }

    /** Per-endpoint request statistics for one capture window (EMR-04's patient load). */
    private static final class ApiMonitor {
        private static final class Stat {
            int count, failed; long totalMs; long maxMs; final Map<String, Integer> statuses = new TreeMap<>();
        }
        private final Map<String, Stat> stats = new TreeMap<>();
        private final Map<Request, Integer> statusOf = new IdentityHashMap<>();
        private final List<String> failures = new ArrayList<>();
        private volatile boolean capturing = false;
        private boolean hasData = false;
        private int totalSeen = 0;
        private long startedAt, stoppedAt;

        synchronized void start() { stats.clear(); statusOf.clear(); failures.clear(); capturing = true; hasData = true; startedAt = System.currentTimeMillis(); }
        synchronized void stop() { capturing = false; stoppedAt = System.currentTimeMillis(); }
        synchronized boolean hasData() { return hasData; }
        synchronized int totalSeen() { return totalSeen; }

        synchronized void onResponse(Response r) {
            if (!r.url().contains("/api/")) return;
            totalSeen++;
            if (!capturing) return;
            statusOf.put(r.request(), r.status());
        }

        synchronized void onFinished(Request q) {
            if (!capturing || !q.url().contains("/api/")) return;
            Integer status = statusOf.remove(q);
            add(q, status == null ? "?" : String.valueOf(status), status != null && status >= 400);
        }

        synchronized void onFailed(Request q) {
            if (!q.url().contains("/api/")) return;
            totalSeen++;
            if (!capturing) return;
            String why = q.failure() == null ? "failed" : q.failure();
            add(q, "ERR(" + why + ")", true);
        }

        private void add(Request q, String status, boolean failed) {
            Stat s = stats.computeIfAbsent(pathOf(q.url()), k -> new Stat());
            s.count++;
            s.statuses.merge(status, 1, Integer::sum);
            long ms = 0;
            try { Timing t = q.timing(); if (t != null && t.responseEnd > 0) ms = Math.round(t.responseEnd); } catch (Exception ignore) { }
            s.totalMs += ms; s.maxMs = Math.max(s.maxMs, ms);
            if (failed) { s.failed++; failures.add(pathOf(q.url()) + " -> " + status); }
        }

        synchronized String headline() {
            int calls = stats.values().stream().mapToInt(x -> x.count).sum();
            int failed = stats.values().stream().mapToInt(x -> x.failed).sum();
            return "Patient load: " + calls + " API calls to " + stats.size() + " endpoints in " + (stoppedAt - startedAt) / 1000.0 + " s, "
                    + failed + " failed; " + focus("FetchEMRHistory") + ", " + focus("CommonDropdown") + ", "
                    + focus("InvestigationFetch") + ", " + focus("FetchEMRLocalExamination");
        }

        private String focus(String part) {
            int n = 0; Map<String, Integer> st = new TreeMap<>();
            for (Map.Entry<String, Stat> e : stats.entrySet()) if (e.getKey().contains(part)) {
                n += e.getValue().count; e.getValue().statuses.forEach((k, v) -> st.merge(k, v, Integer::sum)); }
            return part + " x" + n + (st.isEmpty() ? "" : " " + st);
        }

        synchronized List<String> failedSummary() { return new ArrayList<>(failures); }

        synchronized List<String> duplicatesOver(int limit) {
            List<String> out = new ArrayList<>();
            stats.forEach((k, v) -> { if (v.count > limit) out.add(k + " x" + v.count); });
            return out;
        }

        synchronized String tableHtml(int dupLimit) {
            StringBuilder sb = new StringBuilder("<table><thead><tr><th>Endpoint</th><th>Calls</th><th>HTTP status</th><th>Avg ms</th>"
                    + "<th>Max ms</th><th>Failed</th><th>Repeated</th></tr></thead><tbody>");
            stats.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
                    .forEach(e -> {
                        Stat s = e.getValue();
                        String st = s.statuses.entrySet().stream().map(x -> x.getKey() + " x" + x.getValue()).reduce((a, b) -> a + ", " + b).orElse("");
                        sb.append("<tr><td>").append(esc(e.getKey())).append("</td><td>").append(s.count).append("</td><td>").append(esc(st))
                          .append("</td><td>").append(s.count == 0 ? 0 : s.totalMs / s.count).append("</td><td>").append(s.maxMs)
                          .append("</td><td>").append(s.failed > 0 ? "<b>" + s.failed + "</b>" : "0")
                          .append("</td><td>").append(s.count > dupLimit ? "<b>yes (&gt;" + dupLimit + ")</b>" : s.count > 1 ? "yes" : "no")
                          .append("</td></tr>");
                    });
            return sb.append("</tbody></table>").toString();
        }
    }

    // =============================================================================================================
    //  Report
    // =============================================================================================================

    private void finishReport() {
        int total = casesPass + casesFail + casesSkipped + casesBlocked;
        addSummary("Test cases (EMR-xx)", total + " total / " + casesPass + " passed / " + casesFail + " failed / "
                + casesSkipped + " skipped / " + casesBlocked + " blocked");
        addSummary("Defects / observations reproduced", defectsSeen.isEmpty() ? "none" : String.join("; ", defectsSeen));
        addSummary("Console errors", consoleErrors.values().stream().mapToInt(Integer::intValue).sum() + " (" + consoleErrors.size() + " distinct)");
        addSummary("Write calls blocked by the safety net", blockedWrites.isEmpty() ? "none" : String.join("; ", blockedWrites));
        addSummary("Browser dialogs", dialogsSeen.isEmpty() ? "none" : String.join("; ", dialogsSeen));

        StringBuilder note = new StringBuilder();
        note.append("<b>Read-only run.</b> No Save / OK / Send was pressed; dialogs were closed with Close / Cancel; window.print was stubbed")
            .append(BLOCK_WRITES ? "; /api/ write calls were aborted by a safety net." : ".")
            .append(" Each row is one test case; the Actual column ends with <i>REMARKS</i> (defect details / observations).");
        note.append("<h2>API monitoring - patient load (EMR-04)</h2>");
        note.append(api.hasData() ? "<p>" + esc(api.headline()) + "</p>" + api.tableHtml(DUP_LIMIT) : "<p>Not captured (EMR-04 did not run).</p>");
        note.append("<h2>Console errors</h2>");
        if (consoleErrors.isEmpty()) note.append("<p>None recorded.</p>");
        else {
            note.append("<table><thead><tr><th>Count</th><th>Message (first line)</th></tr></thead><tbody>");
            consoleErrors.forEach((k, v) -> note.append("<tr><td>").append(v).append("</td><td>").append(esc(k)).append("</td></tr>"));
            note.append("</tbody></table>");
        }
        meta("Telemedicine - EMR", "Telemedicine > EMR", note.toString());
    }

    // =============================================================================================================
    //  Small helpers
    // =============================================================================================================

    private static boolean credentialsProvided() {
        return notBlank(System.getProperty("devhis.user", System.getenv("DEVHIS_USER")))
            && notBlank(System.getProperty("devhis.pass", System.getenv("DEVHIS_PASS")));
    }

    private static boolean notBlank(String s) { return s != null && !s.isBlank(); }

    private static boolean isVisible(Locator l) {
        try { return l.isVisible(); } catch (PlaywrightException e) { return false; }
    }

    private static String hashOf(String url) { int i = url.indexOf('#'); return i < 0 ? "" : url.substring(i); }

    private static String pathOf(String url) {
        String p = url.replaceFirst("^https?://[^/]+", "");
        int q = p.indexOf('?');
        return q < 0 ? p : p.substring(0, q);
    }

    private static String firstLine(String s) { return s == null ? "" : s.split("\\R", 2)[0].trim(); }

    /**
     * The useful text of an exception. Playwright for Java formats its errors as {@code Error {\n  message='...'\n  name=...},
     * so the first line is only "Error {" - take the message= part (and the call log line after it, if any) instead.
     */
    private static String errorText(Throwable e) {
        String m = e == null ? null : e.getMessage();
        if (m == null || m.isBlank()) return e == null ? "" : e.getClass().getSimpleName();
        java.util.regex.Matcher mm = Pattern.compile("message='([^\\n]*)").matcher(m);
        String text = mm.find() ? mm.group(1).trim() : firstLine(m);
        if (text.endsWith("'")) text = text.substring(0, text.length() - 1);
        int callLog = m.indexOf("Call log:");
        if (callLog >= 0) {
            String[] lines = m.substring(callLog).split("\\R");
            for (int i = 1; i < lines.length && i <= 3; i++) {
                String l = lines[i].replaceAll("^[\\s-]+", "").trim();
                if (l.equals("'") || l.startsWith("name=") || l.startsWith("stack=")) break;
                if (!l.isEmpty()) text += " | " + l;
            }
        }
        return text.length() > 600 ? text.substring(0, 600) + "..." : text;
    }

    private static String str(Object o) { return o == null ? "" : String.valueOf(o).trim(); }

    private static String describeBlock(Object o) {
        if (!(o instanceof Map)) return "n/a";
        Map<?, ?> m = (Map<?, ?>) o;
        return Boolean.TRUE.equals(m.get("present")) ? (Boolean.TRUE.equals(m.get("visible")) ? "present, visible" : "present, hidden") : "not present";
    }

    private static Object safeEval(Page p, String js) { try { return p.evaluate(js); } catch (Exception e) { return null; } }

    private static byte[] screenshotOf(Page p) {
        try { return p.screenshot(new Page.ScreenshotOptions().setTimeout(15000)); } catch (Exception e) { return null; }
    }

    private static void closeQuietly(Page p) { try { if (p != null && !p.isClosed()) p.close(); } catch (Exception ignore) { } }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** One case's result. */
    private static final class Outcome {
        final String status, actual, remarks;
        byte[] shot;
        private Outcome(String status, String actual, String remarks) { this.status = status; this.actual = actual; this.remarks = remarks; }
        static Outcome pass(String a, String r)    { return new Outcome("PASS", a, r); }
        static Outcome fail(String a, String r)    { return new Outcome("FAIL", a, r); }
        static Outcome skipped(String a, String r) { return new Outcome("SKIPPED", a, r); }
        static Outcome blocked(String a, String r) { return new Outcome("BLOCKED", a, r); }
        Outcome withShot(byte[] png) { this.shot = png; return this; }
    }
}
