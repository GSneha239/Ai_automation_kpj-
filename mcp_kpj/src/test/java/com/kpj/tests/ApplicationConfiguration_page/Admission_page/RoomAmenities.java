package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named RoomAmenities — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Room Amenities</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Room Amenities</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Click <b>Submit</b>; wait for the success toast. If the toast says the code/remark already exists,
 *       change the details and Submit again.</li>
 * </ol>
 */
public class RoomAmenities extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the code / remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public RoomAmenities() { super("ApplicationConfig_RoomAmenities"); }

    public static void main(String[] args) {
        RoomAmenities t = new RoomAmenities();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Room Amenities", "Application Configuration > Admission > Room Amenities",
                "Add a Room Amenity: enter Code + Remark, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.RoomAmenities ra =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.RoomAmenities(page);

        // 1) Navigate
        ra.navigateViaMenu();
        boolean onScreen = ra.onScreen();
        step(page, "Open Room Amenities screen", "Click Application Configuration -> Room Amenities",
                "The Room Amenities screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) + 3) Enter Code + Remark, Submit — retry with different details on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = ra.fillCodeAndRemark(attempt);
            used++;
            toast = ra.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("RoomAmenities: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=RA") && !fill.contains("Remark=(no") && !fill.contains("Remark=null");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                         : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; on 'already exists' change the details and Submit again",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Room Amenity Code", ra.lastCode);
        addSummary("Remark", ra.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
