package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Postal — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Postal</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Postal</b>.</li>
 *   <li>Select the <b>Country</b>, <b>State</b> and <b>City/District</b>.</li>
 *   <li>Enter the <b>Postal</b> code.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The postal code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dpostal=}.</p>
 *
 * <p>Country → State → City <b>cascade</b>: {@code Postal.CountryID} fires {@code GetSelectedState()} and
 * {@code Postal.StateId} fires {@code GetCity2()}. Countries are tried until one yields the whole chain,
 * since several carry no state at all.</p>
 *
 * <p>Two traps, both of which made a working screen look broken. {@code Postal.CityId} is <b>pre-loaded
 * with every city in the database</b> (448 here) before a country is chosen, so "the list is not empty"
 * proves nothing — the run waits for the count to CHANGE, which is what {@code GetCity2()} does. And the
 * field is labelled "City/District" while its model is {@code CityId}: looking it up by "district", as the
 * sibling City/District screen does, finds nothing on this form.</p>
 *
 * <p>The requested steps do not mention Add; the flow clicks it only if the form is not already on screen
 * and says which it found.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL postal record in the target environment.</p>
 */
public class Postal extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Postal() { super("ApplicationConfiguration_General_Postal"); }

    public static void main(String[] args) {
        Postal t = new Postal();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - Postal", "Application Configuration > General > Postal",
                "&#9888; Creates a REAL postal record: select the Country, State and City/District, enter "
                        + "the Postal code, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String postal = System.getProperty("postal", stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.Postal po =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.Postal(page);

        // 1) Navigate
        boolean rendered = po.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", po.lastMenu);
        step(page, "Open Postal screen",
                "Click Application Configuration -> General -> Postal",
                "The Postal screen is shown",
                rendered ? "Opened " + page.url()
                           + (po.lastRoute.isEmpty() ? "" : " (menu route " + po.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", po.describeControls());

        // The steps do not mention Add: click it only if the entry form is not already showing.
        boolean formReady = po.formOpen();
        if (!formReady) {
            po.clickAdd();
            formReady = po.formOpen();
            addSummary("Add", formReady ? "The form needed Add; it is now open." : "Add did not open a form.");
        } else {
            addSummary("Add", "Not needed — the route opens the entry form directly.");
        }
        addSummary("Form controls", po.describeControls());

        // 2) Country -> State -> City/District (cascading)
        String cascade = po.selectCountryStateCity();
        step(page, "Select country, state and city/district",
                "Select the Country, then the State, then the City/District (each fills the next)",
                "All three are selected",
                po.cascadeSelected()
                    ? "PASSES because a country, state and city were all selected: " + cascade
                    : po.noCityAnywhere()
                    ? "FAILS — no country/state pair offered a City/District. Country and State select "
                      + "correctly, and each state change is waited on until GetCity2() has refilled the "
                      + "list, so an empty result here is a master-data gap for the states tried rather "
                      + "than a list read too early. Detail: " + cascade
                    : cascade,
                po.cascadeSelected() ? "PASS" : "FAIL");

        // 3) Postal
        String entry = po.enterPostal(postal);
        boolean postalOk = po.postalEntered(postal);
        step(page, "Enter postal", "Enter the Postal code " + postal,
                "The postal code is entered",
                (postalOk
                    ? "PASSES because the field accepted the value and read it back: " + entry
                    : "FAILS because the value did not land in the field: " + entry),
                postalOk ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = po.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.Postal.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = po.codeInList(postal);
        addSummary("List check", po.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + po.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the postal record IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + po.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step. The save itself goes through, but the user is
        // shown "undefined Saved Successfully." — the record identifier is missing from the message
        // template — and a confirmation that names no record is not a valid confirmation.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        boolean confirmed = ok && !malformed;
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (malformed
                    ? "FAILS because the message is MALFORMED: it reads \"" + toast + "\". The screen "
                      + "cannot name what it saved, so it tells the user \"undefined\" was saved. The "
                      + "record itself is written — " + (inList ? "it IS in the list" : "but it is NOT in "
                      + "the list") + " — so this is a defect in the message template (the record "
                      + "identifier is never substituted in), not a failed save. Detail: " + actual
                    : (ok ? "PASSES because the screen answered with a success message: "
                          : "FAILS — ") + actual),
                confirmed ? "PASS" : "FAIL");

        step(page, "Verify the postal record was created", "Look for " + postal + " in the list",
                "The new postal record is listed",
                (inList
                    ? "PASSES because the saved record — the postal code AND the city it was filed under "
                      + "— was found in the list: " + po.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + po.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Country / State / City", po.lastCity);
        addSummary("Postal", postal);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
