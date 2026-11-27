package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.pages.AncillaryServices_page.BloodBank_page.BloodBankListScreen;
import com.microsoft.playwright.Page;

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Red Cell Serology</b> — list, Search, Add form and its refusals (nothing is saved).
 * Steps and checks: see {@link BloodBankListFlow}. The page object is also named RedCellSerology — referenced by its fully-qualified name.
 */
public class RedCellSerology extends BloodBankListFlow {

    public RedCellSerology() { super("AncillaryServices_BloodBank_RedCellSerology"); }

    public static void main(String[] args) {
        RedCellSerology t = new RedCellSerology();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override protected BloodBankListScreen newScreen(Page page) { return new com.kpj.pages.AncillaryServices_page.BloodBank_page.RedCellSerology(page); }
    @Override protected String title() { return "Ancillary Services - Blood Bank - Red Cell Serology"; }
    @Override protected String path() { return "Ancillary Services > Blood Bank > Red Cell Serology"; }
    @Override protected String[] listHeadings() { return new String[]{"Blood Bag No.", "Bag Type", "Donor Name", "Blood Group", "Serology Date", "Status", "Edit"}; }
    @Override protected String[] addFormModels() {
        return new String[]{"bloodbagno", "donorname", "redcellsrologydatetime", "bloodgroupid", "SelectedChkbox1", "SelectedChkbox2",
                "inregularantibodies", "hemolysintest", "btoid", "technicianid"};
    }
    @Override protected String[][] validations() { return new String[][]{{"Save", "Please Select Donor"}}; }
    @Override protected String manualText() { return "Search a collected blood bag by its number, enter the serology result, Save, and find it in the list"; }
}
