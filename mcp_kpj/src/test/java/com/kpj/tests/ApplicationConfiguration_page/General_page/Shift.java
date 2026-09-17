package com.kpj.tests.ApplicationConfiguration_page.General_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Shift — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; General &gt; <b>Shift</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>General</b> → <b>Shift</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>Code</b>, <b>Remark</b>, <b>From Time</b> and <b>To Time</b>.</li>
 *   <li>Select the <b>Cell Colour</b>.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The Code is generated per run, since configuration screens reject a duplicate. Pin the values with
 * {@code -Dcode=}, {@code -Dfrom=}, {@code -Dto=} and {@code -Dcolour=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL shift in the target environment.</p>
 */
public class Shift extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Shift() { super("ApplicationConfiguration_General_Shift"); }

    public static void main(String[] args) {
        Shift t = new Shift();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - General - Shift", "Application Configuration > General > Shift",
                "&#9888; Creates a REAL shift: Add, enter Code, Remark, From Time and To Time, select the "
                        + "Cell Colour, Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String code = System.getProperty("code", "SH" + stamp);
        String remark = System.getProperty("remark", "Auto shift " + stamp);
        // The picker is a 12-hour one (the box reads ": : PM"), so the meridian is part of the value.
        String from = System.getProperty("from", "08:00 AM");
        String to = System.getProperty("to", "04:00 PM");
        String colour = System.getProperty("colour", "#3b7dd8");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.General_page.Shift sh =
                new com.kpj.pages.ApplicationConfiguration_page.General_page.Shift(page);

        // 1) Navigate
        boolean rendered = sh.navigateViaMenu(BASE);
        if (!rendered) addSummary("General submenu offered", sh.lastMenu);
        step(page, "Open Shift screen",
                "Click Application Configuration -> General -> Shift",
                "The Shift screen is shown",
                rendered ? "Opened " + page.url()
                           + (sh.lastRoute.isEmpty() ? "" : " (menu route " + sh.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the General submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", sh.describeControls());

        // 2) Add
        boolean formOpen = sh.clickAdd();
        addSummary("Form controls", sh.describeControls());
        step(page, "Click Add", "Click Add to open the shift form",
                "The entry form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — entry form not reached"); return; }

        // 3) Code + Remark
        String entry = sh.enterDetails(code, remark);
        boolean entered = sh.detailsEntered(code, remark);
        step(page, "Enter code and remark", "Enter the Code " + code + " and the Remark " + remark,
                "Both are entered",
                (entered
                    ? "PASSES because both boxes accepted the value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry),
                entered ? "PASS" : "FAIL");

        // 4) From Time + To Time — through each box's own picker, verified on the record's model.
        String times = sh.enterTimes(from, to);
        boolean timesOk = sh.timesEntered();
        addSummary("Shift model after the times", sh.dumpShiftModel());
        step(page, "Enter from time and to time",
                "Enter the From Time " + from + " and the To Time " + to,
                "Both times are entered",
                (timesOk
                    ? "PASSES because the shift's own model carries two distinct times: " + times
                    : "FAILS because the times did not reach the record's model: " + times),
                timesOk ? "PASS" : "FAIL");

        // 5) Cell Colour
        String col = sh.selectCellColour(colour);
        boolean colourOk = sh.colourSelected();
        step(page, "Select cell colour", "Select the Cell Colour",
                "A cell colour is selected",
                (colourOk
                    ? "PASSES because the control holds the chosen colour: " + col
                    : "FAILS because no cell colour could be set: " + col),
                colourOk ? "PASS" : "FAIL");

        // 6) Submit -> toast
        String toast = sh.submitAndGetToast();
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.General_page.Shift.isSuccess(toast);

        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        boolean inList = sh.codeInList(code, remark);
        addSummary("List check", sh.lastListCheck);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + sh.lastSaveDiagnostics
                  + (inList ? " BUT the record IS in the list, so the save worked silently." : "")
                : (ok ? toast
                      : noMessage
                        ? (inList
                            ? "DEFECT: the shift IS created (it is in the list) but the toast reads \""
                              + toast + "\" instead of a success message — the screen's result code has no "
                              + "text in the message master."
                            : "Submit answered \"" + toast + "\" and the record is NOT in the list. "
                              + sh.lastListCheck)
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

        step(page, "Verify the shift was created", "Look for " + code + " in the list",
                "The new shift is listed",
                (inList
                    ? "PASSES because the saved record — the code AND its remark — was found in the list: "
                      + sh.lastListCheck
                    : "FAILS because the saved record could not be found in the list. " + sh.lastListCheck),
                inList ? "PASS" : "FAIL");

        addSummary("Code / Remark", code + " / " + remark);
        addSummary("From / To", from + " - " + to);
        addSummary("Cell Colour", sh.lastColour);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
