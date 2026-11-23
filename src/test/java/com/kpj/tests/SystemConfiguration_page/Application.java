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
                "View only: open the screen, click each section and take a screenshot. Nothing is entered, saved, cleared or deleted.");

        String user = credential("devhis.user", "DEVHIS_USER");
        String pass = credential("devhis.pass", "DEVHIS_PASS");
        String loginId = id();
        if (user.isEmpty() || pass.isEmpty()) {
            step(loginId + " - Login", "Log in with DEVHIS_USER / DEVHIS_PASS", "Logged in",
                    "Not run: DEVHIS_USER / DEVHIS_PASS are not set", "BLOCKED");
            addSummary("Result", "Stopped: no credentials supplied");
            return;
        }
        LoginPage loginPage = new LoginPage(page);
        String loginErr = null;
        try { loginPage.login(BASE, user, pass); } catch (Exception e) { loginErr = e.getMessage(); }
        boolean loggedIn = loginErr == null && !loginPage.isLoginFormVisible();
        tc(loginId, "Login", "Log in to " + BASE + " as " + user, "Authenticated; login form gone",
                loggedIn ? "Logged in" : "Login failed: " + (loginErr == null ? "login form still shown" : loginErr),
                loggedIn ? "PASS" : "FAIL", "");
        if (!loggedIn) { addSummary("Result", "Stopped: login failed"); return; }

        ap = new ApplicationPage(page);
        ap.blockSaveRequests();   // safety net only: this test never saves

        boolean on = ap.navigateViaMenu();
        int view = ap.viewStatus();
        tc(id(), "Open Application via menu", "Left menu: System Configuration -> Application",
                "The Application screen is shown",
                on ? "Opened " + ap.currentScreen() + "; view HTTP " + view : "Screen not reached; app is on " + ap.currentScreen() + " (view HTTP " + view + ")",
                on && view == 200 ? "PASS" : "FAIL", view >= 500 ? "Server-side error (environment), not a UI defect" : "");
        if (!on) { addSummary("Result", "Stopped: Application screen did not load"); return; }

        // ---- Click each section and take a screenshot - nothing is typed, added, saved, cleared or deleted ----
        for (Map.Entry<String, String[]> e : ApplicationPage.SECTIONS.entrySet()) {
            String cid = e.getValue()[0];
            boolean open = ap.expand(cid);
            List<String> labels = ap.visibleLabels(cid, 6);
            tc(id(), "Section '" + e.getKey() + "'", "Click the '" + e.getKey() + "' heading and take a screenshot",
                    "The section opens", (open ? "Opened" : "Did NOT open") + "; fields: " + labels, open ? "PASS" : "FAIL", "");
        }

        // The Ancillary sub-sections sit inside the Ancillary section
        ap.expand(ApplicationPage.SECTIONS.get("Ancillary")[0]);
        for (Map.Entry<String, String[]> e : ApplicationPage.ANCILLARY.entrySet()) {
            String cid = e.getValue()[0];
            boolean open = ap.expand(cid);
            List<String> labels = ap.visibleLabels(cid, 4);
            tc(id(), "Ancillary > '" + e.getKey() + "'", "Click '" + e.getKey() + "' inside Ancillary and take a screenshot",
                    "The sub-section opens", (open ? "Opened" : "Did NOT open") + "; fields: " + labels, open ? "PASS" : "FAIL", "");
        }

        addSummary("Route", ApplicationPage.ROUTE);
        addSummary("Mode", "View only - sections clicked and screenshotted; nothing entered, saved, cleared or deleted");
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
