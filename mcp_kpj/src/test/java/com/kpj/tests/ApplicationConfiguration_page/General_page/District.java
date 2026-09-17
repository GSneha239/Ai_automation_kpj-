package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named District — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>District</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>District</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the <b>Country</b> and <b>State</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>District</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>This is the master behind the District dropdown on <b>City/District</b>, which is currently empty for
 * every country — so a successful run here is what makes that screen's District selectable.</p>
 *
 * <p>Country → State cascade, so State is waited for after Country. The menu match is anchored to
 * "District" exactly: "City/District" sits beside it and a loose match takes whichever comes first.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL district in the target environment.</p>
 */
public class District extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public District() { super("ApplicationConfiguration_General_District"); }

    public static void main(String[] args) {
        District t = new District();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - District", "Application Configuration > General > District",
                "&#9888; Creates a REAL district: Add, select the Country and State, enter the Code and "
                        + "District, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "DS" + stamp);
        String district = System.getProperty("district", "Auto District " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.District ds =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.District(page);

        // 1) Navigate
        boolean rendered = ds.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", ds.lastMenu);
        step(page, "Open District screen",
                "Click Application Configuration -> General -> District",
                "The District screen is shown",
                rendered ? "Opened " + page.url()
                           + (ds.lastRoute.isEmpty() ? "" : " (menu route " + ds.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ds.describeControls());

        // 2) Add
        boolean formOpen = ds.clickAdd();
        addSummary("Form controls", ds.describeControls());
        step(page, "Click Add", "Click Add to open the district form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Country -> State (cascading)
        String cascade = ds.selectCountryState();
        step(page, "Select country and state",
                "Select the Country, then the State it fills",
                "Both are selected",
                ds.noStateAnywhere()
                    ? "FAILED — no State can be selected. The State list holds only its placeholder for "
                      + "every country tried, so no states are configured in this environment. Detail: "
                      + cascade
                    : cascade,
                ds.cascadeSelected() ? "PASS" : "FAIL");

        // 4) Code + District
        String entry = ds.enterCodeAndDistrict(code, district);
        step(page, "Enter code and district",
                "Enter the Code " + code + " and the District " + district,
                "Both are entered", entry, ds.detailsEntered(code) ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = ds.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.District.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = ds.codeInList(code);
        addSummary("List check", ds.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ds.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the district IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + ds.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the district was created", "Filter the list for " + code,
                "The new district is listed", ds.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Country / State", ds.lastCity);
        addSummary("Code / District", code + " / " + district);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
