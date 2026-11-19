package com.kpj.tests.SystemConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.SystemConfiguration_page.ApplicationPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * System Access Configuration &gt; <b>Application</b> ({@code #/ApplicationConfiguration}) - read-only checks
 * plus Save validations. Report: {@code test-output/reports/SystemConfiguration_Application.html}.
 *
 * <p><b>Live hospital configuration - nothing may change.</b></p>
 * <ul>
 *   <li>Every write call under {@code /api/ApplicationConfiguration/} (including {@code IUD}, the save) is
 *       aborted in the browser for the whole run; the read calls ({@code Fetch...}, {@code GetPhoto}) pass.</li>
 *   <li>A successful Save and a logo upload are never performed: they are recorded as MANUAL.</li>
 *   <li>Clear was inspected first: {@code ClearForm()} only empties the dropdown option lists in the browser and
 *       sends no request. It is clicked once, Save is NOT clicked after it, and the screen is reloaded.</li>
 *   <li>Every stored value of the form is captured after load and again after the final reload; they must match.</li>
 * </ul>
 *
 * <p>Credentials come only from DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass). Without them
 * nothing is run and every case is BLOCKED - the framework's built-in default account is not used.</p>
 *
 * <p>Report columns follow the framework (Step Name / Description / Expected / Actual / Status / Screenshot):
 * Step Name = "Test Case ID - Scenario", Description = steps performed, Actual ends with "Remarks: ...".</p>
 */
public class Application extends DevHisBase {

    public Application() { super("SystemConfiguration_Application"); }

    public static void main(String[] args) {
        Application t = new Application();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private ApplicationPage ap;
    private int seq = 0;
    /** Planned cases not yet recorded - reported BLOCKED if the run has to stop early. */
    private final List<String[]> pending = new ArrayList<>();

    private String id() { return String.format("APP-%02d", ++seq); }

    /** One report row: "APP-nn - scenario" with a screenshot of the page. */
    private void tc(String id, String scenario, String steps, String expected, String actual, String status, String remarks) {
        String a = actual + (remarks == null || remarks.isBlank() ? "" : " | Remarks: " + remarks);
        step(page, id + " - " + scenario, steps, expected, a, status);
    }

    private void tcPng(byte[] png, String id, String scenario, String steps, String expected, String actual, String status, String remarks) {
        String a = actual + (remarks == null || remarks.isBlank() ? "" : " | Remarks: " + remarks);
        if (png != null && png.length > 0) step(png, id + " - " + scenario, steps, expected, a, status);
        else step(page, id + " - " + scenario, steps, expected, a, status);
    }

    private static String credential(String prop, String env) {
        String v = System.getProperty(prop);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return v == null ? "" : v.trim();
    }

    @Override
    protected void body() {
        meta("System Configuration - Application", "System Access Configuration > Application",
                "Read-only checks of the Application configuration form plus 10 Save validations. Every write call "
                        + "to /api/ApplicationConfiguration/ (incl. IUD) is aborted in the browser, so no configuration is changed.");

        // ---- APP-01 Login --------------------------------------------------------
        String user = credential("devhis.user", "DEVHIS_USER");
        String pass = credential("devhis.pass", "DEVHIS_PASS");
        String loginId = id();
        if (user.isEmpty() || pass.isEmpty()) {
            step(loginId + " - Login", "Log in with DEVHIS_USER / DEVHIS_PASS", "Logged in",
                    "Not run: DEVHIS_USER / DEVHIS_PASS are not set | Remarks: the framework default account is deliberately not used",
                    "BLOCKED");
            blockRest("No credentials supplied");
            return;
        }
        LoginPage loginPage = new LoginPage(page);
        String loginErr = null;
        try { loginPage.login(BASE, user, pass); } catch (Exception e) { loginErr = e.getMessage(); }
        boolean loggedIn = loginErr == null && !loginPage.isLoginFormVisible();
        tc(loginId, "Login", "Log in to " + BASE + " as " + user, "Authenticated; login form gone",
                loggedIn ? "Logged in" : "Login failed: " + (loginErr == null ? "login form still shown" : loginErr),
                loggedIn ? "PASS" : "FAIL", "");
        if (!loggedIn) { blockRest("Login failed"); return; }

        ap = new ApplicationPage(page);
        ap.blockSaveRequests();

        // ---- APP-02 Menu navigation + page load + HTTP ---------------------------
        boolean on = ap.navigateViaMenu();
        int view = ap.viewStatus();
        String landed = ap.currentScreen();
        String openActual = on ? "Opened " + landed + "; view GET /Billing/Load/ApplicationConfiguration -> HTTP " + view
                : (view >= 400 ? "Screen did not load: GET /Billing/Load/ApplicationConfiguration -> HTTP " + view + "; app is on " + landed
                               : "Screen not reached; app is on " + landed + " (view HTTP " + view + ")");
        tc(id(), "Open Application via menu", "Left menu: System Configuration -> Application",
                "Route #/ApplicationConfiguration; view HTTP 200; form loads with stored values",
                openActual, on && view == 200 ? "PASS" : "FAIL",
                view >= 500 ? "Server-side error (environment), not a UI defect" : "");
        if (!on) { blockRest("Application screen did not load"); return; }

        String before = ap.configSnapshot();
        addDiagnostic("Config fields captured", String.valueOf(before.length()) + " chars");

        // ---- APP-03 Location* / APP-04 Hospital Logo ----------------------------
        String loc = ap.locationInfo();
        boolean locOk = ap.locationSelected() && loc.contains("label=Location*");
        tc(id(), "Location* mandatory field", "Read the Location label, its value and options",
                "Label shows '*' (mandatory) and the login hospital is pre-selected", loc, locOk ? "PASS" : "FAIL", "");

        boolean logo = ap.logoUploadPresent();
        tc(id(), "Hospital Logo upload control", "Check that the logo file input #PhotoData exists (no file chosen)",
                "A file chooser for the hospital logo is shown",
                logo ? "File input #PhotoData present" : "File input #PhotoData not found", logo ? "PASS" : "FAIL",
                "Presence only - no file is uploaded");

        // ---- APP-05 headings ----------------------------------------------------
        List<String> titles = ap.sectionTitles();
        List<String> missing = new ArrayList<>();
        for (String s : ApplicationPage.SECTIONS.keySet()) if (!titles.contains(s)) missing.add(s);
        for (String s : ApplicationPage.ANCILLARY.keySet()) if (!titles.contains(s)) missing.add(s);
        tc(id(), "All 13 section headings", "Read every accordion heading",
                "General, Nursing Station, Investigation, Ancillary, HL7/FTP + Ancillary: Blood Bank, Equipment, CSSD, Linen, DMS, MRD, Ambulance, Vital",
                titles.size() + " found: " + titles + (missing.isEmpty() ? "" : " | MISSING: " + missing),
                missing.isEmpty() ? "PASS" : "FAIL", "");

        // ---- APP-06..10 top-level sections (expand + previous collapses) ---------
        String prevId = null, prevName = null;
        for (Map.Entry<String, String[]> e : ApplicationPage.SECTIONS.entrySet()) {
            String cid = e.getValue()[0], firstLabel = e.getValue()[1];
            boolean open = ap.expand(cid);
            List<String> labels = ap.visibleLabels(cid, 6);
            boolean hasLabel = labels.stream().anyMatch(l -> l.startsWith(firstLabel));
            boolean prevClosed = prevId == null || !ap.isExpanded(prevId);
            tc(id(), "Section '" + e.getKey() + "' expands", "Click the '" + e.getKey() + "' heading",
                    "Section opens and shows '" + firstLabel + "'" + (prevName == null ? "" : "; '" + prevName + "' collapses"),
                    (open ? "Opened" : "Did NOT open") + "; fields: " + labels
                            + (prevName == null ? "" : (prevClosed ? "; '" + prevName + "' collapsed" : "; '" + prevName + "' is STILL open")),
                    open && hasLabel && prevClosed ? "PASS" : "FAIL", "");
            prevId = cid; prevName = e.getKey();
        }

        // ---- APP-11..18 Ancillary sub-sections ------------------------------------
        ap.expand(ApplicationPage.SECTIONS.get("Ancillary")[0]);
        for (Map.Entry<String, String[]> e : ApplicationPage.ANCILLARY.entrySet()) {
            String cid = e.getValue()[0], firstLabel = e.getValue()[1];
            boolean open = ap.expand(cid);
            List<String> labels = ap.visibleLabels(cid, 4);
            boolean ok = open && labels.stream().anyMatch(l -> l.startsWith(firstLabel));
            tc(id(), "Ancillary > '" + e.getKey() + "' expands", "Open Ancillary, click '" + e.getKey() + "'",
                    "Sub-section opens and shows '" + firstLabel + "'",
                    (open ? "Opened" : "Did NOT open") + "; fields: " + labels, ok ? "PASS" : "FAIL", "");
        }

        // ---- APP-19..24 numeric limits -------------------------------------------
        ap.expand(ApplicationPage.SECTIONS.get("General")[0]);
        range("Alternative Drug Acknowledgment Time Limit (Hours)", ApplicationPage.ALT_DRUG_ACK_HRS, "min=1 max=720");
        range("Pregnancy Review Gestational Age (Weeks)", ApplicationPage.PREGNANCY_WEEKS, "min=37 max=44");
        range("Progress Notes Approval Time Limit (Hours)", ApplicationPage.PROGRESS_NOTES_HRS, "min=1 max=999");
        range("OP Time Limit for Order Completion (Hours)", ApplicationPage.OP_ORDER_HRS, "min=1 max=999");
        range("IP Time Limit for Order Completion (Hours)", ApplicationPage.IP_ORDER_HRS, "min=1 max=999");
        range("EOL Validity Period (Days)", ApplicationPage.EOL_DAYS, "min=0 max=9999");

        // ---- APP-25 buttons --------------------------------------------------------
        boolean saveOk = ap.saveVisibleAndEnabled(), clearOk = ap.clearVisible();
        tc(id(), "Save and Clear buttons", "Look at the bottom of the form",
                "Save (enabled) and Clear are shown", "Save visible+enabled=" + saveOk + ", Clear visible=" + clearOk,
                saveOk && clearOk ? "PASS" : "FAIL", "");

        // ---- APP-26..35 Save validations (writes aborted) --------------------------
        validation("Refilling Days blank", ApplicationPage.REFILLING_DAYS, "", "Refilling Days is Mandatory");
        validation("Alternative Drug Ack. Hours = 0", ApplicationPage.ALT_DRUG_ACK_HRS, "0", "Please enter a value between 1 and 720 hours");
        validation("Alternative Drug Ack. Hours = 721", ApplicationPage.ALT_DRUG_ACK_HRS, "721", "Please enter a value between 1 and 720 hours");
        validation("Pregnancy Review Weeks = 36", ApplicationPage.PREGNANCY_WEEKS, "36", "Pregnancy Review Gestational Age (Weeks) must be a whole number between 37 and 44");
        validation("Pregnancy Review Weeks = 45", ApplicationPage.PREGNANCY_WEEKS, "45", "Pregnancy Review Gestational Age (Weeks) must be a whole number between 37 and 44");
        validation("Progress Notes Approval Hours = 0", ApplicationPage.PROGRESS_NOTES_HRS, "0", "Progress Notes Approval Time Limit (Hours) is mandatory and must be a whole number greater than 0");
        validation("OP Order Completion Hours blank", ApplicationPage.OP_ORDER_HRS, "", "OP Time Limit for Order Completion (Hours) is mandatory and must be a whole number greater than 0");
        validation("IP Order Completion Hours = 0", ApplicationPage.IP_ORDER_HRS, "0", "IP Time Limit for Order Completion (Hours) is mandatory and must be a whole number greater than 0");
        validation("EOL Validity Days = 1.5", ApplicationPage.EOL_DAYS, "1.5", "EOL Validity Period (Days) must be a whole number of days");
        validation("DNR/DIL Validity Days = 1.5", ApplicationPage.DNR_DIL_DAYS, "1.5", "DNR/DIL Validity Period (Days) must be a whole number of days");

        // ---- APP-36 Clear (client-side only; Save NOT clicked afterwards) ------------
        int writesBefore = ap.apiWrites();
        String afterClear = ap.clickClear();
        page.waitForTimeout(1500);
        int clearWrites = ap.apiWrites() - writesBefore;
        tc(id(), "Clear sends no request", "Click Clear; watch the network (Save is not clicked afterwards)",
                "No write request is sent (ClearForm() only resets dropdown option lists in the browser)",
                clearWrites + " write request(s) during Clear; afterwards " + afterClear,
                clearWrites == 0 ? "PASS" : "FAIL",
                "Observation: Clear does not reset field values or Location - it only empties the dropdown option lists. No spec available to judge this; raise with BA if Clear is meant to reset the form");

        // ---- APP-37 Reload + no configuration changed -------------------------------
        boolean back = ap.reload();
        String after = back ? ap.configSnapshot() : "";
        boolean same = back && !before.isEmpty() && before.equals(after);
        tc(id(), "Stored configuration unchanged", "Reload #/ApplicationConfiguration; compare every stored field with the values captured after the first load",
                "All stored values identical to the start of the run",
                !back ? "Screen did not reload" : (same ? "Identical (" + before.length() + " chars of field values compared)"
                        : "DIFFERENT - values after reload do not match the start of the run"),
                same ? "PASS" : "FAIL", same ? "" : "Investigate immediately: before=" + abbreviate(before) + " after=" + abbreviate(after));

        // ---- APP-38 Save API blocked -------------------------------------------------
        int attempts = ap.saveAttempts(), reached = ap.iudResponses();
        tc(id(), "Save API blocked; no save reached the server", "Count requests to /api/ApplicationConfiguration/ write endpoints (aborted) and responses from IUD",
                "0 responses from IUD (any attempt was aborted in the browser)",
                "Write attempts aborted: " + attempts + "; IUD responses received: " + reached,
                reached == 0 ? "PASS" : "FAIL",
                attempts == 0 ? "Every validation stopped Save before it tried to send" : attempts + " save attempt(s) were made by the screen and aborted - see the validation rows marked FAIL");

        // ---- APP-39 / APP-40 not automated -------------------------------------------
        step(id() + " - Successful Save", "Change a value and Save", "Success toast; value kept after reload",
                "Not executed | Remarks: Save overwrites the live hospital-wide configuration. Test by hand on an agreed field, then restore it", "MANUAL");
        step(id() + " - Hospital Logo upload", "Choose an image and Save", "New logo shown after reload",
                "Not executed | Remarks: would replace the live hospital logo", "MANUAL");

        addSummary("Route", ApplicationPage.ROUTE);
        addSummary("Save/write attempts aborted", String.valueOf(attempts));
        addSummary("IUD responses (must be 0)", String.valueOf(reached));
        addSummary("Configuration unchanged", same ? "Yes - all stored values identical before and after" : "NOT CONFIRMED");
    }

    private void range(String label, String ngModel, String expected) {
        String actual = ap.numberRange(ngModel);
        tc(id(), label + " limits", "Read type/min/max of the field", "type=number " + expected, actual,
                actual.equals("type=number " + expected) ? "PASS" : "FAIL", "");
    }

    /** Invalid value -> Save -> expect a warning toast with the message and no save attempt. */
    private void validation(String name, String ngModel, String value, String expectedText) {
        int before = ap.saveAttempts();
        String toast = ap.saveWithValue(ngModel, value);
        boolean sent = ap.saveAttempts() > before;
        boolean kind = toast.startsWith("warning|");
        boolean text = toast.toLowerCase().contains(expectedText.toLowerCase());
        String status = kind && text && !sent ? "PASS" : "FAIL";
        String remarks = sent ? "APPLICATION DEFECT: validation missing - Save tried to send (request aborted by the test)"
                : (toast.isEmpty() ? "No toast captured" : (!text ? "Different message shown" : ""));
        tcPng(ap.toastPng, id(), "Validation: " + name,
                "Enter '" + value + "' in " + ngModel + ", click Save (value restored afterwards)",
                "Warning toast '" + expectedText + "!' and no save request",
                (toast.isEmpty() ? "No toast appeared" : toast.replace("|", " toast: ")), status, remarks);
    }

    /** Record every remaining planned case as BLOCKED (never PASS) when the run cannot continue. */
    private void blockRest(String why) {
        String[] rest = {"Open Application via menu", "Location* mandatory field", "Hospital Logo upload control",
                "All 13 section headings", "Section 'General' expands", "Section 'Nursing Station' expands",
                "Section 'Investigation' expands", "Section 'Ancillary' expands", "Section 'HL7/FTP' expands",
                "Ancillary > 'Blood Bank' expands", "Ancillary > 'Equipment' expands", "Ancillary > 'CSSD' expands",
                "Ancillary > 'Linen' expands", "Ancillary > 'DMS' expands", "Ancillary > 'MRD' expands",
                "Ancillary > 'Ambulance' expands", "Ancillary > 'Vital' expands",
                "Alternative Drug Acknowledgment Time Limit (Hours) limits", "Pregnancy Review Gestational Age (Weeks) limits",
                "Progress Notes Approval Time Limit (Hours) limits", "OP Time Limit for Order Completion (Hours) limits",
                "IP Time Limit for Order Completion (Hours) limits", "EOL Validity Period (Days) limits", "Save and Clear buttons",
                "Validation: Refilling Days blank", "Validation: Alternative Drug Ack. Hours = 0",
                "Validation: Alternative Drug Ack. Hours = 721", "Validation: Pregnancy Review Weeks = 36",
                "Validation: Pregnancy Review Weeks = 45", "Validation: Progress Notes Approval Hours = 0",
                "Validation: OP Order Completion Hours blank", "Validation: IP Order Completion Hours = 0",
                "Validation: EOL Validity Days = 1.5", "Validation: DNR/DIL Validity Days = 1.5",
                "Clear sends no request", "Stored configuration unchanged", "Save API blocked; no save reached the server"};
        // skip the ones already recorded (seq counts recorded cases; APP-01 is Login)
        for (int i = seq - 1; i < rest.length; i++) {
            step(id() + " - " + rest[i], "-", "-", "Not executed | Remarks: " + why, "BLOCKED");
        }
        step(id() + " - Successful Save", "-", "-", "Not executed | Remarks: live configuration - manual only", "MANUAL");
        step(id() + " - Hospital Logo upload", "-", "-", "Not executed | Remarks: live logo - manual only", "MANUAL");
        addSummary("Result", "Stopped early: " + why);
    }

    private static String abbreviate(String s) { return s == null ? "" : (s.length() > 300 ? s.substring(0, 300) + "..." : s); }
}
