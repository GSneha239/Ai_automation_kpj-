package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Blood Bag Component Extraction</b> ({@code #/BloodBagComponentExtractionList}) — Page Object.
 *
 * <p>List: From Date / To Date / Blood Bag No. ({@code NewBloodbagcomponent.fromdate / todate / bloodbagno}), <b>Search</b>
 * ({@code SearchBloodBagComponent()}), <b>Add</b>; grid Blood Bag No. | Extracted Blood Bag No. | Bag Type | Donor Name | Blood Group |
 * Component Name | Extracted Date | Extracted Time | Edit. Add form ({@code #/add-BloodBagComponentExtraction}): Collected Bag No. +
 * Bag Type + <b>Search</b> ({@code SearchBtnClick();}), donor name, blood group, extracted date / time, bag capacity, Accept / Reject
 * (reason), then the extracted components (bag no., component, weight, technician, BTO, expiry, remarks, <b>Add</b>) and
 * <b>Submit</b> ({@code IUDBtnClick();}) / <b>Back</b>. With nothing entered Add says "Please Enter Bag No!", Reject "Please Select
 * Reason!" and Submit "Please Select Donor!" — all without calling the server.</p>
 */
public class BloodBagComponentExtraction extends BloodBankListScreen {

    public BloodBagComponentExtraction(Page page) { super(page); }

    @Override protected String leafText() { return "Blood Bag Component Extraction"; }
    @Override protected String listHash() { return "#/bloodbagcomponentextractionlist"; }
    @Override protected String addHash() { return "#/add-bloodbagcomponentextraction"; }
    @Override protected String filterModel() { return "NewBloodbagcomponent"; }
    @Override protected String searchCall() { return "SearchBloodBagComponent()"; }
    @Override protected String addFormBagModel() { return "BloodBagComponent.CollectedBagNo"; }
    @Override protected String addFormSearchCall() { return "SearchBtnClick();"; }
}
