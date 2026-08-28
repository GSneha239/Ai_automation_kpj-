package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AreaTown — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Area/Town</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Area/Town</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the <b>City/District</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Area/Town</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>Habits carried over from the sibling screens in this module: the route comes from the menu link
 * rather than the label (Complaint Type is routed {@code #/ComplaintListType}), the City/District list is
 * waited for rather than sampled once, and the saved record is confirmed in the grid — a toast alone is
 * not proof, since screens here can answer "Message Not Found." on a successful write.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL area/town in the target environment.</p>
 */
public class AreaTown extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AreaTown() { super("ApplicationConfiguration_AreaTown"); }

    public static void main(String[] args) {
        AreaTown t = new AreaTown();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Area/Town", "Application Configuration > General > Area/Town",
                "&#9888; Creates a REAL area/town: Add, select the City/District, enter the Code and "
                        + "Area/Town, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "AT" + stamp);
        String area = System.getProperty("area", "Auto Area " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.AreaTown at =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.AreaTown(page);

        // 1) Navigate
        boolean rendered = at.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", at.lastMenu);
        step(page, "Open Area/Town screen",
                "Click Application Configuration -> General -> Area/Town",
                "The Area/Town screen is shown",
                rendered ? "Opened " + page.url()
                           + (at.lastRoute.isEmpty() ? "" : " (menu route " + at.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", at.describeControls());

        // 2) Add
        boolean formOpen = at.clickAdd();
        addSummary("Form controls", at.describeControls());
        step(page, "Click Add", "Click Add to open the area/town form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) City/District
        String city = at.selectCityDistrict();
        step(page, "Select city/district", "Select the City/District",
                "A city/district is selected", "City/District = " + city,
                at.citySelected() ? "PASS" : "FAIL");

        // 4) Code + Area/Town
        String entry = at.enterCodeAndArea(code, area);
        step(page, "Enter code and area/town",
                "Enter the Code " + code + " and the Area/Town " + area,
                "Both are entered", entry, at.detailsEntered(code) ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = at.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.AreaTown.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = at.codeInList(code);
        addSummary("List check", at.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + at.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the area/town IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + at.lastListCheck)
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        step(page, "Verify the area/town was created", "Filter the list for " + code,
                "The new area/town is listed", at.lastListCheck, inList ? "PASS" : "FAIL");

        addSummary("City/District", at.lastCity);
        addSummary("Code / Area", code + " / " + area);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
