package com.kpj.tests.ApplicationConfiguration_page.Admission_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Bed, so it is referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Admission &gt; <b>Bed</b> ({@code #/BedMaster}).
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Bed</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddBedMaster()}) — opens the Bed Master add form.</li>
 *   <li>Fill the details (Code, Bed Number, Location, Ward, Room Type, Bed Class, Department).</li>
 *   <li>Tick the <b>Non-Census (Lodger)</b> checkbox.</li>
 *   <li>Click <b>Submit</b> ({@code fnIUDBedMaster()}) → success toast.</li>
 * </ol>
 *
 * <p>Note: this is a master-configuration screen — there is no "patient" to select; the Add button opens a fresh
 * add form directly.</p>
 */
public class Bed extends DevHisBase {

    /** How many times to re-enter fresh details when the server says the Code / Bed Number already exists. */
    private static final int MAX_ATTEMPTS = 40;

    public Bed() { super("ApplicationConfig_Admission_Bed"); }

    public static void main(String[] args) {
        Bed t = new Bed();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Admission - Bed", "Application Configuration > Admission > Bed",
                "Add a Bed Master record: Add, fill the details, tick the Non-Census checkbox, Submit.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Admission_page.Bed bed =
                new com.kpj.pages.ApplicationConfiguration_page.Admission_page.Bed(page);

        // 1) Navigate: Application Configuration -> Bed (#/BedMaster)
        bed.navigateViaMenu();
        boolean onBed = page.url().toLowerCase().contains("bedmaster");
        step(page, "Open Bed screen", "Click Application Configuration -> Bed",
                "The Bed Master screen (#/BedMaster) is shown", onBed ? "Opened " + page.url() : "Did NOT reach #/BedMaster",
                onBed ? "PASS" : "FAIL");
        if (!onBed) { addSummary("Result", "FAILED — Bed screen not reached"); return; }

        // 2) Add
        boolean added = bed.clickAdd();
        step(page, "Click Add", "Click Add (AddBedMaster())", "The Bed Master add form opens",
                added ? "Add form opened" : "Add form did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        // 3) Fill the details, tick the checkbox + an amenity once, then Submit — retry with fresh details on
        // "already exists" (Code and/or Bed Number can collide with existing records).
        String fill = "", toast = "";
        boolean ticked = false, amenityOk = false;
        String amenity = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            fill = bed.fillBedDetails(attempt);
            used++;
            System.out.println("Bed fill => " + fill);
            if (attempt == 0) {
                ticked = bed.tickNonCensusCheckbox();
                amenity = bed.selectAmenity();
                amenityOk = amenity != null && !amenity.isEmpty();
            }
            toast = bed.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("Bed: attempt " + used + " (" + fill + ") already exists — changing the details");
        }

        boolean fillOk = fill != null && fill.contains("Code=AB") && !fill.contains("Ward=(no");
        String fillActual = fill + (used > 1 ? "  [details changed " + (used - 1) + "x after 'already exists']" : "");
        step(page, "Fill the details", "Fill Code, Bed Number, Location, Ward, Room Type, Bed Class, Department",
                "The bed details are filled", fillActual, fillOk ? "PASS" : "FAIL");

        // 4) Tick the Non-Census checkbox
        step(page, "Select the checkbox", "Tick the 'Non-Census (Lodger)' checkbox (BedMaster.isnoncensus)",
                "The checkbox is ticked", ticked ? "Checkbox ticked" : "Checkbox NOT ticked", ticked ? "PASS" : "FAIL");

        // 4b) Select any amenity (Bed Side / Room Amenities checkbox list)
        step(page, "Select an amenity", "Tick any one amenity (AmenitiesList[$index].isselected)",
                "At least one amenity is selected", amenityOk ? "Amenity ticked: " + amenity : "No amenity ticked",
                amenityOk ? "PASS" : "FAIL");

        // 5) Submit -> success toast
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "")
                + (toast == null || toast.isEmpty() ? "No success toast appeared" : toast);
        step(page, "Click Submit & success toast", "Click Submit (fnIUDBedMaster()); wait for the success toast",
                "'Bed Saved Successfully.' toast", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Bed Code", bed.lastCode);
        addSummary("Bed Number", bed.lastBedNumber);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed");
    }
}
