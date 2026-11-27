package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBankListScreen;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Blood Bag Component Extraction</b> — list, Search, Add form and its refusals (nothing is saved).
 * Steps and checks: see {@link BloodBankListFlow}. The page object is also named BloodBagComponentExtraction — referenced by its fully-qualified name.
 */
public class BloodBagComponentExtraction extends BloodBankListFlow {

    public BloodBagComponentExtraction() { super("AncillaryServices_BloodBank_BloodBagComponentExtraction"); }

    public static void main(String[] args) {
        BloodBagComponentExtraction t = new BloodBagComponentExtraction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override protected BloodBankListScreen newScreen(Page page) { return new com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBagComponentExtraction(page); }
    @Override protected String title() { return "Ancillary Services - Blood Bank - Blood Bag Component Extraction"; }
    @Override protected String path() { return "Ancillary Services > Blood Bank > Blood Bag Component Extraction"; }
    @Override protected String[] listHeadings() {
        return new String[]{"Blood Bag No.", "Extracted Blood Bag No.", "Bag Type", "Donor Name", "Blood Group", "Component Name", "Extracted Date", "Extracted Time", "Edit"};
    }
    @Override protected String[] addFormModels() {
        return new String[]{"CollectedBagNo", "BloodBagTypeId", "FirstName", "MiddleName", "LastName", "BloodGroupID", "ExtractedDate", "BagCapacity", "radio",
                "ReasonID", "extractedbagno", "bloodcomponentid", "bagweight", "technicianid", "btoid", "expirydate", "remarks"};
    }
    @Override protected String[][] validations() {
        return new String[][]{{"Add", "Please Enter Bag No"}, {"Reject", "Please Select Reason"}, {"Submit", "Please Select Donor"}};
    }
    @Override protected String manualText() { return "Search a collected blood bag by its number, accept it, add the extracted components, Submit, and find it in the list"; }
}
