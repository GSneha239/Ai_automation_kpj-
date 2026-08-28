package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named HolidayMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Holiday Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Holiday Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b> and the <b>Date</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. The date is a future
 * one derived from the same stamp, so repeated runs do not collide on a holiday that already exists.
 * Pin either with {@code -Dcode=} / {@code -Ddate=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL holiday in the target environment.</p>
 */
public class HolidayMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public HolidayMaster() { super("ApplicationConfiguration_HolidayMaster"); }

    public static void main(String[] args) {
        HolidayMaster t = new HolidayMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Holiday Master", "Application Configuration > General > Holiday Master",
                "&#9888; Creates a REAL holiday: Add, enter Code and Date, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int n = (int) Math.abs(System.nanoTime() % 100000);
        String stamp = String.format("%05d", n);
        String code = System.getProperty("code", "HM" + stamp);
        // A future date, varied per run: a holiday master keys on the date, so reusing one invites a
        // duplicate refusal that has nothing to do with the flow being tested.
        java.time.LocalDate d = java.time.LocalDate.now()
                .plusYears(1).withDayOfMonth(1 + (n % 28)).withMonth(1 + (n % 12));
        String date = System.getProperty("date",
                d.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.HolidayMaster hm =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.HolidayMaster(page);

        // 1) Navigate
        boolean rendered = hm.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", hm.lastMenu);
        step(page, "Open Holiday Master screen",
                "Click Application Configuration -> General -> Holiday Master",
                "The Holiday Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (hm.lastRoute.isEmpty() ? "" : " (menu route " + hm.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", hm.describeControls());

        // 2) Add
        boolean formOpen = hm.clickAdd();
        addSummary("Form controls", hm.describeControls());
        step(page, "Click Add", "Click Add to open the holiday form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Date
        String entry = hm.enterCode(code);
        String dateEntry = hm.enterDate(date);
        boolean codeOk = hm.codeEntered(code);
        boolean dateOk = hm.dateEntered();
        step(page, "Enter code and date", "Enter the Code " + code + " and the Date " + date,
                "Both are entered",
                ((codeOk && dateOk)
                    ? "PASSES because both boxes read their value back — the date is confirmed on the "
                      + "control and the model, not merely typed, since these boxes are usually readonly "
                      + "and ignore a plain write: Code=" + entry + " | Date=" + dateEntry
                    : "FAILS because a value did not land in its own field: Code=" + entry
                      + " | Date=" + dateEntry),
                (codeOk && dateOk) ? "PASS" : "FAIL");

        // 4) Submit -> toast
        String toast = hm.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.HolidayMaster.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = hm.codeInList(code);
        addSummary("List check", hm.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + hm.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the holiday IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + hm.lastListCheck)
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

        step(page, "Verify the holiday was created", "Look for " + code + " in the list",
                "The new holiday is listed",
                (inList
                    ? "PASSES because the saved record was found in the list: " + hm.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + hm.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Date", code + " / " + date);
        addSummary("Date field", hm.lastDate);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
