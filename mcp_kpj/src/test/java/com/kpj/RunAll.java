package com.kpj;

import java.util.List;

import com.kpj.core.DevHisBase;
import com.kpj.tests.Op_Page.Appointment.MultipleAppointmentBookingTest;
import com.kpj.tests.Op_Page.OutPatientQueueManagementTest;
import com.kpj.tests.Op_Page.RegistrationTest;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

/**
 * Runs ALL tests sequentially. Browser mode is a RUNTIME TOGGLE:
 *
 *   -Dbrowser=shared    (default) one browser window shared by all tests (login persists)
 *   -Dbrowser=isolated  each test launches + closes its own browser
 *
 * Other runtime flags (apply to both modes):
 *   -Dheadless=true|false   (default false)
 *   -Dslowmo=<ms>           (default 120)
 *   -Dlogin=auto|manual     (RunAll forces auto so it never blocks)
 *
 * Examples:
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.RunAll
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.RunAll -Dbrowser=isolated
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.RunAll -Dheadless=true
 *
 * Run a SINGLE test (its own browser) via its main() or JUnit:
 *   mvn -q compile exec:java -Dexec.mainClass=com.kpj.tests.RegistrationTest
 *   mvn test -Dtest=RegistrationTest
 */
public class RunAll {
    public static void main(String[] args) {
        System.setProperty("login", "auto"); // never block on manual login in a batch run
        boolean shared = !"isolated".equalsIgnoreCase(System.getProperty("browser", "shared"));

        List<DevHisBase> tests = List.of(
                new MultipleAppointmentBookingTest(),
                new RegistrationTest(),
                new OutPatientQueueManagementTest());

        System.out.println("RunAll mode: " + (shared ? "SHARED browser (one window)" : "ISOLATED (one browser per test)"));
        int passed = shared ? runShared(tests) : runIsolated(tests);
        // Each test writes its own timestamped report under test-output/reports/ (no consolidated report).
        System.out.println("\n==== RunAll complete: " + passed + "/" + tests.size() + " completed ====");
    }

    /** One browser window for all tests; login persists; popup tabs tidied between tests. */
    static int runShared(List<DevHisBase> tests) {
        int passed = 0;
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(DevHisBase.launchOptions());
            BrowserContext ctx = browser.newContext(DevHisBase.contextOptions());
            Page page = ctx.newPage();
            for (DevHisBase t : tests) {
                System.out.println("\n==== Running " + t.testId + " (shared browser) ====");
                t.attach(pw, browser, ctx, page);
                try { t.run(); passed++; }
                catch (Exception e) { System.err.println("!! " + t.testId + " FAILED: " + e.getMessage()); }
                finally { t.stop(); }
                for (Page pg : ctx.pages()) if (pg != page) { try { pg.close(); } catch (Exception ignored) {} }
            }
            browser.close();
        }
        return passed;
    }

    /** Each test launches and closes its own browser (full isolation). */
    static int runIsolated(List<DevHisBase> tests) {
        int passed = 0;
        for (DevHisBase t : tests) {
            System.out.println("\n==== Running " + t.testId + " (own browser) ====");
            try { t.run(); passed++; }
            catch (Exception e) { System.err.println("!! " + t.testId + " FAILED: " + e.getMessage()); }
            finally { t.stop(); }
        }
        return passed;
    }
}
