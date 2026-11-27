package com.kpj.pages.AncillaryServices_page.BloodBank_page;

import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Red Cell Serology</b> ({@code #/RedCellSerologyList}) — Page Object.
 *
 * <p>List: From Date / To Date / Blood Bag No. ({@code RedCellSerology.fromdate / todate / bloodbagno}), <b>Search</b>
 * ({@code GetRedCellSerologyForGrid()}), <b>Add</b>; grid Blood Bag No. | Bag Type | Donor Name | Blood Group | Serology Date | Status | Edit.
 * Add form ({@code #/add-RedCellSerology}): Blood Bag No. + <b>Search</b> ({@code searchserologydetails()}, which finds the donor's bag
 * by its exact number), Donor Name, Serology Date, Blood Group, two result checkboxes, irregular antibodies, haemolysin test, BTO and
 * Technician, then <b>Save</b> ({@code fnIUD()}) / <b>Back</b>. Save with no donor says "Please Select Donor!" without calling the server.</p>
 */
public class RedCellSerology extends BloodBankListScreen {

    public RedCellSerology(Page page) { super(page); }

    @Override protected String leafText() { return "Red Cell Serology"; }
    @Override protected String listHash() { return "#/redcellserologylist"; }
    @Override protected String addHash() { return "#/add-redcellserology"; }
    @Override protected String filterModel() { return "RedCellSerology"; }
    @Override protected String searchCall() { return "GetRedCellSerologyForGrid()"; }
    @Override protected String addFormBagModel() { return "RedCellSerology.bloodbagno"; }
    @Override protected String addFormSearchCall() { return "searchserologydetails()"; }
}
