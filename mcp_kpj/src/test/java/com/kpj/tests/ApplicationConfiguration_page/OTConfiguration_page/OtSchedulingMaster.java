package com.kpj.tests.ApplicationConfiguration_page.OTConfiguration_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OtSchedulingMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; OT Configuration &gt; <b>OT Scheduling Master</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Application Configuration</b> → <b>OT Configuration</b> → <b>OT Scheduling Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the <b>OT Theatre</b>, then the <b>OT Table</b> it fills.</li>
 *   <li>Enter the <b>Start Time</b> and <b>End Time</b>.</li>
 *   <li>Tick the checkbox for the day.</li>
 *   <li>Click <b>Add</b> — the schedule must appear as a row.</li>
 *   <li>Click <b>Submit</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The OT Table list is filled from the theatre, so a theatre that has one is looked for rather than
 * taking the first. The day checkboxes all share one model and are told apart by label, and the time
 * boxes are masked, so they take real keystrokes.</p>
 *
 * <p>Pin values with {@code -DstartTime=}, {@code -DendTime=}, {@code -Dday=}.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL OT schedule in the target environment.</p>
 */
public class OtSchedulingMaster extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public OtSchedulingMaster() { super("ApplicationConfiguration_OtSchedulingMaster"); }

    public static void main(String[] args) {
        OtSchedulingMaster t = new OtSchedulingMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OT Scheduling Master", "Application Configuration > OT Configuration > OT Scheduling Master",
                "&#9888; Creates a REAL OT schedule: Add, select the OT Theatre and OT Table, enter the "
                        + "Start and End Time, tick the day, Add the line, then Submit.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // The picker parses its own display format; "0900AM" is text it cannot read, and an unparseable
        // entry silently clears the model the Add button reads.
        String startTime = System.getProperty("startTime", "09:00 AM");
        String endTime = System.getProperty("endTime", "10:00 AM");
        String day = System.getProperty("day", "Monday");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.OtSchedulingMaster ots =
                new com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.OtSchedulingMaster(page);

        // 1) Navigate
        boolean rendered = ots.navigateViaMenu(BASE);
        if (!rendered) addSummary("OT Configuration submenu offered", ots.lastMenu);
        step(page, "Open OT Scheduling Master screen",
                "Click Application Configuration -> OT Configuration -> OT Scheduling Master",
                "The OT Scheduling Master screen is shown",
                rendered ? "Opened " + page.url()
                           + (ots.lastRoute.isEmpty() ? "" : " (menu route " + ots.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the OT Configuration submenu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        addSummary("List screen controls", ots.describeControls());

        // 2) Add
        String add = ots.openFormIfNeeded();
        boolean formOpen = ots.formOpen();
        step(page, "Click Add", "Click Add to open the entry form",
                "The entry form is shown",
                (formOpen
                    ? "PASSES because the entry form is on screen: " + add
                    : "FAILS because no entry form appeared: " + add),
                formOpen ? "PASS" : "FAIL");
        addSummary("Form controls", ots.describeControls());

        // 3) OT Theatre
        String theatre = ots.selectTheatreWithTable();
        boolean theatreOk = ots.theatreSelected();
        step(page, "Select the OT theatre", "Select the OT Theatre",
                "An OT theatre is selected",
                (theatreOk
                    ? "PASSES because the dropdown holds the choice: " + theatre
                      + ". A theatre that actually has a table is looked for — the OT Table list is "
                      + "filled FROM this choice, and it is waited for until it CHANGES rather than "
                      + "until it is non-empty."
                    : "FAILS because no usable OT theatre could be selected: " + theatre),
                theatreOk ? "PASS" : "FAIL");

        // 4) OT Table
        String table = ots.selectTable();
        boolean tableOk = ots.tableSelected();
        step(page, "Select the OT table", "Select the OT Table",
                "An OT table is selected",
                (tableOk
                    ? "PASSES because the dropdown holds the choice: " + table
                    : "FAILS because no OT table could be selected: " + table),
                tableOk ? "PASS" : "FAIL");

        // 5) Start / End time
        String times = ots.enterTimes(startTime, endTime);
        boolean timesOk = ots.timesEntered();
        step(page, "Enter start time and end time",
                "Enter the Start Time " + startTime + " and the End Time " + endTime,
                "Both times are entered",
                (timesOk
                    ? "PASSES because both boxes read their value back: " + times
                      + ". They are masked boxes sharing one model (inputTime), so they are driven with "
                      + "real keystrokes and told apart by position — an assigned value is ignored by "
                      + "the mask and would read back empty."
                    : "FAILS — a time did not stay in its box: " + times),
                timesOk ? "PASS" : "FAIL");

        // 6) Day checkbox
        String dayResult = ots.tickDay(day);
        boolean dayOk = ots.dayTicked();
        step(page, "Tick the checkbox for the day", "Tick the checkbox for " + day,
                "The day is ticked",
                (dayOk
                    ? "PASSES because the box was CLICKED and read back as set: " + dayResult
                    : "FAILS because the day checkbox did not take: " + dayResult),
                dayOk ? "PASS" : "FAIL");

        // 7) Add the line
        String addRow = ots.clickAdd();
        addSummary("What the Add button itself validates", ots.describeAddHandler());
        boolean rowAdded = ots.rowAdded();
        step(page, "Click Add", "Click Add to put the schedule into the list",
                "A new row appears in the schedule list",
                (rowAdded
                    ? "PASSES because the list grew — " + addRow + ". The click alone is not credited: "
                      + "the row count before and after is what says the schedule was added."
                    : "FAILS because the list did not grow — " + addRow),
                rowAdded ? "PASS" : "FAIL");

        // Submit. One schedule is allowed per OT table, so a combination that already carries one is
        // refused with "OT already has scheduled!". That is the screen working as intended, not a
        // defect — so the flow moves to a free combination and repeats the entry, and says which
        // combinations it had to walk through.
        String toast = ots.submitAndGetToast();
        StringBuilder combos = new StringBuilder("first tried " + ots.lastTheatre + " / " + ots.lastTable);
        for (int attempt = 1; attempt <= 3
                && com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.OtSchedulingMaster.alreadyScheduled(toast);
                attempt++) {
            String moved = ots.nextCombination();
            combos.append("  ||  \"").append(toast).append("\" -> ").append(moved);
            if (moved.startsWith("(")) break;
            ots.enterTimes(startTime, endTime);
            ots.tickDay(day);
            ots.clickAdd();
            toast = ots.submitAndGetToast();
        }
        addSummary("Combinations tried", combos.toString());
        boolean ok = com.kpj.pages.ApplicationConfiguration_page.OTConfiguration_page.OtSchedulingMaster.isSuccess(toast);
        boolean noMessage = !ok && toast != null && toast.toLowerCase().contains("message not found");
        addSummary("What the screen put on screen after Submit", ots.lastPopupsSeen);

        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + ots.lastSaveDiagnostics
                : (ok ? toast
                      : noMessage
                        ? "DEFECT: the screen's result code has no text in the message master — the toast "
                          + "reads \"" + toast + "\" instead of a success message."
                        : "Submit not confirmed — server returned: \"" + toast + "\"");
        // A message containing "undefined" FAILS the step, even though the save goes through: the record
        // identifier is never substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        boolean updated = toast != null && toast.toLowerCase().contains("updat");
        String creationNote = updated
                ? "  ||  WHAT THE SCREEN DID: it UPDATED an existing schedule rather than creating a new "
                  + "one. This environment already holds a schedule for every OT theatre/table tried, and "
                  + "the screen allows only one per table — the first combination was refused with \"OT "
                  + "already has scheduled!\", so the flow moved on and the next one saved as an update. "
                  + "The combinations walked through: " + combos
                : "";
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (ots.toastFromObserver
                        ? "  ||  HOW THE MESSAGE WAS READ: these toasts live about five seconds. This one "
                          + "was recorded by a mutation observer at the moment the screen showed it, so it "
                          + "is the screen's own text and not a reconstruction."
                        : "")
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen "
                          + "cannot name what it saved. The step FAILS on the message: a confirmation "
                          + "that names no record is not a valid confirmation, even though the record "
                          + "itself is written."
                        : "") + creationNote,
                (ok && !malformed) ? "PASS" : "FAIL");

        addSummary("OT theatre / table", ots.lastTheatre + " / " + ots.lastTable);
        addSummary("Times", ots.lastTimes);
        addSummary("Day", ots.lastDay);
        addSummary("Added row", ots.lastAddRow);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
