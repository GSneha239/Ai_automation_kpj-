package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BedSideAmenities — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Side Amenities</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Bed Side Amenities</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Tick any one record in the table.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class BedSideAmenities extends DevHisBase {

    public BedSideAmenities() { super("ApplicationConfig_BedSideAmenities"); }

    public static void main(String[] args) {
        BedSideAmenities t = new BedSideAmenities();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Bed Side Amenities", "Application Configuration > Admission > Bed Side Amenities",
                "Add a Bed Side Amenity: enter Code + Remark, tick a record in the table, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedSideAmenities bsa =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedSideAmenities(page);

        // 1) Navigate, then WAIT for the screen to finish loading. Filling while the app still shows the previous
        //    screen ("TRANSFER") with a blank form lets the later re-render wipe the values — the step screenshot
        //    then shows an empty form even though the write "succeeded".
        bsa.navigateViaMenu();
        boolean onScreen = bsa.onScreen() && bsa.waitForScreenReady();
        step(page, "Open Bed Side Amenities screen", "Click Application Configuration -> Bed Side Amenities; wait for the screen to load",
                "The Bed Side Amenities screen is shown (Form Name resolved, grid loaded)",
                onScreen ? "Opened " + bsa.currentScreen() : "Did NOT reach a loaded screen - " + bsa.currentScreen(),
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Enter Code + Remark — asserted by READING THE VALUES BACK out of the DOM, not by trusting the write.
        String fill = bsa.fillCodeAndRemark();
        System.out.println("BedSideAmenities fill => " + fill);
        boolean fillOk = bsa.valuesPresent();
        step(page, "Enter Code + Remark", "Enter the Code and Remark",
                "Code and Remark are entered AND still present on screen",
                fill + (fillOk ? "" : "  <-- values are NOT on screen (cleared by a re-render)"),
                fillOk ? "PASS" : "FAIL");

        // 3) Select any record from the table
        String rec = bsa.selectFirstRecord();
        boolean recOk = rec != null && !rec.isEmpty();
        step(page, "Select a record", "Tick any one record's checkbox in the table",
                "A record is selected", recOk ? "Selected: " + rec : "No record selected", recOk ? "PASS" : "FAIL");

        // 4) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String toast = bsa.submitWithRetries();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save failed — server returned: \"" + toast + "\"");
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Amenity Code", bsa.lastCode);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
