package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ShiftAllocation — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Shift Allocation</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Shift Allocation</b>.</li>
 *   <li>Select the list <b>Location</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Shift</b>, <b>Opening Balance</b>, one <b>Day</b>.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class ShiftAllocation extends DevHisBase {

    public ShiftAllocation() { super("ApplicationConfig_Location_ShiftAllocation"); }

    public static void main(String[] args) {
        ShiftAllocation t = new ShiftAllocation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static final String[] DAYS = { "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday" };

    @Override
    protected void body() {
        meta("Application Configuration - Shift Allocation",
                "Application Configuration > Location > Shift Allocation",
                "Add a shift allocation: Location, Shift, Opening Balance, one Day, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.ShiftAllocation sa =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.ShiftAllocation(page);

        boolean onScreen = sa.navigateViaMenu();
        step(page, "Open Shift Allocation screen",
                "Click Application Configuration -> Location -> Shift Allocation",
                "The Shift Allocation screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + sa.currentScreen() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) {
            System.out.println("shift links => " + sa.findShiftLinks());
            addSummary("Result", "FAILED — screen not reached");
            return;
        }
        System.out.println("--- LIST SCREEN ---\n" + sa.describeForm());

        // 1) Select the list-level Location
        String listLoc = sa.selectListLocation();
        step(page, "Select Location", "Choose the Location on the list screen", "A Location is selected",
                listLoc.isEmpty() ? "Location NOT selected" : "Location = " + listLoc, listLoc.isEmpty() ? "FAIL" : "PASS");

        // 2) Search
        boolean searched = sa.clickSearch();
        step(page, "Click Search", "Click Search", "The grid refreshes for the selected Location",
                searched ? "Searched" : "Search did NOT run", searched ? "PASS" : "FAIL");

        // 3) Add
        boolean added = sa.clickAdd() && sa.addFormOpen();
        step(page, "Click Add", "Click Add", "The add form opens",
                added ? "Add form opened" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) {
            System.out.println("--- SCREEN ---\n" + sa.describeForm());
            addSummary("Result", "FAILED — add form did not open");
            return;
        }
        System.out.println("--- ADD FORM ---\n" + sa.describeForm());

        // 4) Location, Shift, Opening Balance, Days — Submit, retrying across Shift/Day when the combo
        // already has an allocation ("already exists"). Each retry re-opens a FRESH form.
        String toast = "", tl;
        boolean ok = false;
        StringBuilder tries = new StringBuilder();
        int shiftIndex = 0, dayIndex = 0;
        for (int attempt = 0; attempt < 6 && !ok; attempt++) {
            String filled = sa.fillAll(shiftIndex, "5", DAYS[dayIndex % DAYS.length]);
            System.out.println("Attempt " + (attempt + 1) + ": " + filled);
            toast = sa.submitAndGetToast();
            tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added");
            tries.append(attempt == 0 ? "" : " | ").append(sa.lastShift).append('/').append(sa.lastDay).append(" -> \"").append(toast).append('"');
            if (ok) break;
            if (!tl.contains("already") && !tl.contains("exist")) break;
            // move to the next Shift/Day combination and start from a fresh form. A duplicate rejection leaves
            // the add form OPEN (Submit only navigates back on success), so the previous Day checkbox is still
            // ticked — tickDay() only ADDS a tick, it does not clear the others. Always cycle Back -> Add so the
            // next fillAll() lands on a genuinely clean form rather than accumulating checked days. clickBack()
            // is a harmless no-op if Submit already left us on the list (onScreen()'s text fallback matches the
            // add form's own header too — "Add Shift Allocation" contains "Shift Allocation" — so it cannot tell
            // list and form apart; clickBack()/clickAdd() succeeding or no-op'ing is the reliable signal instead).
            dayIndex++;
            if (dayIndex % DAYS.length == 0) shiftIndex++;
            sa.clickBack();
            if (!(sa.clickAdd() && sa.addFormOpen())) break;
        }
        step(page, "Fill and Submit", "Select Location, Shift, Opening Balance, tick one Day, click Submit",
                "The shift allocation is saved",
                (toast == null || toast.isEmpty() ? "No message appeared" : "Message: \"" + toast + "\"")
                        + (sa.lastSaveApi.isEmpty() ? "" : "  [" + sa.lastSaveApi + "]")
                        + "  [attempts: " + tries + "]",
                ok ? "PASS" : "FAIL");

        // 5) Success toast
        String actual = toast == null || toast.isEmpty() ? "No toast appeared" : "Toast: \"" + toast + "\"";
        step(sa.toastPng, "Success toast message", "Wait for the success toast after Submit",
                "'Shift Allocation saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Location", sa.lastFormLocation);
        addSummary("Shift", sa.lastShift);
        addSummary("Opening Balance", sa.lastOpeningBalance);
        addSummary("Day", sa.lastDay);
        addSummary("Save API", sa.lastSaveApi);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
