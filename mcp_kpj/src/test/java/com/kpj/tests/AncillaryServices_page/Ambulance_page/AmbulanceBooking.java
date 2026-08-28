package com.kpj.tests.AncillaryServices_page.Ambulance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AmbulanceBooking — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Booking</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Booking</b>.</li>
 *   <li>Select <b>Vehicle Type</b>.</li>
 *   <li>Enter <b>MRN No.</b> and click <b>Search</b>.</li>
 *   <li>In <b>Booking Details</b> select <b>Vehicle</b>, <b>Driver1</b> and <b>Doctor</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Pass one with {@code -Dmrn=100000956}; without it the flow uses {@link #DEFAULT_MRN}.
 * The MRN must exist in the target environment — if the search returns nothing the flow reports that
 * step as FAIL and stops rather than saving a booking against no patient.</p>
 *
 * <p>&#9888; A successful run CREATES a real ambulance booking in the target environment.</p>
 */
public class AmbulanceBooking extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. Override per environment. */
    public static final String DEFAULT_MRN = "100000956";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AmbulanceBooking() { super("AncillaryServices_Ambulance_AmbulanceBooking"); }

    public static void main(String[] args) {
        AmbulanceBooking t = new AmbulanceBooking();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ambulance Booking", "Ancillary Services > Ambulance > Ambulance Booking",
                "&#9888; Creates a REAL ambulance booking: select Vehicle Type, search the patient by MRN, "
                        + "select Vehicle + Driver1 + Doctor in Booking Details, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        // The counter matters: DevHIS only builds the navigation menu for an OUTPATIENT cash counter. Letting
        // LoginPage pick the first one offered lands on a menu-less "Transfer / Welcome" shell with no
        // Ancillary Services menu at all, so this flow asks for an OPD counter explicitly.
        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        // Harvest a broader pool of known-live patients from Billing > OPD Bill's patient-search popup —
        // a different screen already known to carry real patients today's OP visits. This is a LAST-RESORT
        // fallback source (tried after this screen's own booking history below), since a patient from OPD
        // Bill is not guaranteed to satisfy whatever precondition Ambulance Booking enforces.
        java.util.List<String> broadCandidates = java.util.Collections.emptyList();
        if (System.getProperty("mrn") == null) {
            com.kpj.pages.Billing_page.OpdBill opb = new com.kpj.pages.Billing_page.OpdBill(page);
            if (opb.navigateTo(BASE)) broadCandidates = opb.candidateOpdMrnsFromPopup(8);
        }

        com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceBooking booking =
                new com.kpj.pages.AncillaryServices_page.Ambulance_page.AmbulanceBooking(page);

        // 1) Navigate to the list/search screen
        boolean onScreen = booking.navigateViaMenu();
        step(page, "Open Ambulance Booking screen",
                "Click Ancillary Services -> Ambulance -> Ambulance Booking",
                "The Ambulance Booking list screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // Harvest fallback candidate MRNs from this screen's own list while it's still on view — those
        // patients were already accepted by a prior real booking, so they are known to resolve. Tried
        // before the broader OPD Bill pool above. Only used if the pinned/default MRN turns out not to
        // exist in this environment (see step 3 below).
        java.util.List<String> mrnCandidates = new java.util.ArrayList<>();
        if (System.getProperty("mrn") == null) mrnCandidates.addAll(booking.harvestMrnsFromList());
        for (String c : broadCandidates) if (!mrnCandidates.contains(c)) mrnCandidates.add(c);

        // 2) The booking form lives behind the list screen's Add button.
        boolean formOpen = booking.clickAdd();
        step(page, "Click Add", "Click Add (AddAmbulanceRequisition) on the list screen",
                "The Ambulance Booking form opens",
                formOpen ? "Booking form opened (" + page.url() + ")" : "Booking form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — booking form not reached"); return; }

        booking.describeControls();   // diagnostics only

        // 2) Vehicle Type
        String vehicleType = booking.selectVehicleType();
        boolean vtOk = vehicleType != null && !vehicleType.isEmpty() && !vehicleType.startsWith("(");
        step(page, "Select Vehicle Type", "Select a Vehicle Type",
                "A Vehicle Type is selected",
                vtOk ? "Vehicle Type = " + vehicleType : "Vehicle Type NOT selected " + vehicleType,
                vtOk ? "PASS" : "FAIL");

        // 3) MRN + Search — if the pinned/default MRN isn't a patient in this environment, fall back to
        // the candidates harvested from the list above, trying each in turn until one resolves.
        String searchResult = booking.searchByMrn(mrn);
        boolean found = booking.patientLoaded();
        java.util.List<String> tried = new java.util.ArrayList<>();
        tried.add(mrn);
        if (!found && !mrnCandidates.isEmpty()) {
            for (String candidate : mrnCandidates) {
                if (candidate.equals(mrn)) continue;
                searchResult = booking.searchByMrn(candidate);
                found = booking.patientLoaded();
                tried.add(candidate);
                if (found) { mrn = candidate; break; }
            }
        }
        step(page, "Enter MRN No. & click Search", "Enter MRN " + mrn + " and click Search (SearchPatientByMRNo)",
                "The patient is found and Patient Name is filled",
                found ? "MRN " + mrn + " -> " + searchResult
                      : "No patient found among " + tried.size() + " MRN(s) tried: " + tried + " (" + searchResult + ")",
                found ? "PASS" : "FAIL");
        if (!found) {
            addSummary("MRN", mrn);
            addSummary("MRNs tried", tried.toString());
            addSummary("Result", "FAILED — no patient for any tried MRN (pass a valid one with -Dmrn=...)");
            return;
        }

        // 4) Booking Details — Vehicle, Driver1, Doctor
        String details = booking.fillBookingDetails();
        boolean detailsOk = booking.bookingDetailsComplete();
        step(page, "Select Vehicle, Driver1 and Doctor",
                "In Booking Details select Vehicle, Driver1 and Doctor",
                "Vehicle, Driver1 and Doctor are all selected", details,
                detailsOk ? "PASS" : "FAIL");

        // 5) Save -> success toast
        String toast = booking.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("booked") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully.' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("MRN source", tried.size() > 1 ? "harvested from the list (after " + (tried.size() - 1) + " miss(es))"
                : (System.getProperty("mrn") == null ? "default" : "-Dmrn"));
        addSummary("Vehicle Type", vehicleType);
        addSummary("Vehicle", booking.lastVehicle);
        addSummary("Driver1", booking.lastDriver1);
        addSummary("Doctor", booking.lastDoctor);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
