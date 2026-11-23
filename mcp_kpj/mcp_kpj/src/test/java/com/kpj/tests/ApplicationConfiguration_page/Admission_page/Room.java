package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Room — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Room</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Room</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>Code</b> + <b>Remark</b>.</li>
 *   <li>Select <b>Room Type</b>.</li>
 *   <li>Tick any one <b>Amenity</b> checkbox in the table.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 */
public class Room extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Remark already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Room() { super("ApplicationConfig_Admission_Room"); }

    public static void main(String[] args) {
        Room t = new Room();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Room", "Application Configuration > Admission > Room",
                "Add a Room: Add, enter Code + Remark, select Room Type, tick an Amenity checkbox, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.Room room =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.Room(page);

        // 1) Navigate
        room.navigateViaMenu();
        boolean onScreen = room.onScreen();
        step(page, "Open Room screen", "Click Application Configuration -> Room",
                "The Room screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = room.clickAdd();
        step(page, "Click Add", "Click Add", "The Room add form opens",
                added ? "Add clicked" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Enter Code + Remark, select Room Type + an amenity once, then Submit — retry with fresh details on
        // "already exists" (Code and/or Remark can collide with existing records).
        String fill = "", toast = "";
        boolean rtOk = false, amOk = false;
        String rt = "", amenity = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = room.fillCodeAndRemark(attempt);
            used++;
            if (attempt == 0) {
                rt = room.selectRoomType();
                rtOk = rt != null && !rt.startsWith("(");
                amenity = room.selectAmenity();
                amOk = amenity != null && !amenity.isEmpty();
            }
            toast = room.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Room: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=RM") && !fill.contains("Remark=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Enter Code + Remark", "Enter the Code and Remark", "Code and Remark are entered",
                fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Select Room Type
        step(page, "Select Room Type", "Select a Room Type from the dropdown", "A Room Type is selected",
                rt, rtOk ? "PASS" : "FAIL");

        // 5) Select an amenity
        step(page, "Select an amenity", "Tick any one amenity checkbox in the table",
                "An amenity is selected", amOk ? "Selected: " + amenity : "No amenity selected", amOk ? "PASS" : "FAIL");

        // 6) Submit -> success toast. A non-success toast (e.g. a server "Error!") FAILs the test.
        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Room Code", room.lastCode);
        addSummary("Remark", room.lastRemark);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
