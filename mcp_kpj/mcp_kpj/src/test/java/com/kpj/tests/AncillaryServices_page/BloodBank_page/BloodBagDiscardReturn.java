package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BloodBagDiscardReturn — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>BloodBag Discard/Return</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>BloodBag Discard/Return</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select the <b>Extract Blood Bag No.</b>, the <b>BTO</b> and the <b>Reason Of Discarded</b>.</li>
 *   <li>Click the form's <b>Add</b> — the bag moves into the table on the form.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 *   <li>Verify the discard in the list table.</li>
 * </ol>
 *
 * <p>The row count and highest id of today's list are noted BEFORE the form is opened, so the new row can be told from older ones.</p>
 *
 * <p>&#9888; A successful run DISCARDS a REAL blood bag in the target environment — it leaves the Extract Blood Bag No. list. Only bags
 * the test suite itself created (Blood Bag Opening Stock, numbers {@code BB…}) are taken; when none is left the run says so. Run
 * Blood Bag Opening Stock to add more.</p>
 */
public class BloodBagDiscardReturn extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public BloodBagDiscardReturn() { super("Ancillary_BloodBank_BloodBagDiscardReturn"); }

    public static void main(String[] args) {
        BloodBagDiscardReturn t = new BloodBagDiscardReturn();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Blood Bank - BloodBag Discard/Return", "Ancillary Services > Blood Bank > BloodBag Discard/Return",
                "&#9888; DISCARDS a REAL blood bag: Add, choose the Extract Blood Bag No., BTO and Reason, Add, Save, check the toast and the list table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagDiscardReturn bd =
                new com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagDiscardReturn(page);

        // 1) Navigate
        boolean rendered = bd.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", bd.lastMenu);
        step(page, "Open BloodBag Discard/Return screen",
                "Click Ancillary Services -> Blood Bank -> BloodBag Discard/Return",
                "The BloodBag Discard/Return list screen is shown",
                rendered ? "Opened " + page.url()
                           + (bd.lastRoute.isEmpty() ? "" : " (menu route " + bd.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("List screen controls", bd.describeControls());
        addSummary("List before the save", bd.baseline());

        // 2) Add
        boolean added = bd.clickAdd();
        step(page, "Click Add", "Click Add", "The BloodBag Discard entry form opens",
                added ? bd.lastAdd : "FAILS — " + bd.lastAdd, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the form did not open"); return; }
        addSummary("Form controls", bd.describeControls());

        // 3) Extract Blood Bag No., BTO, Reason Of Discarded
        String entry = bd.fillDetails(0);
        boolean entered = bd.detailsEntered();
        entry = bd.lastEntry;                           // carries the read-back when a box did not hold its value
        stepShot(shot(), "Select extract blood bag no, BTO and reason of discarded",
                "Choose the Extract Blood Bag No., the BTO and the Reason Of Discarded",
                "Each box holds its choice",
                entered ? "PASSES because each box accepted its choice and read it back: " + entry
                        : "FAILS because a choice did not land in its own box: " + entry,
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the discard details could not be chosen"); return; }

        // 4) Add
        boolean staged = bd.addToList();
        stepShot(shot(), "Click Add", "Click the form's Add",
                "The bag appears in the table above the entry boxes with the chosen reason and BTO, and the boxes clear",
                staged ? "PASSES because the bag is in the form's table with the values chosen: " + bd.lastStaged
                       : "FAILS — " + bd.lastStaged,
                staged ? "PASS" : "FAIL");
        if (!staged) { addSummary("Result", "FAILED — the bag was not added to the form's table"); return; }

        // 5) Save -> toast. The screenshot is taken at once: the screen returns to the list and the toast fades.
        String toast = bd.saveAndGetToast();
        byte[] toastShot = shot();
        boolean ok = com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagDiscardReturn.isSuccess(toast);
        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + bd.lastSaveDiagnostics
                : (ok ? toast + "  (" + bd.lastSaveDiagnostics + ")"
                      : "Save not confirmed — the screen said: \"" + toast + "\"  (" + bd.lastSaveDiagnostics + ")");
        stepShot(toastShot, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 6) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = bd.verifyInTable();
        addSummary("List check", bd.lastListCheck);
        step(page, "Check the discarded bag in the table",
                "Open the list; Search today; find the new row for bag " + bd.lastBagNo + " with the chosen Discard Reason",
                "A new row carries the bag number and the Discard Reason chosen",
                (inList
                    ? "PASSES because the discard was found in the table with the values chosen: " + bd.lastListCheck
                    : "FAILS because the discard could not be found in the table exactly once. " + bd.lastListCheck
                        + (ok ? "" : "  (the Save was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Extract Blood Bag No.", bd.lastBag);
        addSummary("BTO / Reason Of Discarded", bd.lastBto + " / " + bd.lastReason);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    private byte[] shot() {
        try { return page.screenshot(); } catch (Exception e) { return null; }
    }

    /** Records a step with the given screenshot, or with a fresh one of the page when none could be taken. */
    private void stepShot(byte[] png, String name, String desc, String expected, String actual, String status) {
        if (png != null && png.length > 0) step(png, name, desc, expected, actual, status);
        else step(page, name, desc, expected, actual, status);
    }
}
