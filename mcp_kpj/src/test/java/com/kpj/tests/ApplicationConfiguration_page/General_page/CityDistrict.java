package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CityDistrict — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>City/District</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>City/District</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Country</b>, <b>State</b> and <b>District</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>City/District</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>Country → State → District <b>cascade</b>, so each is waited for after the one above rather than read
 * at a fixed moment. Text fields are addressed by exact ng-model: on the sibling Area/Town screen every
 * model began "area.", so a keyword match for the screen's own name found the Code box and overwrote it.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL city/district in the target environment.</p>
 */
public class CityDistrict extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public CityDistrict() { super("ApplicationConfiguration_CityDistrict"); }

    public static void main(String[] args) {
        CityDistrict t = new CityDistrict();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("City/District", "Application Configuration > General > City/District",
                "&#9888; Creates a REAL city/district: Add, select Country, State and District, enter the "
                        + "Code and City/District, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "CD" + stamp);
        String city = System.getProperty("city", "Auto City " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.CityDistrict cd =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.CityDistrict(page);

        // 1) Navigate
        boolean rendered = cd.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", cd.lastMenu);
        step(page, "Open City/District screen",
                "Click Application Configuration -> General -> City/District",
                "The City/District screen is shown",
                rendered ? "Opened " + page.url()
                           + (cd.lastRoute.isEmpty() ? "" : " (menu route " + cd.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", cd.describeControls());

        // 2) Add
        boolean formOpen = cd.clickAdd();
        addSummary("Form controls", cd.describeControls());
        step(page, "Click Add", "Click Add to open the city/district form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Country -> State -> District (cascading). District turns out to be unfillable in this
        //    environment — see below — so the step FAILS with the reason rather than being softened: the
        //    requested action cannot be performed at all.
        String cascade = cd.selectCountryStateDistrict();
        step(page, "Select country, state and district",
                "Select the Country, then the State, then the District (each fills the next)",
                "All three are selected",
                cd.noDistrictAnywhere()
                    ? "FAILED — the District cannot be selected. Country and State fill correctly, but the "
                      + "District list holds only its placeholder for EVERY country tried (Malaysia, "
                      + "Singapore, India, Indonesia and then the list in order — 16 in all). No districts "
                      + "are configured in this environment. Note the record still saves without one, so "
                      + "District is not mandatory. Detail: " + cascade
                    : cascade,
                cd.cascadeSelected() ? "PASS" : "FAIL");

        // 4) Code + City/District
        String entry = cd.enterCodeAndCity(code, city);
        step(page, "Enter code and city/district",
                "Enter the Code " + code + " and the City/District " + city,
                "Both are entered", entry, cd.detailsEntered(code) ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = cd.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.CityDistrict.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = cd.codeInList(code);
        addSummary("List check", cd.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + cd.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the city/district IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + cd.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the city/district was created", "Filter the list for " + code,
                "The new city/district is listed", cd.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("Country / State / District", cd.lastCity);
        addSummary("Code / City", code + " / " + city);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
