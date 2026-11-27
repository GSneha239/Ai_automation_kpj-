package com.kpj.tests;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * Shared plumbing for READ-ONLY screen test classes: one JUnit {@code @Test} method per scenario, one HTML report per class,
 * written by the existing {@link DevHisBase} reporter. Used by the Discount Manager/CR and MRD ICD Entry suites.
 *
 * <p>It adds no framework behaviour; it only drives {@link DevHisBase}'s public API. {@code @BeforeAll} opens the browser with
 * the framework's {@link DevHisBase#launchOptions()} / {@link DevHisBase#contextOptions()} and hands it to {@link #attach},
 * installs the {@link ReadOnlyScreen} write guard BEFORE login, logs in with the existing {@link LoginPage} and proves the
 * guard. Each {@code @Test} records its report row with {@code step(...)} (the framework screenshots every step, and adds its
 * failure detail to a FAIL). {@code @AfterAll} calls {@link #run()}, which - the browser being attached - only writes
 * {@code test-output/reports/<testId>.html}; then the browser is closed.</p>
 *
 * <p>Report rows carry descriptive scenario names only: no test-case IDs, and the report's step-number column is switched off
 * ({@code -Ddevhis.report.stepNumbers=off}, the default for these suites; pass {@code =on} to get the numbers back).</p>
 *
 * <p>Result mapping: PASS; FAIL (the JUnit test fails too; the reason says APPLICATION DEFECT or AUTOMATION / LOCATOR
 * FAILURE); SKIPPED (the JUnit test is aborted: test data not on this environment, credentials missing, or guard not proven).</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class ReadOnlyScenarioTestBase<P extends ReadOnlyScreen> extends DevHisBase {

    protected static final String APP_DEFECT = "FAILURE REASON - APPLICATION DEFECT: ";
    protected static final String AUTOMATION = "FAILURE REASON - AUTOMATION / LOCATOR FAILURE: ";

    protected P screen;
    private String notReady = "";            // "" = logged in and guard proven
    private Playwright ownPw;
    private Browser ownBrowser;
    private int pass, fail, skipped;

    protected ReadOnlyScenarioTestBase(String testId) { super(testId); }

    protected abstract P createScreen(Page page);
    protected abstract String reportTitle();
    protected abstract String reportModule();
    protected abstract String reportNote();
    protected abstract String dataSummary();
    /** Summary line describing what this suite never does. */
    protected String readOnlyNote() { return "READ-ONLY - write calls aborted in the browser; no save / approve / submit action is performed"; }

    @BeforeAll
    void openBrowserAndLogIn() {
        // descriptive scenario names only - hide the report's step numbers unless the run asks for them
        System.setProperty("devhis.report.stepNumbers", System.getProperty("devhis.report.stepNumbers", "off"));
        meta(reportTitle(), reportModule(), reportNote());
        String user = cfgValue("devhis.user", "DEVHIS_USER"), pwd = cfgValue("devhis.pass", "DEVHIS_PASS");
        ownPw = Playwright.create();
        ownBrowser = ownPw.chromium().launch(DevHisBase.launchOptions());
        BrowserContext ctx = ownBrowser.newContext(DevHisBase.contextOptions());
        Page p = ctx.newPage();
        DevHisBase.ensureLargeWindow(p);
        attach(ownPw, ownBrowser, ctx, p);
        if (user.isEmpty() || pwd.isEmpty()) {
            notReady = "credentials missing - set DEVHIS_USER and DEVHIS_PASS (no built-in account is used)";
            step("Credentials", "Read DEVHIS_USER / DEVHIS_PASS", "Credentials supplied by the environment", "Not set - nothing was run", "SKIPPED");
            return;
        }
        try {
            screen = createScreen(page);
            screen.installGuards();                        // BEFORE login: write requests are aborted from here on
            new LoginPage(page).login(BASE, user, pwd);
            step("Login", user + " login at " + BASE + " (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");
            boolean guard = screen.guardVerified();
            step("Read-only guard", "Probe a fake IUD request before touching the screen", "The browser aborts write requests",
                    guard ? "Write guard active (probe aborted)" : "Write guard NOT proven - every scenario is SKIPPED", guard ? "PASS" : "FAIL");
            if (!guard) notReady = "read-only write guard could not be proven, so the screen was not touched";
        } catch (Throwable e) {
            notReady = "login / guard set-up failed: " + firstLine(e);
            step("Login", "Log in at " + BASE, "Authenticated", AUTOMATION + notReady, "FAIL");
        }
    }

    @AfterAll
    void writeReportAndClose() {
        addSummary("Mode", readOnlyNote());
        addSummary("Environment / data", BASE + " - " + dataSummary());
        addSummary("Scenarios", (pass + fail + skipped) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped);
        if (screen != null) {
            addSummary("Blocked write calls", screen.blockedWrites.isEmpty() ? "none - no write request was attempted" : String.join(" ; ", screen.blockedWrites));
            addSummary("Failed API responses", screen.apiFailures.isEmpty() ? "none" : String.join(" ; ", screen.apiFailures));
            addSummary("Console errors", screen.consoleErrors.isEmpty() ? "none" : String.join(" ; ", screen.consoleErrors));
        }
        try {
            run();                                         // attached browser: start() is a no-op, body() is empty, the report is written
        } finally {
            try { if (ownBrowser != null) ownBrowser.close(); } catch (Exception ignore) { }
            try { if (ownPw != null) ownPw.close(); } catch (Exception ignore) { }
        }
    }

    /** Scenarios run as {@code @Test} methods; {@link #run()} is only used to write the report. */
    @Override
    protected void body() { }

    // ---- per-scenario plumbing -------------------------------------------------------

    protected interface Check { String[] run() throws Exception; }

    /**
     * Run one scenario and record one report row named by its descriptive {@code name} (no sequential test IDs). FAIL also fails the JUnit test and SKIPPED aborts
     * it, so Maven's result matches the report. An exception is an automation / locator failure, never an application defect.
     */
    protected void scenario(String name, String steps, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        if (!notReady.isEmpty()) {
            status = "SKIPPED";
            actual = "SKIPPED - " + notReady;
        } else {
            try {
                String[] r = c.run();
                status = r[0];
                actual = r[1];
            } catch (Throwable e) {
                status = "FAIL";
                actual = AUTOMATION + "exception - " + firstLine(e);
            }
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, name, "Steps: " + steps, expected, actual, status);
        if ("PASS".equals(status)) pass++;
        else if ("SKIPPED".equals(status)) skipped++;
        else fail++;
        Assumptions.assumeFalse("SKIPPED".equals(status), actual);
        if (!"PASS".equals(status)) Assertions.fail(name + ": " + actual);
    }

    protected static String[] passed(String actual) { return new String[]{"PASS", actual}; }
    protected static String[] defect(String actual) { return new String[]{"FAIL", APP_DEFECT + actual}; }
    protected static String[] automationFailure(String actual) { return new String[]{"FAIL", AUTOMATION + actual}; }
    protected static String[] dataMissing(String actual) { return new String[]{"SKIPPED", "SKIPPED - TEST DATA NOT AVAILABLE: " + actual}; }

    // ---- helpers --------------------------------------------------------------------------

    protected static String cfgValue(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return v == null ? "" : v.trim();
    }

    protected static double amount(String s) {
        if (s == null || s.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** Equal as numbers when both parse, otherwise case-insensitive text. */
    protected static boolean sameValue(String actual, String expected) {
        double a = amount(actual), e = amount(expected);
        if (!Double.isNaN(a) && !Double.isNaN(e)) return Math.abs(a - e) < 0.005;
        return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    /** The application's discount rounding: nearest 0.05. */
    protected static double roundToNearest005(double v) { return Math.round(v * 20) / 20.0; }

    protected static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static String firstLine(Throwable e) { return String.valueOf(e.getMessage()).split("\\R")[0]; }
}
