package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named MaritalStatus — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Marital Status</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Marital Status</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Remark</b>.</li>
 *   <li>Enter the <b>MIMS GUID</b> and <b>MIMS Description</b>.</li>
 *   <li>Select the <b>MIMS Type</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL marital status in the target environment.</p>
 */
public class MaritalStatus extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public MaritalStatus() { super("ApplicationConfiguration_General_MaritalStatus"); }

    public static void main(String[] args) {
        MaritalStatus t = new MaritalStatus();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - Marital Status", "Application Configuration > General > Marital Status",
                "&#9888; Creates a REAL marital status: enter Code and Remark, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "MST" + stamp);
        String remark = System.getProperty("remark", "Auto marital status " + stamp);
        // Distinct from the remark on purpose: the form carries two description boxes, and identical
        // values would hide one being written into the other.
        String mimsGuid = System.getProperty("mimsguid", "GUID-" + stamp);
        String mimsDesc = System.getProperty("mimsdesc", "Auto MIMS description " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.MaritalStatus tor =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.MaritalStatus(page);

        // 1) Navigate
        boolean rendered = tor.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", tor.lastMenu);
        step(page, "Open Marital Status screen",
                "Click Application Configuration -> General -> Marital Status",
                "The Marital Status screen is shown",
                rendered ? "Opened " + page.url()
                           + (tor.lastRoute.isEmpty() ? "" : " (menu route " + tor.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", tor.describeControls());
        addSummary("Add", tor.openFormIfNeeded());
        String formControls = tor.describeControls();
        addSummary("Form controls", formControls);

        // The form that actually opened may only carry Code (+ maybe Store) — no Remark, no MIMS
        // GUID/Description, no MIMS Type. Rather than mechanically fail once per missing field, stop here
        // and report it as the one real problem it is: the app opens the wrong (an incomplete) page.
        // Nothing is entered anywhere on a form already known to be wrong.
        if (!tor.hasExpectedFields()) {
            step(page, "Form has the expected fields", "Check for Remark / MIMS GUID / MIMS Description before entering anything",
                    "The form carries Code, Remark, MIMS GUID, MIMS Description and MIMS Type",
                    "FAILS — opens the WRONG (an incomplete) page: Remark/MIMS fields are missing. "
                        + "Form controls: " + formControls,
                    "FAIL");
            addSummary("Result", "FAILED — wrong/incomplete page (Remark/MIMS fields missing)");
            return;
        }

        // 2) Code + Remark
        String entry = tor.enterDetails(code, remark);
        boolean entered = tor.detailsEntered(code, remark);
        step(page, "Enter code and remark",
                "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 3) MIMS GUID + MIMS Description
        String mims = tor.enterMims(mimsGuid, mimsDesc);
        boolean mimsOk = tor.mimsEntered(mimsGuid, mimsDesc);
        step(page, "Enter MIMS GUID and MIMS Description",
                "Enter the MIMS GUID " + mimsGuid + " and the MIMS Description",
                "Both are entered",
                (mimsOk
                    ? "PASSES because each MIMS box read back its own value, and neither took the "
                      + "record's own Remark — the form carries two description boxes: " + mims
                    : "FAILS because a MIMS value did not land in its own field: " + mims),
                mimsOk ? "PASS" : "FAIL");

        // 4) MIMS Type
        String mimsType = tor.selectMimsType();
        boolean typeOk = tor.mimsTypeSelected();
        step(page, "Select MIMS Type", "Select the MIMS Type",
                "A MIMS type is selected",
                (typeOk
                    ? "PASSES because the MIMS Type dropdown holds the choice: " + mimsType
                    : "FAILS because no MIMS type could be selected: " + mimsType),
                typeOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = tor.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.MaritalStatus.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = tor.codeInList(code, remark);
        addSummary("List check", tor.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + tor.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + tor.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        step(page, "Verify the marital status was created", "Look for " + code + " in the list",
                "The new marital status is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the "
                      + "list: " + tor.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + tor.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("MIMS GUID / Description", tor.lastMims);
        addSummary("MIMS Type", tor.lastMimsType);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
