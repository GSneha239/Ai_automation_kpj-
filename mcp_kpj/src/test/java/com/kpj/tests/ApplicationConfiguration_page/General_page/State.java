package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named State — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>State</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>State</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>State</b>.</li>
 *   <li>Select the <b>Country</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL state in the target environment — which then appears in the
 * State dropdown of City/District, District and Postal.</p>
 */
public class State extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public State() { super("ApplicationConfiguration_State"); }

    public static void main(String[] args) {
        State t = new State();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("State", "Application Configuration > General > State",
                "&#9888; Creates a REAL state: Add, enter Code and State, select the Country, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "ST" + stamp);
        String state = System.getProperty("state", "Auto State " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.State st =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.State(page);

        // 1) Navigate
        boolean rendered = st.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", st.lastMenu);
        step(page, "Open State screen",
                "Click Application Configuration -> General -> State",
                "The State screen is shown",
                rendered ? "Opened " + page.url()
                           + (st.lastRoute.isEmpty() ? "" : " (menu route " + st.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", st.describeControls());

        // 2) Add
        boolean formOpen = st.clickAdd();
        addSummary("Form controls", st.describeControls());
        step(page, "Click Add", "Click Add to open the state form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + State
        String entry = st.enterDetails(code, state);
        boolean entered = st.detailsEntered(code, state);
        step(page, "Enter code and state", "Enter the Code " + code + " and the State " + state,
                "Both are entered",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) Country
        String country = st.selectCountry();
        boolean countryOk = st.countrySelected();
        step(page, "Select country", "Select the Country",
                "A country is selected",
                (countryOk
                    ? "PASSES because the dropdown holds the chosen country: " + country
                    : "FAILS because no country could be selected: " + country),
                countryOk ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = st.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.State.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = st.codeInList(code, state);
        addSummary("List check", st.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + st.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the state IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + st.lastListCheck)
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

        step(page, "Verify the state was created", "Look for " + code + " in the list",
                "The new state is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its state name — was found in the "
                      + "list: " + st.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + st.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / State", code + " / " + state);
        addSummary("Country", st.lastCountry);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
