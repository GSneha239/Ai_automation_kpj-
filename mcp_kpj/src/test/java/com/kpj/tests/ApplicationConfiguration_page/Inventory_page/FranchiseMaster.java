package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named FranchiseMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Inventory &gt; <b>Franchise Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>Inventory</b> → <b>Franchise Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the <b>Location</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Remark</b>, <b>Address</b>, <b>Contact No</b> and <b>E-mail</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin one with
 * {@code -Dcode=}.</p>
 *
 * <p>Each of the five values carries its own field name, so a value that lands in a neighbouring box is
 * visible in the report and in the saved row rather than passing unnoticed.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL franchise in the target environment.</p>
 */
public class FranchiseMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public FranchiseMaster() { super("ApplicationConfiguration_Inventory_FranchiseMaster"); }

    public static void main(String[] args) {
        FranchiseMaster t = new FranchiseMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Franchise Master", "Application Configuration > Inventory > Franchise Master",
                "&#9888; Creates a REAL franchise: Add, select the Location, enter Code, Remark, Address, "
                        + "Contact No and E-mail, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "FR" + stamp);
        String remark = System.getProperty("remark", "Auto franchise " + stamp);
        String address = System.getProperty("address", "12 Jalan Auto Address " + stamp);
        String contact = System.getProperty("contact", "03" + stamp + "1");
        String email = System.getProperty("email", "auto" + stamp + "@example.com");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.FranchiseMaster fm =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.FranchiseMaster(page);

        // 1) Navigate
        boolean rendered = fm.navigateViaMenu(BASE);
        if (!rendered) addSummary("Inventory submenu offered", fm.lastMenu);
        step(page, "Open Franchise Master screen",
                "Click Application Configuration -> Inventory -> Franchise Master",
                "The Franchise Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (fm.lastRoute.isEmpty() ? "" : " (menu route " + fm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the Inventory submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", fm.describeControls());

        // 2) Add
        boolean formOpen = fm.clickAdd();
        addSummary("Form controls", fm.describeControls());
        step(page, "Click Add", "Click Add to open the franchise form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Location
        String location = fm.selectLocation();
        boolean locOk = fm.locationSelected();
        step(page, "Select location", "Select the Location",
                "A location is selected",
                (locOk
                    ? "PASSES because the dropdown holds the chosen location: " + location
                    : "FAILS because no location could be selected: " + location),
                locOk ? "PASS" : "FAIL");

        // 4) Code, Remark, Address, Contact No, E-mail
        String entry = fm.enterDetails(code, remark, address, contact, email);
        String[] expected = { "Code", code, "Remark", remark, "Address", address,
                              "ContactNo", contact, "Email", email };
        boolean entered = fm.allEntered(expected);
        String missing = fm.missing(expected);
        step(page, "Enter code, remark, address, contact no and e-mail",
                "Enter all five values, each in its own field",
                "All five are entered",
                (entered
                    ? "PASSES because every one of the five boxes read back its OWN value — checked "
                      + "individually, since the contact and e-mail fields sit together and are easily "
                      + "confused for one another: " + entry
                    : "FAILS — these fields did not receive their value: " + missing + ". Detail: " + entry),
                entered ? "PASS" : "FAIL");

        // 5) Submit -> toast
        String toast = fm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.Inventory_page.FranchiseMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = fm.codeInList(code, remark);
        addSummary("List check", fm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + fm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the franchise IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + fm.lastListCheck)
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

        step(page, "Verify the franchise was created", "Look for " + code + " in the list",
                "The new franchise is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the list: "
                      + fm.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + fm.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Location", fm.lastLocation);
        addSummary("Values entered", entry);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
