package com.kpj.tests.AncillaryServices_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AmbulanceUsage — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Usage</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Usage</b>.</li>
 *   <li>Click <b>Add</b> to open the usage form.</li>
 *   <li>Select <b>Vehicle Type</b>.</li>
 *   <li>Enter <b>MRN No.</b> and click <b>Search</b>.</li>
 *   <li>Select <b>Vehicle</b> and <b>Driver</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Pass one with {@code -Dmrn=100000956}; without it the flow uses {@link #DEFAULT_MRN}.
 * It is looked up under OPD, then IPD, then External — whichever resolves a patient wins.</p>
 *
 * <p>&#9888; A successful run CREATES a real ambulance usage record in the target environment.</p>
 */
public class AmbulanceUsage extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. Override per environment. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AmbulanceUsage() { super("AncillaryServices_Ambulance_AmbulanceUsage"); }

    public static void main(String[] args) {
        AmbulanceUsage t = new AmbulanceUsage();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ambulance Usage", "Ancillary Services > Ambulance > Ambulance Usage",
                "&#9888; Creates a REAL ambulance usage record: select Vehicle Type, search the patient by MRN, "
                        + "select Vehicle + Driver, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceUsage usage =
                new com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceUsage(page);

        // 1) Navigate to the list/search screen
        boolean onScreen = usage.navigateViaMenu();
        step(page, "Open Ambulance Usage screen",
                "Click Ancillary Services -> Ambulance -> Ambulance Usage",
                "The Ambulance Usage list screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) The usage form lives behind the list screen's Add button.
        boolean formOpen = usage.clickAdd();
        step(page, "Click Add", "Click Add on the list screen",
                "The Ambulance Usage form opens",
                formOpen ? "Usage form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — usage form not reached"); return; }

        usage.describeControls();   // diagnostics only

        // 3) Vehicle Type
        String vehicleType = usage.selectVehicleType();
        boolean vtOk = vehicleType != null && !vehicleType.isEmpty() && !vehicleType.startsWith("(");
        step(page, "Select Vehicle Type", "Select a Vehicle Type",
                "A Vehicle Type is selected",
                vtOk ? "Vehicle Type = " + vehicleType : "Vehicle Type NOT selected " + vehicleType,
                vtOk ? "PASS" : "FAIL");

        // 4) MRN + Search
        String searchResult = usage.searchByMrn(mrn);
        boolean found = usage.patientLoaded();
        step(page, "Enter MRN No. & click Search", "Enter MRN " + mrn + " and click Search (SearchPatientByMRNo)",
                "The patient is found and Patient Name is filled",
                found ? "MRN " + mrn + " -> " + searchResult : "No patient for MRN " + mrn + " (" + searchResult + ")",
                found ? "PASS" : "FAIL");
        if (!found) {
            addSummary("MRN", mrn);
            addSummary("Result", "FAILED — no patient for this MRN (pass a valid one with -Dmrn=...)");
            return;
        }

        // 5) Vehicle + Driver
        String vd = usage.selectVehicleAndDriver();
        boolean vdOk = usage.vehicleAndDriverSelected();
        step(page, "Select Vehicle and Driver", "Select Vehicle and Driver",
                "Vehicle and Driver are both selected", vd, vdOk ? "PASS" : "FAIL");

        // 6) Save -> success toast
        String toast = usage.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (fnIUDAmbulanceUsage); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient", usage.lastPatientName + (usage.lastPatientType.isEmpty() ? "" : " (" + usage.lastPatientType + ")"));
        addSummary("Vehicle Type", vehicleType);
        addSummary("Vehicle", usage.lastVehicle);
        addSummary("Driver", usage.lastDriver);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
