package com.kpj.tests.AncillaryServices_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AmbulanceRequisition — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Requisition</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Requisition</b>.</li>
 *   <li>Click <b>Add</b> to open the requisition form.</li>
 *   <li>Select <b>Vehicle Type</b>.</li>
 *   <li>Enter <b>MRN No.</b> and click <b>Search</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Pass one with {@code -Dmrn=100000956}; without it the flow uses {@link #DEFAULT_MRN}.
 * The MRN is looked up under OPD, then IPD, then External — whichever resolves a patient wins. If none
 * does, the flow reports that step FAIL and stops rather than saving against no patient.</p>
 *
 * <p>&#9888; A successful run CREATES a real ambulance requisition in the target environment.</p>
 */
public class AmbulanceRequisition extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. Override per environment. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AmbulanceRequisition() { super("AncillaryServices_Ambulance_AmbulanceRequisition"); }

    public static void main(String[] args) {
        AmbulanceRequisition t = new AmbulanceRequisition();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ambulance Requisition", "Ancillary Services > Ambulance > Ambulance Requisition",
                "&#9888; Creates a REAL ambulance requisition: select Vehicle Type, search the patient by MRN, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        // The counter matters: DevHIS only builds the navigation menu for an OUTPATIENT cash counter.
        // Letting LoginPage pick the first one offered lands on a menu-less "Transfer / Welcome" shell.
        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceRequisition req =
                new com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceRequisition(page);

        // 1) Navigate to the list/search screen
        boolean onScreen = req.navigateViaMenu();
        step(page, "Open Ambulance Requisition screen",
                "Click Ancillary Services -> Ambulance -> Ambulance Requisition",
                "The Ambulance Requisition list screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) The requisition form lives behind the list screen's Add button.
        boolean formOpen = req.clickAdd();
        step(page, "Click Add", "Click Add (AddAmbulanceRequisition) on the list screen",
                "The Ambulance Requisition form opens",
                formOpen ? "Requisition form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — requisition form not reached"); return; }

        req.describeControls();   // diagnostics only

        // 3) Vehicle Type
        String vehicleType = req.selectVehicleType();
        boolean vtOk = vehicleType != null && !vehicleType.isEmpty() && !vehicleType.startsWith("(");
        step(page, "Select Vehicle Type", "Select a Vehicle Type",
                "A Vehicle Type is selected",
                vtOk ? "Vehicle Type = " + vehicleType : "Vehicle Type NOT selected " + vehicleType,
                vtOk ? "PASS" : "FAIL");

        // 4) MRN + Search
        String searchResult = req.searchByMrn(mrn);
        boolean found = req.patientLoaded();
        step(page, "Enter MRN No. & click Search", "Enter MRN " + mrn + " and click Search (SearchPatientByMRNo)",
                "The patient is found and Patient Name is filled",
                found ? "MRN " + mrn + " -> " + searchResult : "No patient for MRN " + mrn + " (" + searchResult + ")",
                found ? "PASS" : "FAIL");
        if (!found) {
            addSummary("MRN", mrn);
            addSummary("Result", "FAILED — no patient for this MRN (pass a valid one with -Dmrn=...)");
            return;
        }

        // 5) Save -> success toast
        String toast = req.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (fnIUDAmbulanceRequisition); wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient", req.lastPatientName + (req.lastPatientType.isEmpty() ? "" : " (" + req.lastPatientType + ")"));
        addSummary("Vehicle Type", vehicleType);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
