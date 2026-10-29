package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BloodBagOpeningStock — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Blood Bag Opening Stock</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>Blood Bag Opening Stock</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Blood Bag No.</b>, <b>Bag Weight(gm)</b>, <b>Blood Bag Type</b>, <b>Component</b>, <b>Blood Group</b>,
 *       <b>Technician</b>, <b>BTO</b> and <b>Remark</b>.</li>
 *   <li>Click the form's <b>Add</b> — the bag moves into the grid on the form.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 *   <li>Verify the saved bag in the list table.</li>
 * </ol>
 *
 * <p>The Blood Bag No. and Remark are generated per run (the shared QA environment keeps every record ever saved) and a different
 * bag number is entered if the server answers "already exists".</p>
 *
 * <p>&#9888; A successful run CREATES a REAL blood bag opening stock record in the target environment.</p>
 */
public class BloodBagOpeningStock extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** How many times to re-enter a fresh bag when the server says the bag number already exists. */
    private static final int MAX_ATTEMPTS = 5;

    public BloodBagOpeningStock() { super("Ancillary_BloodBank_BloodBagOpeningStock"); }

    public static void main(String[] args) {
        BloodBagOpeningStock t = new BloodBagOpeningStock();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Blood Bank - Blood Bag Opening Stock", "Ancillary Services > Blood Bank > Blood Bag Opening Stock",
                "&#9888; Creates a REAL blood bag opening stock record: Add, enter the bag details, Add to the form's grid, Save, check the toast and the list table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagOpeningStock bb =
                new com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagOpeningStock(page);

        // 1) Navigate
        boolean rendered = bb.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", bb.lastMenu);
        step(page, "Open Blood Bag Opening Stock screen",
                "Click Ancillary Services -> Blood Bank -> Blood Bag Opening Stock",
                "The Blood Bag Opening Stock list screen is shown",
                rendered ? "Opened " + page.url()
                           + (bb.lastRoute.isEmpty() ? "" : " (menu route " + bb.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("List screen controls", bb.describeControls());

        // 2) Add
        boolean added = bb.clickAdd();
        step(page, "Click Add", "Click Add", "The Blood Bag Opening Stock entry form opens",
                added ? bb.lastAdd : "FAILS — " + bb.lastAdd, added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — the add form did not open"); return; }
        addSummary("Add form controls", bb.describeControls());

        // 3) Details, 4) the form's Add, 5) Save -> toast. A rejected Save leaves the staged row on the form, so a retry starts the
        // form again (Back, Add) with a new bag number. The steps are recorded after the loop, so each screenshot is taken in the loop
        // at the moment it is meaningful: the filled form BEFORE the form's Add, the staged grid, and the page right after the toast.
        String entry = "", toast = "";
        boolean entered = false, staged = false, ok = false, duplicate = false;
        int used = 0;
        byte[] enteredShot = null, stagedShot = null, toastShot = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (attempt > 0 && !(bb.clickBack() && bb.clickAdd())) break;
            entry = bb.fillDetails(attempt);
            entered = bb.detailsEntered();
            entry = bb.lastEntry;                       // now carries the read-back when a box did not hold its value
            used++;
            enteredShot = shot();
            if (!entered) break;                        // nothing sensible to stage — report the entry failure
            staged = bb.addToStock();
            stagedShot = shot();
            if (!staged) break;                         // nothing staged to save — report why
            toast = bb.saveAndGetToast();
            toastShot = shot();
            ok = com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagOpeningStock.isSuccess(toast);
            duplicate = com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagOpeningStock.isDuplicate(toast);
            if (ok || !duplicate) break;
            System.out.println("BloodBagOpeningStock: attempt " + used + " (" + bb.lastBagNo + ") already exists — entering a new bag");
        }
        String bag = bb.lastBagNo;

        stepShot(enteredShot, "Enter the bag details",
                "Enter Blood Bag No. " + bag + ", Bag Weight " + bb.lastWeight + " gm, Blood Bag Type, Component, Blood Group, Technician, BTO and Remark",
                "Every box holds its value",
                (entered
                    ? "PASSES because each box accepted its own value and read it back: " + entry
                    : "FAILS because a value did not land in its own field: " + entry)
                    + (used > 1 ? "  [bag number changed " + (used - 1) + "x after 'already exists']" : ""),
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the details could not be entered"); return; }

        stepShot(stagedShot, "Click Add", "Click the form's Add",
                "The bag appears in the grid above the form with every entered value, and the entry boxes clear",
                staged ? "PASSES because the bag is in the form's grid with the values entered: " + bb.lastStaged
                       : "FAILS — " + bb.lastStaged,
                staged ? "PASS" : "FAIL");
        if (!staged) { addSummary("Result", "FAILED — the bag was not added to the form's grid"); return; }

        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + bb.lastSaveDiagnostics
                : (ok ? toast + "  (" + bb.lastSaveDiagnostics + ")"
                      : duplicate ? "Still 'already exists' after " + used + " attempt(s) with different bag numbers: \"" + toast + "\""
                                  : "Save not confirmed — server returned: \"" + toast + "\"  (" + bb.lastSaveDiagnostics + ")");
        stepShot(toastShot, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 6) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = bb.verifyInTable();
        addSummary("List check", bb.lastListCheck);
        step(page, "Check the saved bag in the table",
                "Open the list; search today's date; filter the Blood Bag No. column by " + bag + "; check the Blood Bag Type, Component Name and Blood Group of the row",
                "The saved bag is in the table with the entered Blood Bag Type, Component Name and Blood Group",
                (inList
                    ? "PASSES because the saved bag was found in the table with the values entered: " + bb.lastListCheck
                    : "FAILS because the saved bag could not be found in the table with those values. " + bb.lastListCheck
                        + (ok ? "" : "  (the Save was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        // 7) One saved bag is one row. Seen 2026-10-29: the Blood Bag Type list holds 32 entries under ONE id, the saved bag is stored
        // under that id, and the list returns it once per type that shares the id. That is an application / master-data defect and is
        // reported as such rather than passed over (the page stays red until the types carry their own ids).
        if (inList) {
            boolean once = bb.lastRowsForBag == 1;
            step(page, "The saved bag is listed once",
                    "Count the table rows that carry Blood Bag No. " + bag,
                    "One row for the one bag that was saved",
                    once ? "PASSES because the bag is listed once"
                         : "FAILS because the list shows the one saved bag " + bb.lastRowsForBag + " times (" + bb.lastRecordsForBag
                           + " distinct record" + (bb.lastRecordsForBag == 1 ? "" : "s") + ") — the same record repeated, once per Blood Bag Type."
                           + (bb.lastTypeIds.isEmpty() ? "" : "  CAUSE: " + bb.lastTypeIds
                               + ", so the form can only hold the first of them and the list returns the bag under every one.")
                           + "  " + bb.lastListCheck,
                    once ? "PASS" : "FAIL");
        }
        if (!bb.lastTypeIds.isEmpty()) addSummary("Blood Bag Type ids", bb.lastTypeIds);

        addSummary("Blood Bag No.", bag);
        addSummary("Weight / Type / Component / Group", bb.lastWeight + " gm / " + bb.lastType + " / " + bb.lastComponent + " / " + bb.lastGroup);
        addSummary("Technician / BTO", bb.lastTechnician + " / " + bb.lastBto);
        addSummary("Remark", bb.lastRemark);
        addSummary("Screen defaults", bb.lastDefaults);
        addSummary("Attempts", String.valueOf(used));
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
