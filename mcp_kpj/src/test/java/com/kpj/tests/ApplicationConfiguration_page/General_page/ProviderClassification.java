package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ProviderClassification — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Provider Classification</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Provider Classification</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Provider Classification</b>.</li>
 *   <li>Select the <b>Service</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The flow ends at the toast: the separate "verify the record was created" step was dropped on
 * request. The list is still checked behind the scenes and reported under "List check", so the toast can
 * still be told apart from a silent or malformed save.</p>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL provider classification in the target environment.</p>
 */
public class ProviderClassification extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public ProviderClassification() { super("ApplicationConfiguration_ProviderClassification"); }

    public static void main(String[] args) {
        ProviderClassification t = new ProviderClassification();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Provider Classification", "Application Configuration > General > Provider Classification",
                "&#9888; Creates a REAL provider classification: Add, enter Code and Provider "
                        + "Classification, select the Service, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "PC" + stamp);
        String name = System.getProperty("name", "Auto Provider Class " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.ProviderClassification pc =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.ProviderClassification(page);

        // 1) Navigate
        boolean rendered = pc.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", pc.lastMenu);
        step(page, "Open Provider Classification screen",
                "Click Application Configuration -> General -> Provider Classification",
                "The Provider Classification screen is shown",
                rendered ? "Opened " + page.url()
                           + (pc.lastRoute.isEmpty() ? "" : " (menu route " + pc.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", pc.describeControls());

        // 2) Add
        boolean formOpen = pc.clickAdd();
        addSummary("Form controls", pc.describeControls());
        step(page, "Click Add", "Click Add to open the provider classification form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Provider Classification
        String entry = pc.enterDetails(code, name);
        boolean entered = pc.detailsEntered(code, name);
        step(page, "Enter code and provider classification",
                "Enter the Code " + code + " and the Provider Classification " + name,
                "Both are entered",
                (entered
                    ? "PASSES because both boxes accepted the value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Service
        String service = pc.selectService();
        boolean serviceOk = pc.serviceSelected();
        step(page, "Select service", "Select the Service",
                "A service is selected",
                (serviceOk
                    ? "PASSES because the dropdown holds the chosen service: " + service
                    : "FAILS because no service could be selected: " + service),
                serviceOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = pc.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.ProviderClassification.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = pc.codeInList(code, name);
        addSummary("List check", pc.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + pc.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + pc.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: "
                    : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // The list check is no longer a step of its own — the flow ends at the success toast. It still
        // runs, and its outcome is reported above and in the summary below, so the evidence that the
        // record really was written is not lost.
        addSummary("Code / Provider Classification", code + " / " + name);
        addSummary("Service", pc.lastService);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
