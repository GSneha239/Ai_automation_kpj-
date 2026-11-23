package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BedClass — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Bed Class</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Bed Class</b> (grid: Bed Class Code / Room Type / Status / Edit).</li>
 *   <li>Click <b>Add</b> — opens the Bed Class add form.</li>
 *   <li>Fill <b>Bed Class Code</b> + <b>Room Type</b>.</li>
 *   <li>Tick any one <b>Select</b> checkbox in the <b>Pricing Policy</b> table.</li>
 *   <li>Click <b>Submit</b> → success toast.</li>
 * </ol>
 *
 * <p>Master-configuration screen — there is no patient to select; Add opens a fresh form directly.</p>
 */
public class BedClass extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Bed Class Code / Room Type already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public BedClass() { super("ApplicationConfig_Admission_BedClass"); }

    public static void main(String[] args) {
        BedClass t = new BedClass();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Bed Class", "Application Configuration > Admission > Bed Class",
                "Add a Bed Class: Add, fill Bed Class Code + Room Type, tick a Pricing Policy checkbox, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedClass bc =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.BedClass(page);

        // 1) Navigate: Application Configuration -> Bed Class
        bc.navigateViaMenu();
        boolean onScreen = bc.onBedClassScreen();
        System.out.println("BedClass screen => " + bc.dumpScreen());
        step(page, "Open Bed Class screen", "Click Application Configuration -> Bed Class",
                "The Bed Class list (Bed Class Code / Room Type) is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the Bed Class screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — Bed Class screen not reached"); return; }

        // 2) Add
        boolean added = bc.clickAdd();
        System.out.println("BedClass add form => " + bc.dumpAddForm());
        step(page, "Click Add", "Click Add", "The Bed Class add form opens",
                added ? "Add form opened" : "Add form did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Fill Bed Class Code + Room Type, tick Location + Pricing Policy once, then Submit — retry with fresh
        // details on "already exists" (Bed Class Code and/or Room Type can collide with existing records).
        String fill = "", toast = "";
        boolean locOk = false, policyOk = false;
        String location = "", policy = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bc.fillBedClassDetails(attempt);
            used++;
            System.out.println("BedClass fill => " + fill);
            if (attempt == 0) {
                location = bc.selectLocation();
                locOk = location != null && !location.isEmpty();
                policy = bc.selectPricingPolicy();
                policyOk = policy != null && !policy.isEmpty();
            }
            toast = bc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("BedClass: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("BedClassCode=BC") && !fill.contains("RoomType=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Fill the details", "Fill Bed Class Code + Room Type",
                "Bed Class Code and Room Type are filled", fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Tick the Location row (its table loads async, same as Pricing Policy)
        step(page, "Select the Location", "Tick the Select checkbox in the Location table",
                "A location row is selected", locOk ? "Selected: " + location : "No location selected",
                locOk ? "PASS" : "FAIL");

        // 5) Tick a Pricing Policy checkbox
        step(page, "Select a checkbox", "Tick any one Select checkbox in the Pricing Policy table",
                "A pricing policy row is selected", policyOk ? "Selected: " + policy : "No pricing policy selected",
                policyOk ? "PASS" : "FAIL");

        // 6) Submit -> success toast. A non-success toast (e.g. the server "Error!") FAILs the test.
        String actual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No toast appeared" : (ok ? toast : "Save failed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast", "Click Submit; wait for the success toast",
                "'Bed Class saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Bed Class Code", bc.lastCode);
        addSummary("Room Type", bc.lastRoomType);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "FAILED — save rejected (\"" + toast + "\")");
    }
}
