package com.kpj.pages.AncillaryServices_page.Ambulance_page;

import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Ambulance &gt; <b>Ambulance Requisition</b> — Page Object.
 *
 * <p>Flow: <b>Ancillary Services</b> → <b>Ambulance</b> → <b>Ambulance Requisition</b>
 * ({@code #/AmbulanceRequisition}, a search/list grid) → <b>Add</b> ({@code AddAmbulanceRequisition()}) →
 * the requisition form ({@code #/add-AmbulanceRequisition/1}) → select <b>Vehicle Type</b> → enter
 * <b>MRN No.</b> + search → <b>Save</b> → toast.</p>
 *
 * <p><b>Why this extends {@link AmbulanceBooking}.</b> Verified live 2026-08-06: both menu items are the
 * SAME AngularJS controller and the same {@code AmbulanceRequisition.*} model, distinguished only by the
 * mode suffix on the add route — {@code /1} here, {@code /2} for Booking. The difference is what the form
 * renders: Requisition shows a single {@code vehicletypeid} select, while Booking additionally renders the
 * Booking Details block ({@code vehicleid}, {@code driverid1}, {@code driverid2}, {@code doctorid},
 * {@code attendentid}, {@code nurseid}). So navigation, Add, the MRN search (including the OPD/IPD/External
 * scoping) and Save are inherited unchanged; only the menu label and route differ.</p>
 *
 * <p><b>Fields on this form (discovered live):</b> {@code vehicletypeid} (the only select); {@code MRNo},
 * {@code patientname}, {@code callingno}, {@code relativename}, {@code requisitionno},
 * {@code requisitiondatetime}, {@code locationfrom}, {@code locationto}, {@code refname} (inputs);
 * {@code OPDIPD} radios. Buttons: {@code SearchPatientByMRNo()}, {@code fnIUDAmbulanceRequisition()} (Save),
 * {@code CancelForm()} (Back).</p>
 *
 * <p><b>Login note.</b> Like every menu-driven screen, this needs a session with a navigation menu — log in
 * with an outpatient counter, e.g. {@code login(BASE, USER, PASS, "OPD-B-01")}.</p>
 */
public class AmbulanceRequisition extends AmbulanceBooking {

    public AmbulanceRequisition(Page page) { super(page); }

    @Override protected String listRoute()    { return "#/AmbulanceRequisition"; }
    @Override protected String menuLabel()    { return "Ambulance Requisition"; }
    @Override protected String listUrlToken() { return "ambulancerequisition"; }

    /**
     * This form has no Booking Details block, so there is nothing to select here.
     *
     * @throws UnsupportedOperationException always — calling it signals the flow is using the wrong screen.
     */
    @Override
    public String fillBookingDetails() {
        throw new UnsupportedOperationException(
                "Ambulance Requisition has no Booking Details section (no Vehicle / Driver / Doctor selects) — "
                        + "use AmbulanceBooking for that flow.");
    }
}
