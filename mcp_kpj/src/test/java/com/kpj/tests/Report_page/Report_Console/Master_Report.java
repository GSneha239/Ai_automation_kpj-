package com.kpj.tests.Report_page.Report_Console;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Report &gt; Report Console &gt; <b>Master Report</b> - one section ("tab") per report of the Reports dropdown, each built from a recording of a user's own steps.
 *
 * <p><b>How a Master report differs from the Investigation Reports:</b> Show Report does not open a PDF tab - the console builds the report in the browser and the browser <b>downloads</b> it (a CSV). Every section ends with four
 * verdicts on that file: it was generated (a non-empty {@code .csv} was downloaded), it has no error (a download happened, it is data and not an error page / the console's "No Data Available!"), it is the report that was added
 * (its file name / first line say so) and it lists records (at least one data row under its header) - otherwise FAIL. The file is saved in {@code <outdir>/downloads/MasterReport/} and its first lines are in the test report.
 * <b>Evidence:</b> the screenshot of the "shows records" step is the downloaded CSV shown as a table (long files: the first 40 and last 5 rows); the same picture is saved next to the CSV as {@code .png}.</p>
 *
 * <ol>
 *   <li><b>Admission - Admission Type</b> (Master Report &gt; "Admission - Admission Type"; the steps of a recording): filter Location (one, {@code loc.IsSelected}); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Amenities</b> (Master Report &gt; "Admission - Bed Amenities"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Class</b> (Master Report &gt; "Admission - Bed Class"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Details</b> (Master Report &gt; "Admission - Bed Details"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Bed Release Checklist</b> (Master Report &gt; "Admission - Bed Release Checklist"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Discharge Destination</b> (Master Report &gt; "Admission - Discharge Destination"; the steps of a recording - asked for as "Discharge Checklist", the recording chose this neighbouring entry): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Discharge Checklist</b> (Master Report &gt; "Admission - Discharge Checklist"; asked for by the user, derived from the Discharge Destination recording - same steps, the report offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Discharge Type</b> (Master Report &gt; "Admission - Discharge Type"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Floor</b> (Master Report &gt; "Admission - Floor"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Room</b> (Master Report &gt; "Admission - Room", not "Room Amenities"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Room Amenities</b> (Master Report &gt; "Admission - Room Amenities"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Admission - Ward</b> (Master Report &gt; "Admission - Ward"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Bill &amp; Receipt Cancellation Reason</b> (Master Report &gt; "Billing - Bill &amp; Receipt Cancellation Reason"; the steps of a recording): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Code Type</b> (Master Report &gt; "Billing - Code Type"; not separately recorded - a probe showed it offers only the Location filter, so the recorded steps of the other Master tabs apply): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Concession Discount Reason</b> (Master Report &gt; "Billing - Concession Discount Reason"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Cost Center</b> (Master Report &gt; "Billing - Cost Center"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - CreditNote Reason</b> (Master Report &gt; "Billing - CreditNote Reason"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Customer Type</b> (Master Report &gt; "Billing - Customer Type"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Deposit Against (Deposit Receipt Against)</b> (Master Report &gt; "Billing - Deposit Against (Deposit Receipt Against)"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Discount Policy</b> (Master Report &gt; "Billing - Discount Policy"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Doctor_Fee_MMA13</b> (Master Report &gt; "Billing - Doctor_Fee_MMA13"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - DoctorShare</b> (Master Report &gt; "Billing - DoctorShare"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Group</b> (Master Report &gt; "Billing - Group"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Procedure Master</b> (Master Report &gt; "Billing - Procedure Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - ProcedureType</b> (Master Report &gt; "Billing - ProcedureType"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Service</b> (Master Report &gt; "Billing - Service"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Service-CodeType Integration</b> (Master Report &gt; "Billing - Service-CodeType Integration"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Status Tag</b> (Master Report &gt; "Billing - Status Tag"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Store</b> (Master Report &gt; "Billing - Store"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Sub Service Master</b> (Master Report &gt; "Billing - Sub Service Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Billing - Subgroup</b> (Master Report &gt; "Billing - Subgroup"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Laboratory - Lab Profile</b> (Master Report &gt; "Clinical-Laboratory - Lab Profile"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Laboratory - Lab Sample</b> (Master Report &gt; "Clinical-Laboratory - Lab Sample"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-MRD - Diagnosis Master</b> (recorded 2026-11-26: Master Report &gt; "Clinical-MRD - Diagnosis Master"): filter Location; Add; Add Value; tick the one location (id 1); OK; Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Nursing - Allergy</b> (Master Report &gt; "Clinical-Nursing - Allergy"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Nursing - Triage</b> (Master Report &gt; "Clinical-Nursing - Triage"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Nursing - Vital Sign</b> (Master Report &gt; "Clinical-Nursing - Vital Sign"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Allergy Severity</b> (recorded 2026-11-27: Report &gt; Report Console &gt; Master Report &gt; "Clinical-Pharmacy - Allergy Severity"): filter Location; Add; Add Value; tick the one location (id 1); OK; Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Dosage Master</b> (Master Report &gt; "Clinical-Pharmacy - Dosage Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Dose Frequency</b> (Master Report &gt; "Clinical-Pharmacy - Dose Frequency"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Drug Instruction</b> (Master Report &gt; "Clinical-Pharmacy - Drug Instruction"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Prescription Type</b> (Master Report &gt; "Clinical-Pharmacy - Prescription Type"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Clinical-Pharmacy - Route of Administration</b> (Master Report &gt; "Clinical-Pharmacy - Route of Administration"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Finance - Doctor Master</b> (Master Report &gt; "Finance - Doctor Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Finance - Employee Master</b> (Master Report &gt; "Finance - Employee Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records (private columns are hidden in everything saved).</li>
 *   <li><b>Finance - Package Master</b> (Master Report &gt; "Finance - Package Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Finance - Payer Master</b> (Master Report &gt; "Finance - Payer Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records (private columns are hidden in everything saved).</li>
 *   <li><b>Finance - Service Master Report</b> (Master Report &gt; "Finance - Service Master Report"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Finance - Supplier Master Report</b> (Master Report &gt; "Finance - Supplier Master Report"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records (private columns are hidden in everything saved).</li>
 *   <li><b>Finance - Tariff Discount Policy Master</b> (Master Report &gt; "Finance - Tariff Discount Policy Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General -Item Master -Unit Master</b> (Master Report &gt; "General -Item Master -Unit Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Drug Class</b> (Master Report &gt; "General-Item Master - Drug Class"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Drug Item Category</b> (Master Report &gt; "General-Item Master - Drug Item Category"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Generic Master</b> (Master Report &gt; "General-Item Master - Generic Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Item Class</b> (Master Report &gt; "General-Item Master - Item Class"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Rack</b> (Master Report &gt; "General-Item Master - Rack"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Item Master - Shelf (Cell)</b> (Master Report &gt; "General-Item Master - Shelf (Cell)"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - Blood Group</b> (Master Report &gt; "General-Masters - Blood Group"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - City</b> (Master Report &gt; "General-Masters - City"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - Country</b> (Master Report &gt; "General-Masters - Country"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - Designation</b> (Master Report &gt; "General-Masters - Designation"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - Gender</b> (Master Report &gt; "General-Masters - Gender"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - Postcode</b> (Master Report &gt; "General-Masters - Postcode"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>General-Masters - State</b> (Master Report &gt; "General-Masters - State"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Item Markup Policy Master</b> (Master Report &gt; "Item Markup Policy Master"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Item Master-Item (All)</b> (Master Report &gt; "Item Master-Item (All)"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Location - Bank</b> (Master Report &gt; "Location - Bank"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 *   <li><b>Location - Cash Counter</b> (Master Report &gt; "Location - Cash Counter"; not separately recorded - a probe showed it offers only the Location filter): filter Location (one); Show Report; a CSV must be downloaded and list records.</li>
 * </ol>
 *
 * <p>Each filter: choose it in the Filter dropdown, <b>Add</b>, verify it appears in the Filter Name | Filter Value table, <b>Add Value</b> (its popup), tick, OK, verify the Filter Value. Nothing is saved in the application.</p>
 *
 * <p>Options: {@code -Dmst.only=admissiontype|bedamenities|bedclass|beddetails|bedrelease|dischargedestination|dischargechecklist|dischargetype|floor|room|roomamenities|ward|billcancel|codetype|concession|costcenter|creditnote|customertype|depositagainst|discountpolicy|doctorfee|doctorshare|group|proceduremaster|proceduretype|service|servicecodetype|statustag|store|subservice|subgroup|labprofile|labsample|diagnosismaster|nursingallergy|triage|vitalsign|allergyseverity|dosagemaster|dosefrequency|druginstruction|prescriptiontype|routeofadmin|doctormaster|employeemaster|packagemaster|payermaster|servicemaster|suppliermaster|tariffdiscount|unitmaster|drugclass|drugitemcategory|genericmaster|itemclass|rack|shelf|bloodgroup|city|country|designation|gender|postcode|state|itemmarkup|itemall|bank|cashcounter} (run one section; its own report {@code ReportConsole_MasterReport_<Section>.html}, no option = every section = {@code ReportConsole_MasterReport.html}),
 * {@code -Dmst.at.report=} / {@code -Dmst.at.header=}, {@code -Dmst.ba.*}, {@code -Dmst.bc.*}, {@code -Dmst.bd.*}, {@code -Dmst.br.*}, {@code -Dmst.dd.*}, {@code -Dmst.dc.*}, {@code -Dmst.dt.*}, {@code -Dmst.fl.*}, {@code -Dmst.rm.*}, {@code -Dmst.ra.*}, {@code -Dmst.wd.*}, {@code -Dmst.bcr.*}, {@code -Dmst.bct.*}, {@code -Dmst.bcd.*}, {@code -Dmst.bcc.*}, {@code -Dmst.bcn.*}, {@code -Dmst.bcu.*}, {@code -Dmst.bda.*}, {@code -Dmst.bdp.*}, {@code -Dmst.bdf.*}, {@code -Dmst.bds.*}, {@code -Dmst.bg.*}, {@code -Dmst.bpm.*}, {@code -Dmst.bpt.*}, {@code -Dmst.bsv.*}, {@code -Dmst.bsc.*}, {@code -Dmst.bst.*}, {@code -Dmst.bsto.*}, {@code -Dmst.bssm.*}, {@code -Dmst.bsg.*}, {@code -Dmst.clp.*}, {@code -Dmst.cls.*}, {@code -Dmst.cdm.*}, {@code -Dmst.cna.*}, {@code -Dmst.cnt.*}, {@code -Dmst.cnv.*}, {@code -Dmst.cpas.*}, {@code -Dmst.cpdm.*}, {@code -Dmst.cpdf.*}, {@code -Dmst.cpdi.*}, {@code -Dmst.cppt.*}, {@code -Dmst.cpra.*}, {@code -Dmst.fdm.*}, {@code -Dmst.fem.*}, {@code -Dmst.fpm.*}, {@code -Dmst.fpy.*}, {@code -Dmst.fsm.*}, {@code -Dmst.fsp.*}, {@code -Dmst.ftd.*}, {@code -Dmst.gum.*}, {@code -Dmst.gdc.*}, {@code -Dmst.gdic.*}, {@code -Dmst.ggm.*}, {@code -Dmst.gic.*}, {@code -Dmst.grk.*}, {@code -Dmst.gsh.*}, {@code -Dmst.gbg.*}, {@code -Dmst.gct.*}, {@code -Dmst.gcn.*}, {@code -Dmst.gdg.*}, {@code -Dmst.ggn.*}, {@code -Dmst.gpc.*}, {@code -Dmst.gst.*}, {@code -Dmst.imp.*}, {@code -Dmst.ima.*}, {@code -Dmst.lbk.*} and {@code -Dmst.lcc.*} (the same two options for the other sections; regexes: the file name / first line must match the report, the first line must match the header; defaults in the section).</p>
 */
public class Master_Report extends DevHisBase {

    /** The sections ("tabs") in the order they run: -Dmst.only value, report id suffix, title. */
    private static final String[][] SECTIONS = {
            {"admissiontype", "AdmissionAdmissionType", "Admission - Admission Type"},
            {"bedamenities", "AdmissionBedAmenities", "Admission - Bed Amenities"},
            {"bedclass", "AdmissionBedClass", "Admission - Bed Class"},
            {"beddetails", "AdmissionBedDetails", "Admission - Bed Details"},
            {"bedrelease", "AdmissionBedReleaseChecklist", "Admission - Bed Release Checklist"},
            {"dischargedestination", "AdmissionDischargeDestination", "Admission - Discharge Destination"},
            {"dischargechecklist", "AdmissionDischargeChecklist", "Admission - Discharge Checklist"},
            {"dischargetype", "AdmissionDischargeType", "Admission - Discharge Type"},
            {"floor", "AdmissionFloor", "Admission - Floor"},
            {"room", "AdmissionRoom", "Admission - Room"},
            {"roomamenities", "AdmissionRoomAmenities", "Admission - Room Amenities"},
            {"ward", "AdmissionWard", "Admission - Ward"},
            {"billcancel", "BillingBillReceiptCancellationReason", "Billing - Bill & Receipt Cancellation Reason"},
            {"codetype", "BillingCodeType", "Billing - Code Type"},
            {"concession", "BillingConcessionDiscountReason", "Billing - Concession Discount Reason"},
            {"costcenter", "BillingCostCenter", "Billing - Cost Center"},
            {"creditnote", "BillingCreditNoteReason", "Billing - CreditNote Reason"},
            {"customertype", "BillingCustomerType", "Billing - Customer Type"},
            {"depositagainst", "BillingDepositAgainst", "Billing - Deposit Against (Deposit Receipt Against)"},
            {"discountpolicy", "BillingDiscountPolicy", "Billing - Discount Policy"},
            {"doctorfee", "BillingDoctorFeeMMA13", "Billing - Doctor_Fee_MMA13"},
            {"doctorshare", "BillingDoctorShare", "Billing - DoctorShare"},
            {"group", "BillingGroup", "Billing - Group"},
            {"proceduremaster", "BillingProcedureMaster", "Billing - Procedure Master"},
            {"proceduretype", "BillingProcedureType", "Billing - ProcedureType"},
            {"service", "BillingService", "Billing - Service"},
            {"servicecodetype", "BillingServiceCodeTypeIntegration", "Billing - Service-CodeType Integration"},
            {"statustag", "BillingStatusTag", "Billing - Status Tag"},
            {"store", "BillingStore", "Billing - Store"},
            {"subservice", "BillingSubServiceMaster", "Billing - Sub Service Master"},
            {"subgroup", "BillingSubgroup", "Billing - Subgroup"},
            {"labprofile", "ClinicalLaboratoryLabProfile", "Clinical-Laboratory - Lab Profile"},
            {"labsample", "ClinicalLaboratoryLabSample", "Clinical-Laboratory - Lab Sample"},
            {"diagnosismaster", "ClinicalMrdDiagnosisMaster", "Clinical-MRD - Diagnosis Master"},
            {"nursingallergy", "ClinicalNursingAllergy", "Clinical-Nursing - Allergy"},
            {"triage", "ClinicalNursingTriage", "Clinical-Nursing - Triage"},
            {"vitalsign", "ClinicalNursingVitalSign", "Clinical-Nursing - Vital Sign"},
            {"allergyseverity", "ClinicalPharmacyAllergySeverity", "Clinical-Pharmacy - Allergy Severity"},
            {"dosagemaster", "ClinicalPharmacyDosageMaster", "Clinical-Pharmacy - Dosage Master"},
            {"dosefrequency", "ClinicalPharmacyDoseFrequency", "Clinical-Pharmacy - Dose Frequency"},
            {"druginstruction", "ClinicalPharmacyDrugInstruction", "Clinical-Pharmacy - Drug Instruction"},
            {"prescriptiontype", "ClinicalPharmacyPrescriptionType", "Clinical-Pharmacy - Prescription Type"},
            {"routeofadmin", "ClinicalPharmacyRouteOfAdministration", "Clinical-Pharmacy - Route of Administration"},
            {"doctormaster", "FinanceDoctorMaster", "Finance - Doctor Master"},
            {"employeemaster", "FinanceEmployeeMaster", "Finance - Employee Master"},
            {"packagemaster", "FinancePackageMaster", "Finance - Package Master"},
            {"payermaster", "FinancePayerMaster", "Finance - Payer Master"},
            {"servicemaster", "FinanceServiceMasterReport", "Finance - Service Master Report"},
            {"suppliermaster", "FinanceSupplierMasterReport", "Finance - Supplier Master Report"},
            {"tariffdiscount", "FinanceTariffDiscountPolicyMaster", "Finance - Tariff Discount Policy Master"},
            {"unitmaster", "GeneralItemMasterUnitMaster", "General -Item Master -Unit Master"},
            {"drugclass", "GeneralItemMasterDrugClass", "General-Item Master - Drug Class"},
            {"drugitemcategory", "GeneralItemMasterDrugItemCategory", "General-Item Master - Drug Item Category"},
            {"genericmaster", "GeneralItemMasterGenericMaster", "General-Item Master - Generic Master"},
            {"itemclass", "GeneralItemMasterItemClass", "General-Item Master - Item Class"},
            {"rack", "GeneralItemMasterRack", "General-Item Master - Rack"},
            {"shelf", "GeneralItemMasterShelfCell", "General-Item Master - Shelf (Cell)"},
            {"bloodgroup", "GeneralMastersBloodGroup", "General-Masters - Blood Group"},
            {"city", "GeneralMastersCity", "General-Masters - City"},
            {"country", "GeneralMastersCountry", "General-Masters - Country"},
            {"designation", "GeneralMastersDesignation", "General-Masters - Designation"},
            {"gender", "GeneralMastersGender", "General-Masters - Gender"},
            {"postcode", "GeneralMastersPostcode", "General-Masters - Postcode"},
            {"state", "GeneralMastersState", "General-Masters - State"},
            {"itemmarkup", "ItemMarkupPolicyMaster", "Item Markup Policy Master"},
            {"itemall", "ItemMasterItemAll", "Item Master-Item (All)"},
            {"bank", "LocationBank", "Location - Bank"},
            {"cashcounter", "LocationCashCounter", "Location - Cash Counter"},
    };

    private static String[] sectionOf(String only) {
        for (String[] s : SECTIONS) if (s[0].equals(only)) return s;
        return null;
    }

    /** One report per section while a tab is being added ({@code -Dmst.only=<name>}); the whole file (no option) writes {@code ReportConsole_MasterReport.html}. */
    private static String sectionReportId() {
        final String base = "ReportConsole_MasterReport";
        String only = System.getProperty("mst.only", "").trim().toLowerCase();
        if (only.isEmpty()) return base;
        String[] s = sectionOf(only);
        return base + "_" + (s != null ? s[1] : only.replaceAll("[^a-z0-9]", ""));
    }

    public Master_Report() { super(sectionReportId()); }

    public static void main(String[] args) {
        Master_Report t = new Master_Report();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** A step whose screenshot is taken here with a 40 s limit (the framework's 10 s one times out now and then on the heavy console screens). */
    private void stepL(String name, String desc, String expected, String actual, String status) {
        byte[] png = null;
        try { png = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { png = null; }
        if (png != null) step(png, name, desc, expected, actual, status);
        else step(name, desc, expected, actual, status);
    }

    @Override
    protected void body() {
        final String only = System.getProperty("mst.only", "").trim().toLowerCase();

        final String[] one = sectionOf(only);
        if (!only.isEmpty() && one != null)
            meta("Report Console - Master Report - " + one[2], "Report > Report Console > Master Report > " + one[2],
                    "ONE TAB ONLY (-Dmst.only=" + only + "): " + one[2] + ". Show Report; the report is downloaded as a CSV: it must be generated, without an error, be the report that was added and list records. Nothing is saved in the application.");
        else
            meta("Report Console - Master Report", "Report > Report Console > Master Report",
                    "Every tab of the Master Report file: " + SECTIONS.length + " section(s) (" + java.util.Arrays.stream(SECTIONS).map(x -> x[2]).collect(java.util.stream.Collectors.joining(", ")) + "). "
                            + "Each: Show Report; the report is downloaded as a CSV: it must be generated, without an error, be the report that was added and list records. Nothing is saved in the application.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Report_page.Report_Console.Master_Report rc = new com.kpj.pages.Report_page.Report_Console.Master_Report(page);

        boolean on = rc.navigateViaMenu();
        stepL("Open Report Console", "Click Report -> Report Console", "The Report Console (Reports + Filter dropdowns, category buttons) is shown",
                on ? "Opened " + page.url() + "; categories: " + rc.categories() : "Did NOT reach the Report Console (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Report Console not reached"); return; }

        if (only.isEmpty() || only.equals("admissiontype")) admissionType(rc);
        if (only.isEmpty() || only.equals("bedamenities")) bedAmenities(rc);
        if (only.isEmpty() || only.equals("bedclass")) bedClass(rc);
        if (only.isEmpty() || only.equals("beddetails")) bedDetails(rc);
        if (only.isEmpty() || only.equals("bedrelease")) bedReleaseChecklist(rc);
        if (only.isEmpty() || only.equals("dischargedestination")) dischargeDestination(rc);
        if (only.isEmpty() || only.equals("dischargechecklist")) dischargeChecklist(rc);
        if (only.isEmpty() || only.equals("dischargetype")) dischargeType(rc);
        if (only.isEmpty() || only.equals("floor")) admissionFloor(rc);
        if (only.isEmpty() || only.equals("room")) admissionRoom(rc);
        if (only.isEmpty() || only.equals("roomamenities")) roomAmenities(rc);
        if (only.isEmpty() || only.equals("ward")) admissionWard(rc);
        if (only.isEmpty() || only.equals("billcancel")) billingCancellationReason(rc);
        if (only.isEmpty() || only.equals("codetype")) billingCodeType(rc);
        if (only.isEmpty() || only.equals("concession")) billingConcessionDiscountReason(rc);
        if (only.isEmpty() || only.equals("costcenter")) billingCostCenter(rc);
        if (only.isEmpty() || only.equals("creditnote")) billingCreditNoteReason(rc);
        if (only.isEmpty() || only.equals("customertype")) billingCustomerType(rc);
        if (only.isEmpty() || only.equals("depositagainst")) billingDepositAgainst(rc);
        if (only.isEmpty() || only.equals("discountpolicy")) billingDiscountPolicy(rc);
        if (only.isEmpty() || only.equals("doctorfee")) billingDoctorFeeMma13(rc);
        if (only.isEmpty() || only.equals("doctorshare")) billingDoctorShare(rc);
        if (only.isEmpty() || only.equals("group")) billingGroup(rc);
        if (only.isEmpty() || only.equals("proceduremaster")) billingProcedureMaster(rc);
        if (only.isEmpty() || only.equals("proceduretype")) billingProcedureType(rc);
        if (only.isEmpty() || only.equals("service")) billingService(rc);
        if (only.isEmpty() || only.equals("servicecodetype")) billingServiceCodeTypeIntegration(rc);
        if (only.isEmpty() || only.equals("statustag")) billingStatusTag(rc);
        if (only.isEmpty() || only.equals("store")) billingStore(rc);
        if (only.isEmpty() || only.equals("subservice")) billingSubServiceMaster(rc);
        if (only.isEmpty() || only.equals("subgroup")) billingSubgroup(rc);
        if (only.isEmpty() || only.equals("labprofile")) clinicalLabProfile(rc);
        if (only.isEmpty() || only.equals("labsample")) clinicalLabSample(rc);
        if (only.isEmpty() || only.equals("diagnosismaster")) clinicalMrdDiagnosisMaster(rc);
        if (only.isEmpty() || only.equals("nursingallergy")) clinicalNursingAllergy(rc);
        if (only.isEmpty() || only.equals("triage")) clinicalNursingTriage(rc);
        if (only.isEmpty() || only.equals("vitalsign")) clinicalNursingVitalSign(rc);
        if (only.isEmpty() || only.equals("allergyseverity")) clinicalPharmacyAllergySeverity(rc);
        if (only.isEmpty() || only.equals("dosagemaster")) clinicalPharmacyDosageMaster(rc);
        if (only.isEmpty() || only.equals("dosefrequency")) clinicalPharmacyDoseFrequency(rc);
        if (only.isEmpty() || only.equals("druginstruction")) clinicalPharmacyDrugInstruction(rc);
        if (only.isEmpty() || only.equals("prescriptiontype")) clinicalPharmacyPrescriptionType(rc);
        if (only.isEmpty() || only.equals("routeofadmin")) clinicalPharmacyRouteOfAdministration(rc);
        if (only.isEmpty() || only.equals("doctormaster")) financeDoctorMaster(rc);
        if (only.isEmpty() || only.equals("employeemaster")) financeEmployeeMaster(rc);
        if (only.isEmpty() || only.equals("packagemaster")) financePackageMaster(rc);
        if (only.isEmpty() || only.equals("payermaster")) financePayerMaster(rc);
        if (only.isEmpty() || only.equals("servicemaster")) financeServiceMasterReport(rc);
        if (only.isEmpty() || only.equals("suppliermaster")) financeSupplierMasterReport(rc);
        if (only.isEmpty() || only.equals("tariffdiscount")) financeTariffDiscountPolicyMaster(rc);
        if (only.isEmpty() || only.equals("unitmaster")) generalItemMasterUnitMaster(rc);
        if (only.isEmpty() || only.equals("drugclass")) generalItemMasterDrugClass(rc);
        if (only.isEmpty() || only.equals("drugitemcategory")) generalItemMasterDrugItemCategory(rc);
        if (only.isEmpty() || only.equals("genericmaster")) generalItemMasterGenericMaster(rc);
        if (only.isEmpty() || only.equals("itemclass")) generalItemMasterItemClass(rc);
        if (only.isEmpty() || only.equals("rack")) generalItemMasterRack(rc);
        if (only.isEmpty() || only.equals("shelf")) generalItemMasterShelfCell(rc);
        if (only.isEmpty() || only.equals("bloodgroup")) generalMastersBloodGroup(rc);
        if (only.isEmpty() || only.equals("city")) generalMastersCity(rc);
        if (only.isEmpty() || only.equals("country")) generalMastersCountry(rc);
        if (only.isEmpty() || only.equals("designation")) generalMastersDesignation(rc);
        if (only.isEmpty() || only.equals("gender")) generalMastersGender(rc);
        if (only.isEmpty() || only.equals("postcode")) generalMastersPostcode(rc);
        if (only.isEmpty() || only.equals("state")) generalMastersState(rc);
        if (only.isEmpty() || only.equals("itemmarkup")) itemMarkupPolicyMaster(rc);
        if (only.isEmpty() || only.equals("itemall")) itemMasterItemAll(rc);
        if (only.isEmpty() || only.equals("bank")) locationBank(rc);
        if (only.isEmpty() || only.equals("cashcounter")) locationCashCounter(rc);
    }

    // =====================================================================================================================================================
    //  Admission - Admission Type (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void admissionType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Admission Type", "ADMISSION TYPE",
                System.getProperty("mst.at.report", "ADMISSION\\W*TYPE"), System.getProperty("mst.at.header", "^\\W*admissiontype\\W*,\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Amenities (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedAmenities(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Amenities", "BED AMENITIES",
                System.getProperty("mst.ba.report", "BED\\W*AMENIT"), System.getProperty("mst.ba.header", "^\\W*bedamenitiesid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Class (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedClass(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Class", "BED CLASS",
                System.getProperty("mst.bc.report", "BED\\W*CLASS"), System.getProperty("mst.bc.header", "^\\W*bedclassid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Details (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedDetails(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Details", "BED DETAILS",
                System.getProperty("mst.bd.report", "BED\\W*DETAIL"), System.getProperty("mst.bd.header", "^\\W*ID\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Ward\\s*,\\s*Bed\\s*Class\\s*,\\s*Room\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Bed Release Checklist (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void bedReleaseChecklist(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Bed Release Checklist", "BED RELEASE CHECKLIST",
                System.getProperty("mst.br.report", "BED\\W*RELEASE"), System.getProperty("mst.br.header", "^\\W*id\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Discharge Destination (recorded: Master Report; Location; Show Report -> a CSV is downloaded).
    //  NOTE: the user asked for "Admission - Discharge Checklist" but the recording chose the neighbouring dropdown entry "Admission - Discharge Destination" (option number:1658) - this is what was recorded.
    // =====================================================================================================================================================

    private void dischargeDestination(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Discharge Destination", "DISCHARGE DESTINATION",
                System.getProperty("mst.dd.report", "DISCHARGE\\W*DESTINATION"), System.getProperty("mst.dd.header", "^\\W*dischargedestinationid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Discharge Checklist (asked for by the user; NOT separately recorded - the recording of the neighbouring "Discharge Destination" has exactly these steps and a probe showed the report offers only the Location filter)
    // =====================================================================================================================================================

    private void dischargeChecklist(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Discharge Checklist", "DISCHARGE CHECKLIST",
                System.getProperty("mst.dc.report", "DISCHARGE\\W*CHECKLIST"), System.getProperty("mst.dc.header", "^\\W*id\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Discharge Type (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void dischargeType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Discharge Type", "DISCHARGE TYPE",
                System.getProperty("mst.dt.report", "DISCHARGE\\W*TYPE"), System.getProperty("mst.dt.header", "^\\W*dischargetypeid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Floor (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void admissionFloor(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Floor", "ADMISSION FLOOR",
                System.getProperty("mst.fl.report", "ADMISSION\\W*FLOOR"), System.getProperty("mst.fl.header", "^\\W*floorid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Room (recorded: Master Report; Location; Show Report -> a CSV is downloaded). The name check excludes the neighbouring "Admission - Room Amenities".
    // =====================================================================================================================================================

    private void admissionRoom(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Room", "ADMISSION ROOM",
                System.getProperty("mst.rm.report", "ADMISSION\\W*ROOM(?!\\W*AMENIT)"), System.getProperty("mst.rm.header", "^\\W*roomid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Room\\s*Type"));
    }

    // =====================================================================================================================================================
    //  Admission - Room Amenities (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void roomAmenities(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Room Amenities", "ROOM AMENITIES",
                System.getProperty("mst.ra.report", "ROOM\\W*AMENIT"), System.getProperty("mst.ra.header", "^\\W*roomamenitiesid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Admission - Ward (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void admissionWard(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Admission - Ward", "ADMISSION WARD",
                System.getProperty("mst.wd.report", "ADMISSION\\W*WARD"), System.getProperty("mst.wd.header", "^\\W*wardid\\W*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Floor\\s*,\\s*Gender\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Bill & Receipt Cancellation Reason (recorded: Master Report; Location; Show Report -> a CSV is downloaded)
    // =====================================================================================================================================================

    private void billingCancellationReason(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Bill & Receipt Cancellation Reason", "BILL & RECEIPT CANCELLATION REASON",
                System.getProperty("mst.bcr.report", "RECEIPT\\W*CANCELLATION\\W*REASON"), System.getProperty("mst.bcr.header", "^\\W*id\\s*,\\s*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Code Type (asked for by the user without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs).
    //  The name check needs "Billing - Code Type", so the neighbouring "Billing - Service-CodeType Integration" cannot pass.
    // =====================================================================================================================================================

    private void billingCodeType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Code Type", "BILLING CODE TYPE",
                System.getProperty("mst.bct.report", "BILLING\\W*CODE\\W*TYPE"), System.getProperty("mst.bct.header", "^\\W*clinicalcodetypeid\\W*,\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Concession Discount Reason (asked for by the user without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingConcessionDiscountReason(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Concession Discount Reason", "BILLING CONCESSION DISCOUNT REASON",
                System.getProperty("mst.bcd.report", "CONCESSION\\W*DISCOUNT\\W*REASON"), System.getProperty("mst.bcd.header", "^\\W*id\\s*,\\s*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Cost Center (asked for by the user without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingCostCenter(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Cost Center", "BILLING COST CENTER",
                System.getProperty("mst.bcc.report", "BILLING\\W*COST\\W*CENT(ER|RE)"), System.getProperty("mst.bcc.header", "^\\W*costcenterid\\W*,\\s*costcentercode\\s*,\\s*costcentername\\s*,\\s*profitcenter\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - CreditNote Reason (asked for by the user without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingCreditNoteReason(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - CreditNote Reason", "BILLING CREDITNOTE REASON",
                System.getProperty("mst.bcn.report", "CREDIT\\W*NOTE\\W*REASON"), System.getProperty("mst.bcn.header", "^\\W*id\\s*,\\s*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Customer Type (asked for by the user without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingCustomerType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Customer Type", "BILLING CUSTOMER TYPE",
                System.getProperty("mst.bcu.report", "CUSTOMER\\W*TYPE"), System.getProperty("mst.bcu.header", "^\\W*Code\\s*,\\s*receivabletype\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Deposit Against (Deposit Receipt Against) (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingDepositAgainst(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        // On 2026-11-26 this report downloaded an EMPTY file (5 bytes: byte-order mark + line break), so its columns have never been seen: the header pattern only asks for SOME comma-separated header line.
        // When the report returns data, replace it with the real columns.
        locationCsvReport(rc, "Billing - Deposit Against (Deposit Receipt Against)", "BILLING DEPOSIT AGAINST",
                System.getProperty("mst.bda.report", "DEPOSIT\\W*AGAINST"), System.getProperty("mst.bda.header", "^\\W*[A-Za-z][^,]*,"));
    }

    // =====================================================================================================================================================
    //  Billing - Discount Policy (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingDiscountPolicy(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Discount Policy", "BILLING DISCOUNT POLICY",
                System.getProperty("mst.bdp.report", "DISCOUNT\\W*POLICY"), System.getProperty("mst.bdp.header", "^\\W*id\\s*,\\s*Case\\s*Type\\s*,\\s*Discount\\s*Policy\\s*Code\\s*,\\s*Discount\\s*Policy\\s*Name\\s*,\\s*Group\\s*Code\\s*,\\s*Group\\s*Name\\s*,\\s*Subgroup\\s*Code\\s*,\\s*Subgroup\\s*Name\\s*,\\s*Discount\\s*Percentage"));
    }

    // =====================================================================================================================================================
    //  Billing - Doctor_Fee_MMA13 (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingDoctorFeeMma13(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Doctor_Fee_MMA13", "BILLING DOCTOR FEE MMA13",
                System.getProperty("mst.bdf.report", "DOCTOR\\W*FEE\\W*MMA\\W*13"), System.getProperty("mst.bdf.header", "^\\W*id\\s*,\\s*ProcedureCode\\s*,\\s*DoctorFeeCode\\s*,\\s*DoctorFee\\s*,\\s*MMADoctorFeeServiceCode\\s*,\\s*AnesthetistFeeCode\\s*,\\s*AnesthetistFee\\s*,\\s*MMAAnesthetistFeeCode"));
    }

    // =====================================================================================================================================================
    //  Billing - DoctorShare (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingDoctorShare(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - DoctorShare", "BILLING DOCTOR SHARE",
                System.getProperty("mst.bds.report", "DOCTOR\\W*SHARE"), System.getProperty("mst.bds.header", "^\\W*PayableClassificationCode\\s*,\\s*ServiceCode\\s*,\\s*PayableCode\\s*,\\s*SharePercentage\\s*,\\s*ShareAmount\\s*,\\s*BedClassCode\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Group (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingGroup(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Group", "BILLING GROUP",
                System.getProperty("mst.bg.report", "BILLING\\W*GROUP"), System.getProperty("mst.bg.header", "^\\W*groupid\\s*,\\s*Group\\s*Code\\s*,\\s*Group\\s*Name\\s*,\\s*Is\\s*Clinical\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Procedure Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingProcedureMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Procedure Master", "BILLING PROCEDURE MASTER",
                System.getProperty("mst.bpm.report", "PROCEDURE\\W*MASTER"), System.getProperty("mst.bpm.header", "^\\W*Procedure\\s*Id\\s*,\\s*Procedure\\s*Code\\s*,\\s*Procedure\\s*Description\\s*,\\s*Service\\s*Item\\s*Code\\s*,\\s*Duration\\s*\\(\\s*Mins\\s*\\)\\s*,\\s*Procedure\\s*Type\\s*,\\s*Remark\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - ProcedureType (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingProcedureType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - ProcedureType", "BILLING PROCEDURE TYPE",
                System.getProperty("mst.bpt.report", "PROCEDURE\\W*TYPE"), System.getProperty("mst.bpt.header", "^\\W*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Service (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingService(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        // the report check must not accept the neighbouring "Billing - Service-CodeType Integration"
        locationCsvReport(rc, "Billing - Service", "BILLING SERVICE",
                System.getProperty("mst.bsv.report", "BILLING\\W*SERVICE(?!\\W*CODE)"), System.getProperty("mst.bsv.header", "^\\W*serviceitemid\\s*,\\s*Service\\s*Code\\s*,\\s*Service\\s*Name\\s*,\\s*Service\\s*Name\\s*Label\\s*,\\s*Group\\s*Name\\s*,\\s*Sub\\s*Group\\s*Name\\s*,\\s*Short\\s*Description\\s*,"
                        + "\\s*Rate\\s*Editable\\s*,\\s*Emergency\\s*Applicable\\s*,\\s*Discount\\s*Applicable\\s*,\\s*Min\\s*Rate\\s*,\\s*Max\\s*Rate\\s*,\\s*Emergency\\s*Charge\\s*Percentage\\s*,\\s*Discount\\s*Percentage\\s*,\\s*Rate\\s*\\(OPD\\)\\s*,\\s*Rate\\s*\\(IPD\\)\\s*,"
                        + "\\s*Service\\s*Type\\s*,\\s*Can\\s*be\\s*part\\s*of\\s*Package\\s*,\\s*Is\\s*Package\\s*,\\s*Package\\s*\\(IPD\\)\\s*,\\s*Package\\s*\\(OPD\\)\\s*,\\s*Is\\s*OT\\s*Procedure\\s*,\\s*Is\\s*Doctor\\s*Share\\s*Applicable\\s*,\\s*Share\\s*%\\s*,"
                        + "\\s*E-Invoice\\s*Classification\\s*Code\\s*,\\s*Cost\\s*Center\\s*,\\s*Service\\s*Tax\\s*Applicability\\s*\\(Non\\s*Citizen\\)\\s*,\\s*Service\\s*Tax\\s*%\\s*\\(Non\\s*Citizen\\)"));
    }

    // =====================================================================================================================================================
    //  Billing - Service-CodeType Integration (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingServiceCodeTypeIntegration(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Service-CodeType Integration", "BILLING SERVICE CODETYPE INTEGRATION",
                System.getProperty("mst.bsc.report", "SERVICE\\W*CODE\\W*TYPE\\W*INTEGRATION"), System.getProperty("mst.bsc.header", "^\\W*codeid\\s*,\\s*Service\\s*Item\\s*Code\\s*,\\s*TC-ServiceCode\\s*\\(Integration\\)\\s*,\\s*Clinical\\s*Code\\s*Type\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Status Tag (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingStatusTag(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Status Tag", "BILLING STATUS TAG",
                System.getProperty("mst.bst.report", "STATUS\\W*TAG"), System.getProperty("mst.bst.header", "^\\W*billstatusid\\s*,\\s*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Store (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingStore(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Store", "BILLING STORE",
                System.getProperty("mst.bsto.report", "BILLING\\W*STORE"), System.getProperty("mst.bsto.header", "^\\W*id\\s*,\\s*Store\\s*Code\\s*,\\s*Store\\s*Name\\s*,\\s*Cost\\s*Center\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Billing - Sub Service Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingSubServiceMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Sub Service Master", "BILLING SUB SERVICE MASTER",
                System.getProperty("mst.bssm.report", "SUB\\W*SERVICE\\W*MASTER"), System.getProperty("mst.bssm.header", "^\\W*Sub\\s*Service\\s*ID\\s*,\\s*Parent\\s*Service\\s*Code\\s*,\\s*Parent\\s*Service\\s*Description\\s*,\\s*Sub\\s*Service\\s*Code\\s*,\\s*Sub\\s*Service\\s*Description\\s*,\\s*Is\\s*Selected"));
    }

    // =====================================================================================================================================================
    //  Billing - Subgroup (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void billingSubgroup(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Billing - Subgroup", "BILLING SUBGROUP",
                System.getProperty("mst.bsg.report", "BILLING\\W*SUB\\W*GROUP"), System.getProperty("mst.bsg.header", "^\\W*subgroupid\\s*,\\s*Subgroup\\s*Code\\s*,\\s*Description\\s*,\\s*Group\\s*Name\\s*,\\s*Cost\\s*Center\\s*,\\s*GL\\s*Code\\s*OP\\s*,\\s*GL\\s*Code\\s*IP\\s*,"
                        + "\\s*GL\\s*Code\\s*OP\\s*Discount\\s*,\\s*GL\\s*Code\\s*IP\\s*Discount\\s*,\\s*Inventory\\s*GL\\s*Code\\s*,\\s*COGS\\s*GL\\s*Code\\s*,\\s*COGS\\s*Write\\s*Off\\s*GL\\s*Code\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Laboratory - Lab Profile (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalLabProfile(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Laboratory - Lab Profile", "CLINICAL LABORATORY LAB PROFILE",
                System.getProperty("mst.clp.report", "LAB\\W*PROFILE"), System.getProperty("mst.clp.header", "^\\W*id\\s*,\\s*Lab\\s*Profile\\s*Code\\s*,\\s*Profile\\s*Name\\s*,\\s*Service\\s*Code\\s*,\\s*Category\\s*,\\s*Service\\s*/\\s*Item\\s*Name\\s*\\(Included\\s*in\\s*Package\\)\\s*,"
                        + "\\s*Package\\s*Rate\\s*-\\s*OPD\\s*,\\s*Package\\s*Rate\\s*-\\s*IPD\\s*,\\s*OPD\\s*Rate\\s*,\\s*IPD\\s*Rate"));
    }

    // =====================================================================================================================================================
    //  Clinical-Laboratory - Lab Sample (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalLabSample(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Laboratory - Lab Sample", "CLINICAL LABORATORY LAB SAMPLE",
                System.getProperty("mst.cls.report", "LAB\\W*SAMPLE"), System.getProperty("mst.cls.header", "^\\W*id\\s*,\\s*Service\\s*Code\\s*,\\s*Service\\s*Name\\s*,\\s*Test\\s*Name\\s*,\\s*Sample\\s*Name\\s*,\\s*Volume\\s*,\\s*Volume\\s*Unit\\s*,\\s*Frequency\\s*,\\s*TAT\\s*,\\s*Notify\\s*TAT\\s*,"
                        + "\\s*Container\\s*,\\s*Authorization\\s*Level\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-MRD - Diagnosis Master (recorded from the user's own actions, 2026-11-26: Master Report > report > filter Location > Add > Add Value > tick location 1 > OK > Show Report > CSV download)
    // =====================================================================================================================================================

    private void clinicalMrdDiagnosisMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-MRD - Diagnosis Master", "CLINICAL MRD DIAGNOSIS MASTER",
                System.getProperty("mst.cdm.report", "DIAGNOSIS\\W*MASTER"), System.getProperty("mst.cdm.header", "^\\W*code\\s*,\\s*description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Nursing - Allergy (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalNursingAllergy(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        // the report check must not accept the neighbouring "Clinical-Pharmacy - Allergy Severity"
        locationCsvReport(rc, "Clinical-Nursing - Allergy", "CLINICAL NURSING ALLERGY",
                System.getProperty("mst.cna.report", "NURSING\\W*ALLERGY(?!\\W*SEVERITY)"), System.getProperty("mst.cna.header", "^\\W*allergyid\\s*,\\s*Code\\s*,\\s*Allergen\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Nursing - Triage (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalNursingTriage(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Nursing - Triage", "CLINICAL NURSING TRIAGE",
                System.getProperty("mst.cnt.report", "NURSING\\W*TRIAGE"), System.getProperty("mst.cnt.header", "^\\W*triagemasterid\\s*,\\s*Code\\s*,\\s*Category\\s*,\\s*Status\\s*,\\s*printorder"));
    }

    // =====================================================================================================================================================
    //  Clinical-Nursing - Vital Sign (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalNursingVitalSign(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Nursing - Vital Sign", "CLINICAL NURSING VITAL SIGN",
                System.getProperty("mst.cnv.report", "NURSING\\W*VITAL\\W*SIGN"), System.getProperty("mst.cnv.header", "^\\W*patientvitalid\\s*,\\s*Code\\s*,\\s*Vital\\s*,\\s*Default\\s*Value\\s*,\\s*Min\\s*Value\\s*,\\s*Max\\s*Value\\s*,\\s*Unit\\s*,\\s*Vital\\s*Order\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Allergy Severity (recorded from the user's own actions, 2026-11-27: Master Report > report > filter Location > Add > Add Value > tick location 1 > OK > Show Report > CSV download)
    // =====================================================================================================================================================

    private void clinicalPharmacyAllergySeverity(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Allergy Severity", "CLINICAL PHARMACY ALLERGY SEVERITY",
                System.getProperty("mst.cpas.report", "PHARMACY\\W*ALLERGY\\W*SEVERITY"), System.getProperty("mst.cpas.header", "^\\W*Code\\s*,\\s*Severity\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Dosage Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalPharmacyDosageMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Dosage Master", "CLINICAL PHARMACY DOSAGE MASTER",
                System.getProperty("mst.cpdm.report", "PHARMACY\\W*DOSAGE\\W*MASTER"), System.getProperty("mst.cpdm.header", "^\\W*serviceitemid\\s*,\\s*itemdetailid\\s*,\\s*TCARE\\s*Item\\s*Code\\s*,\\s*Item\\s*Name\\s*,\\s*Ideal\\s*Dosage\\s*Qty\\s*,\\s*Ideal\\s*Dosage\\s*Unit\\s*,"
                        + "\\s*Item\\s*Dosage\\s*Qty\\s*,\\s*Item\\s*Dosage\\s*Unit\\s*,\\s*Total\\s*Cons\\s*Qty\\s*,\\s*Item\\s*Dosage\\s*Cons\\.\\s*Unit\\s*,\\s*Dosage\\s*Conversion\\s*,\\s*Item\\s*Size\\s*\\(Cons\\.\\s*Unit\\)\\s*,\\s*Divided\\s*Dose"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Dose Frequency (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalPharmacyDoseFrequency(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Dose Frequency", "CLINICAL PHARMACY DOSE FREQUENCY",
                System.getProperty("mst.cpdf.report", "PHARMACY\\W*DOSE\\W*FREQUENCY"), System.getProperty("mst.cpdf.header", "^\\W*Code\\s*,\\s*Description\\s*,\\s*Label\\s*Description\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Drug Instruction (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalPharmacyDrugInstruction(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Drug Instruction", "CLINICAL PHARMACY DRUG INSTRUCTION",
                System.getProperty("mst.cpdi.report", "PHARMACY\\W*DRUG\\W*INSTRUCTION"), System.getProperty("mst.cpdi.header", "^\\W*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Prescription Type (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalPharmacyPrescriptionType(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Prescription Type", "CLINICAL PHARMACY PRESCRIPTION TYPE",
                System.getProperty("mst.cppt.report", "PHARMACY\\W*PRESCRIPTION\\W*TYPE"), System.getProperty("mst.cppt.header", "^\\W*id\\s*,\\s*Type\\s*,\\s*Description\\s*,\\s*Store"));
    }

    // =====================================================================================================================================================
    //  Clinical-Pharmacy - Route of Administration (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void clinicalPharmacyRouteOfAdministration(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Clinical-Pharmacy - Route of Administration", "CLINICAL PHARMACY ROUTE OF ADMINISTRATION",
                System.getProperty("mst.cpra.report", "PHARMACY\\W*ROUTE\\W*OF\\W*ADMINISTRATION"), System.getProperty("mst.cpra.header", "^\\W*Code\\s*,\\s*Description\\s*,\\s*Print\\s*Description\\s*,\\s*Status\\s*\\(Y/N\\)\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description"));
    }

    // =====================================================================================================================================================
    //  Finance - Doctor Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void financeDoctorMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Doctor Master", "FINANCE DOCTOR MASTER",
                System.getProperty("mst.fdm.report", "FINANCE\\W*DOCTOR\\W*MASTER"), System.getProperty("mst.fdm.header", "^\\W*PayableID\\s*,\\s*Code\\s*,\\s*Prefix\\s*,\\s*DoctorName\\s*,\\s*PayableType\\s*,\\s*LoginName\\s*,\\s*Password\\s*,\\s*Nationality\\s*,\\s*Cabin\\s*,\\s*Cluster\\s*,\\s*DoctorType\\s*,"
                        + "\\s*Designation\\s*,\\s*Speciality\\s*,\\s*MaritalStatus\\s*,\\s*BirthDate\\s*,\\s*MobileNo\\s*,\\s*Identification\\s*Number\\s*,\\s*Qualification\\s*,\\s*EmailID\\s*,\\s*NMC\\s*,\\s*NSR\\s*,\\s*Gender\\s*,\\s*DateOfJoining\\s*,\\s*GLCode\\s*,"
                        + "\\s*EinvoiceIdentificationNmber\\s*,\\s*tinnumber\\s*,\\s*tourismtaxregistrationno\\s*,\\s*sstregistrationno\\s*,\\s*misccode\\s*,\\s*businessactivitydescription\\s*,\\s*bankaccountno"));
    }

    // =====================================================================================================================================================
    //  Finance - Employee Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  The export holds login names, passwords and personal details: those columns are hidden before anything is saved (see showAndVerifyDownload).
    // =====================================================================================================================================================

    private void financeEmployeeMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Employee Master", "FINANCE EMPLOYEE MASTER",
                System.getProperty("mst.fem.report", "FINANCE\\W*EMPLOYEE\\W*MASTER"), System.getProperty("mst.fem.header", "^\\W*payableid\\s*,\\s*Code\\s*,\\s*Prefix\\s*,\\s*Full\\s*Name\\s*,\\s*Payable\\s*Type\\s*,\\s*Login\\s*Name\\s*,\\s*Password\\s*,\\s*Nationality\\s*,"
                        + "\\s*Unit\\s*/\\s*Location\\s*,\\s*Designation\\s*,\\s*Department\\s*,\\s*Marital\\s*Status\\s*,\\s*Birth\\s*Date\\s*,\\s*Mobile\\s*No\\s*,\\s*Identification\\s*No\\s*,\\s*Qualification\\s*,\\s*Tel\\s*No\\s*,\\s*Email\\s*Id\\s*,\\s*Gender\\s*,\\s*GL\\s*Code"));
    }

    // =====================================================================================================================================================
    //  Finance - Package Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  Its columns look like Lab Profile's plus "Qty (Medication)"; the header pattern keeps the two apart.
    // =====================================================================================================================================================

    private void financePackageMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Package Master", "FINANCE PACKAGE MASTER",
                System.getProperty("mst.fpm.report", "FINANCE\\W*PACKAGE\\W*MASTER"), System.getProperty("mst.fpm.header", "^\\W*id\\s*,\\s*Lab\\s*Profile\\s*Code\\s*,\\s*Profile\\s*Name\\s*,\\s*Service\\s*Code\\s*,\\s*Category\\s*,\\s*Service\\s*/\\s*Item\\s*Name\\s*\\(Included\\s*in\\s*Package\\)\\s*,"
                        + "\\s*Qty\\s*\\(Medication\\)\\s*,\\s*OPD\\s*Rate\\s*,\\s*Package\\s*Rate\\s*-\\s*OPD\\s*,\\s*IPD\\s*Rate\\s*,\\s*Package\\s*Rate\\s*-\\s*IPD"));
    }

    // =====================================================================================================================================================
    //  Finance - Payer Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  The export holds payers' addresses, phone / fax numbers, contact persons and e-mail addresses: those columns are hidden before anything is saved (see showAndVerifyDownload).
    // =====================================================================================================================================================

    private void financePayerMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Payer Master", "FINANCE PAYER MASTER",
                System.getProperty("mst.fpy.report", "FINANCE\\W*PAYER\\W*MASTER"), System.getProperty("mst.fpy.header", "^\\W*receivableid\\s*,\\s*Customer\\s*Type\\s*,\\s*Customer\\s*Code\\s*,\\s*Customer\\s*Name\\s*,\\s*address\\s*,\\s*Postcode\\s*,\\s*description\\s*,\\s*State\\s*,\\s*Country\\s*,"
                        + "\\s*Primary\\s*Phone\\s*no\\s*,\\s*Primary\\s*Fax\\s*no\\s*,\\s*Primary\\s*Contact\\s*Person\\s*,\\s*Email\\s*Id\\s*,\\s*GL\\s*Code\\s*,\\s*Credit\\s*Term\\s*\\(Days\\)\\s*,\\s*Credit\\s*Limit\\s*\\(RM\\)\\s*,\\s*Status\\s*,\\s*Category\\s*,\\s*Created\\s*Date"));
    }

    // =====================================================================================================================================================
    //  Finance - Service Master Report (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  Its columns are the same 28 as "Billing - Service"; the report is told apart by its file name.
    // =====================================================================================================================================================

    private void financeServiceMasterReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Service Master Report", "FINANCE SERVICE MASTER",
                System.getProperty("mst.fsm.report", "FINANCE\\W*SERVICE\\W*MASTER"), System.getProperty("mst.fsm.header", "^\\W*serviceitemid\\s*,\\s*Service\\s*Code\\s*,\\s*Service\\s*Name\\s*,\\s*Service\\s*Name\\s*Label\\s*,\\s*Group\\s*Name\\s*,\\s*Sub\\s*Group\\s*Name\\s*,\\s*Short\\s*Description\\s*,"
                        + "\\s*Rate\\s*Editable\\s*,\\s*Emergency\\s*Applicable\\s*,\\s*Discount\\s*Applicable\\s*,\\s*Min\\s*Rate\\s*,\\s*Max\\s*Rate\\s*,\\s*Emergency\\s*Charge\\s*Percentage\\s*,\\s*Discount\\s*Percentage\\s*,\\s*Rate\\s*\\(OPD\\)\\s*,\\s*Rate\\s*\\(IPD\\)\\s*,"
                        + "\\s*Service\\s*Type\\s*,\\s*Can\\s*be\\s*part\\s*of\\s*Package\\s*,\\s*Is\\s*Package\\s*,\\s*Package\\s*\\(IPD\\)\\s*,\\s*Package\\s*\\(OPD\\)\\s*,\\s*Is\\s*OT\\s*Procedure\\s*,\\s*Is\\s*Doctor\\s*Share\\s*Applicable\\s*,\\s*Share\\s*%\\s*,"
                        + "\\s*E-Invoice\\s*Classification\\s*Code\\s*,\\s*Cost\\s*Center\\s*,\\s*Service\\s*Tax\\s*Applicability\\s*\\(Non\\s*Citizen\\)\\s*,\\s*Service\\s*Tax\\s*%\\s*\\(Non\\s*Citizen\\)"));
    }

    // =====================================================================================================================================================
    //  Finance - Supplier Master Report (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  The export holds vendors' addresses, phone / fax numbers and e-mail addresses: those columns are hidden before anything is saved (see showAndVerifyDownload).
    // =====================================================================================================================================================

    private void financeSupplierMasterReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Supplier Master Report", "FINANCE SUPPLIER MASTER",
                System.getProperty("mst.fsp.report", "FINANCE\\W*SUPPLIER\\W*MASTER"), System.getProperty("mst.fsp.header", "^\\W*payableid\\s*,\\s*Code\\s*,\\s*Vendor\\s*Name\\s*,\\s*Vendor\\s*Address\\s*,\\s*Payable\\s*Type\\s*,\\s*Company\\s*Tel\\s*Number\\s*1\\s*,\\s*Company\\s*Tel\\s*Number\\s*2\\s*,"
                        + "\\s*Email\\s*,\\s*Fax\\s*Number\\s*,\\s*Payment\\s*Term\\s*,\\s*Payment\\s*Term\\s*Detail\\s*,\\s*Vendor\\s*Status\\s*,\\s*GLCode-Ledger"));
    }

    // =====================================================================================================================================================
    //  Finance - Tariff Discount Policy Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    //  Its columns are the same nine as "Billing - Discount Policy"; the report is told apart by its file name.
    // =====================================================================================================================================================

    private void financeTariffDiscountPolicyMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Finance - Tariff Discount Policy Master", "FINANCE TARIFF DISCOUNT POLICY MASTER",
                System.getProperty("mst.ftd.report", "FINANCE\\W*TARIFF\\W*DISCOUNT\\W*POLICY"), System.getProperty("mst.ftd.header", "^\\W*id\\s*,\\s*Case\\s*Type\\s*,\\s*Discount\\s*Policy\\s*Code\\s*,\\s*Discount\\s*Policy\\s*Name\\s*,\\s*Group\\s*Code\\s*,\\s*Group\\s*Name\\s*,"
                        + "\\s*Subgroup\\s*Code\\s*,\\s*Subgroup\\s*Name\\s*,\\s*Discount\\s*Percentage"));
    }

    // =====================================================================================================================================================
    //  General -Item Master -Unit Master (the dropdown's own spacing; asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterUnitMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General -Item Master -Unit Master", "GENERAL ITEM MASTER UNIT MASTER",
                System.getProperty("mst.gum.report", "GENERAL\\W*ITEM\\W*MASTER\\W*UNIT\\W*MASTER"), System.getProperty("mst.gum.header", "^\\W*Unit\\s*ID\\s*,\\s*Location\\s*,\\s*Code\\s*,\\s*Item\\s*Unit\\s*,\\s*MIMS\\s*Description\\s*,"
                        + "\\s*Created\\s*By\\s*,\\s*Created\\s*Date\\s*&\\s*Time\\s*,\\s*Approved\\s*By\\s*,\\s*Approved\\s*Date\\s*&\\s*Time"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Drug Class (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterDrugClass(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Drug Class", "GENERAL ITEM MASTER DRUG CLASS",
                System.getProperty("mst.gdc.report", "GENERAL\\W*ITEM\\W*MASTER\\W*DRUG\\W*CLASS"), System.getProperty("mst.gdc.header", "^\\W*Code\\s*,\\s*Drug\\s*Class\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Drug Item Category (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterDrugItemCategory(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Drug Item Category", "GENERAL ITEM MASTER DRUG ITEM CATEGORY",
                System.getProperty("mst.gdic.report", "GENERAL\\W*ITEM\\W*MASTER\\W*DRUG\\W*ITEM\\W*CATEGORY"), System.getProperty("mst.gdic.header", "^\\W*Code\\s*,\\s*Drug\\s*Category\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Generic Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterGenericMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Generic Master", "GENERAL ITEM MASTER GENERIC MASTER",
                System.getProperty("mst.ggm.report", "GENERAL\\W*ITEM\\W*MASTER\\W*GENERIC\\W*MASTER"), System.getProperty("mst.ggm.header", "^\\W*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Item Class (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterItemClass(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Item Class", "GENERAL ITEM MASTER ITEM CLASS",
                System.getProperty("mst.gic.report", "GENERAL\\W*ITEM\\W*MASTER\\W*ITEM\\W*CLASS"), System.getProperty("mst.gic.header", "^\\W*itemclassid\\s*,\\s*Code\\s*,\\s*TCARE\\s*Class\\s*,\\s*Label\\s*Description\\s*,\\s*Controlled\\s*Medicine"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Rack (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterRack(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Rack", "GENERAL ITEM MASTER RACK",
                System.getProperty("mst.grk.report", "GENERAL\\W*ITEM\\W*MASTER\\W*RACK"), System.getProperty("mst.grk.header", "^\\W*id\\s*,\\s*Code\\s*,\\s*Description"));
    }

    // =====================================================================================================================================================
    //  General-Item Master - Shelf (Cell) (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalItemMasterShelfCell(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Item Master - Shelf (Cell)", "GENERAL ITEM MASTER SHELF CELL",
                System.getProperty("mst.gsh.report", "GENERAL\\W*ITEM\\W*MASTER\\W*SHELF"), System.getProperty("mst.gsh.header", "^\\W*id\\s*,\\s*Code\\s*,\\s*Description"));
    }

    // =====================================================================================================================================================
    //  General-Masters - Blood Group (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersBloodGroup(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - Blood Group", "GENERAL MASTERS BLOOD GROUP",
                System.getProperty("mst.gbg.report", "GENERAL\\W*MASTERS\\W*BLOOD\\W*GROUP"), System.getProperty("mst.gbg.header", "^\\W*bloodgroupid\\s*,\\s*TCARE\\s*Code\\s*,\\s*TCARE\\s*Description\\s*,\\s*SMRP\\s*Blood\\s*Type\\s*Code\\s*,\\s*SMRP\\s*Blood\\s*Type\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - City (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersCity(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - City", "GENERAL MASTERS CITY",
                System.getProperty("mst.gct.report", "GENERAL\\W*MASTERS\\W*CITY"), System.getProperty("mst.gct.header", "^\\W*cityid\\s*,\\s*City\\s*Code\\s*,\\s*City\\s*Name\\s*,\\s*State\\s*,\\s*Country\\s*,\\s*SMRP\\s*City\\s*Code\\s*,\\s*SMRP\\s*City\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - Country (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersCountry(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - Country", "GENERAL MASTERS COUNTRY",
                System.getProperty("mst.gcn.report", "GENERAL\\W*MASTERS\\W*COUNTRY"), System.getProperty("mst.gcn.header", "^\\W*countryid\\s*,\\s*TCARE\\s*Code\\s*,\\s*TCARE\\s*Description\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - Designation (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersDesignation(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - Designation", "GENERAL MASTERS DESIGNATION",
                System.getProperty("mst.gdg.report", "GENERAL\\W*MASTERS\\W*DESIGNATION"), System.getProperty("mst.gdg.header", "^\\W*designationid\\s*,\\s*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - Gender (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersGender(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - Gender", "GENERAL MASTERS GENDER",
                System.getProperty("mst.ggn.report", "GENERAL\\W*MASTERS\\W*GENDER"), System.getProperty("mst.ggn.header", "^\\W*genderid\\s*,\\s*TCARE\\s*Code\\s*,\\s*TCARE\\s*Description\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - Postcode (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersPostcode(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - Postcode", "GENERAL MASTERS POSTCODE",
                System.getProperty("mst.gpc.report", "GENERAL\\W*MASTERS\\W*POSTCODE"), System.getProperty("mst.gpc.header", "^\\W*id\\s*,\\s*Postal\\s*Code\\s*,\\s*City\\s*,\\s*State\\s*,\\s*Country\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  General-Masters - State (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void generalMastersState(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "General-Masters - State", "GENERAL MASTERS STATE",
                System.getProperty("mst.gst.report", "GENERAL\\W*MASTERS\\W*STATE"), System.getProperty("mst.gst.header", "^\\W*id\\s*,\\s*TCARE\\s*Code\\s*,\\s*TCARE\\s*Description\\s*,\\s*Country\\s*,\\s*SMRP\\s*Code\\s*,\\s*SMRP\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Item Markup Policy Master (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void itemMarkupPolicyMaster(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Item Markup Policy Master", "ITEM MARKUP POLICY MASTER",
                System.getProperty("mst.imp.report", "ITEM\\W*MARKUP\\W*POLICY\\W*MASTER"), System.getProperty("mst.imp.header", "^\\W*Pricing\\s*Policy\\s*,\\s*Class\\s*Name\\s*,\\s*Service\\s*Group\\s*Code\\s*\\(Item\\)\\s*,\\s*Service\\s*Group\\s*Name\\s*\\(Item\\)\\s*,\\s*Service\\s*Subgroup\\s*Code\\s*\\(Item\\)\\s*,\\s*Service\\s*Subgroup\\s*Name\\s*\\(Item\\)\\s*,\\s*WAC\\s*From\\s*,\\s*WAC\\s*To\\s*,\\s*Markup\\s*Per"));
    }

    // =====================================================================================================================================================
    //  Item Master-Item (All) (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void itemMasterItemAll(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Item Master-Item (All)", "ITEM MASTER ITEM ALL",
                System.getProperty("mst.ima.report", "ITEM\\W*MASTER\\W*ITEM\\W*ALL"), System.getProperty("mst.ima.header", "^\\W*Item\\s*ID\\s*,\\s*Location\\s*,\\s*Item\\s*Code\\s*,\\s*TCM\\s*Item\\s*,\\s*Item\\s*Name\\s*,\\s*Group\\s*,\\s*Sub\\s*Group"));
    }

    // =====================================================================================================================================================
    //  Location - Bank (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void locationBank(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Location - Bank", "LOCATION BANK",
                System.getProperty("mst.lbk.report", "LOCATION\\W*BANK"), System.getProperty("mst.lbk.header", "^\\W*TCARE\\s*Code\\s*,\\s*Description\\s*,\\s*Status"));
    }

    // =====================================================================================================================================================
    //  Location - Cash Counter (asked for without a recording: a probe showed the report offers only the Location filter, so it uses the steps recorded for the other Master tabs)
    // =====================================================================================================================================================

    private void locationCashCounter(com.kpj.pages.Report_page.Report_Console.Master_Report rc) {
        locationCsvReport(rc, "Location - Cash Counter", "LOCATION CASH COUNTER",
                System.getProperty("mst.lcc.report", "LOCATION\\W*CASH\\W*COUNTER"), System.getProperty("mst.lcc.header", "^\\W*cashcounterid\\s*,\\s*Location\\s*,\\s*TCARE\\s*Code\\s*,\\s*Cash\\s*Counter\\s*,\\s*Cost\\s*Center"));
    }

    /**
     * A Master report whose only filter is Location (as recorded for Admission - Admission Type and Admission - Bed Amenities): Master Report tab, the report, filter Location (the popup's first record, id 1),
     * Show Report -> the CSV is verified ({@link #showAndVerifyDownload}).
     */
    private void locationCsvReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String reportLabel, String reportRegex, String headerRegex) {
        rc.clickClear();   // the previous report's filters must not be sent along
        if (!openReport(rc, sec, sec)) return;   // the Reports dropdown entry is named like the section

        // 1) Location: the Select Location popup (checkbox loc.IsSelected, record id 1)
        final String fLoc = "Location";
        if (!addFilter(rc, sec, fLoc)) return;
        if (!openValue(rc, sec, fLoc, "The Select Location popup opens (a checkbox per location)")) return;
        List<String> locs = rc.rowsOf("loc.IsSelected");
        stepL(sec + " - List the locations", "Look at the locations the popup offers", "The popup lists the locations",
                !locs.isEmpty() ? locs.size() + " location(s) listed, first: " + locs.get(0) : "No location is listed " + rc.problems, !locs.isEmpty() ? "PASS" : "FAIL");
        if (locs.isEmpty()) { addSummary(sec + " - Result", "FAILED - no location listed"); return; }
        List<String> locTicked = tickIds(rc, sec, "loc.IsSelected", "a location", "1");
        if (locTicked == null) return;
        if (!okStep(rc, sec, fLoc, "Location", v -> locTicked.stream().allMatch(d -> v.contains(d) || d.contains(v)), "the chosen location")) return;

        // 2) Show Report -> a CSV is downloaded: it must be generated, without an error, be this report and list records
        boolean related = showAndVerifyDownload(rc, sec, reportRegex, reportLabel, headerRegex);

        addSummary(sec + " - Report", sec + " (Master Report)");
        addSummary(sec + " - Location", String.valueOf(locTicked));
        addSummary(sec + " - Result", related ? sec + " CSV generated with records" : "See the failed step(s)");
    }

    // =====================================================================================================================================================
    //  shared steps
    // =====================================================================================================================================================

    /** Tick the rows of the open popup by record id (the row at the same position when an id is not listed); returns the ticked names or null when a step failed. */
    private List<String> tickIds(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String ngModel, String what, String... ids) {
        List<String> names = new java.util.ArrayList<>();
        for (int i = 0; i < ids.length; i++) {
            String n = rc.tickRowById(ngModel, ids[i]);
            boolean fallback = false;
            if (n.isEmpty() && rc.rowsOf(ngModel).size() > i) { n = rc.tickRowOf(ngModel, i); fallback = true; }
            stepL(sec + " - Tick " + what + " (" + (i + 1) + " of " + ids.length + ")", "Tick the checkbox of " + what + " (recorded record id " + ids[i] + ")", "The row is ticked",
                    !n.isEmpty() ? "Ticked \"" + n + "\"" + (fallback ? " (record id " + ids[i] + " is not listed - row " + (i + 1) + " used instead)" : " (id " + ids[i] + ")") : "Problem - " + rc.problems, !n.isEmpty() ? "PASS" : "FAIL");
            if (n.isEmpty()) { addSummary(sec + " - Result", "FAILED - " + what + ": " + rc.problems); return null; }
            names.add(n);
        }
        return names;
    }

    /** Master Report tab -> the report in the Reports dropdown. */
    private boolean openReport(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String report) {
        boolean cat = rc.selectCategory(com.kpj.pages.Report_page.Report_Console.Master_Report.CATEGORY);
        stepL(sec + " - Open the Master Report tab", "Click the \"Master Report\" button of the Report Console", "The Reports dropdown lists the master reports",
                cat ? "Reports offered (" + rc.reportOptions().size() + "): " + rc.reportOptions() : "Problem - " + rc.problems, cat ? "PASS" : "FAIL");
        if (!cat) { addSummary(sec + " - Result", "FAILED - Master Report: " + rc.problems); return false; }
        String rep = rc.selectReport(report);
        stepL(sec + " - Select the report", "In the Reports dropdown select \"" + report + "\"", "The report is selected and its filters are offered",
                !rep.isEmpty() ? "Reports = \"" + rep + "\"; Filter dropdown offers " + rc.filterOptions() : "Problem - " + rc.problems, !rep.isEmpty() ? "PASS" : "FAIL");
        if (rep.isEmpty()) { addSummary(sec + " - Result", "FAILED - report: " + rc.problems); return false; }
        return true;
    }

    /** Filter dropdown -> Add -> verify the filter appears in the table. */
    private boolean addFilter(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter) {
        String f = rc.selectFilter(filter);
        stepL(sec + " - Select the filter " + filter, "In the Filter dropdown select \"" + filter + "\"", "The filter is selected", !f.isEmpty() ? "Filter = \"" + f + "\"" : "Problem - " + rc.problems, !f.isEmpty() ? "PASS" : "FAIL");
        if (f.isEmpty()) { addSummary(sec + " - Result", "FAILED - filter " + filter + ": " + rc.problems); return false; }
        boolean a = rc.clickAdd(filter);
        stepL(sec + " - Click Add (" + filter + ")", "Click Add (AddFilter())", "The filter is added to the table", a ? "Add clicked" : "Problem - " + rc.problems, a ? "PASS" : "FAIL");
        boolean in = rc.hasFilter(filter);
        stepL(sec + " - Verify the filter appears in the table (" + filter + ")", "In the Filter Name | Filter Value table look for \"" + filter + "\"", "The added filter appears",
                in ? "The table lists " + rc.filterRows().stream().map(r -> r[0]).toList() : "\"" + filter + "\" is not in the table (" + rc.filterRows().stream().map(r -> r[0]).toList() + ")", in ? "PASS" : "FAIL");
        if (!in) { addSummary(sec + " - Result", "FAILED - the " + filter + " filter was not added"); return false; }
        return true;
    }

    /** Add Value on the filter's row -> its popup. */
    private boolean openValue(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter, String expected) {
        boolean v = rc.clickAddValue(filter);
        stepL(sec + " - Click Add Value (" + filter + ")", "Click Add Value on the filter's row", expected, v ? "The popup opened" : "Problem - " + rc.problems, v ? "PASS" : "FAIL");
        if (!v) { addSummary(sec + " - Result", "FAILED - popup of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    /** OK in the popup -> it closes and the Filter Value shows what was chosen. */
    private boolean okStep(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String filter, String kind, java.util.function.Predicate<String> valueCheck, String what) {
        boolean ok = rc.clickOk(kind);
        String val = rc.filterValue(filter);
        boolean good = ok && valueCheck.test(val);
        stepL(sec + " - Click OK (" + filter + ")", "Click OK in the popup", "The popup closes and the filter's value shows " + what,
                good ? "Filter Value = \"" + val + "\"" : "Problem - " + rc.problems + " Filter Value = \"" + val + "\"", good ? "PASS" : "FAIL");
        if (!good) { addSummary(sec + " - Result", "FAILED - OK of " + filter + ": " + rc.problems); return false; }
        return true;
    }

    private static final Pattern ERROR_MESSAGE = Pattern.compile("server error|failed to retrieve|error in file|exception|database vendor code", Pattern.CASE_INSENSITIVE);

    /**
     * The first line of a downloaded report that carries an error message, or "" when none does. The records of a table are data whatever words they hold (Diagnosis Master lists "Exceptionally large
     * newborn baby", which is not an exception), so for a file whose header has two or more columns only the header and the lines that do NOT have that many columns are searched (an error text or a
     * stack trace added to the data). A file that is not such a table - an error message instead of a CSV - is searched whole.
     */
    private static String errorMessageIn(String text) {
        java.util.List<java.util.List<String>> rows = com.kpj.pages.Report_page.Report_Console.Master_Report.parseCsv(text);
        final int columns = rows.isEmpty() ? 0 : rows.get(0).size();
        for (int i = 0; i < rows.size(); i++) {
            if (columns >= 2 && i > 0 && rows.get(i).size() == columns) continue;
            String line = String.join(",", rows.get(i)).trim();
            if (ERROR_MESSAGE.matcher(line).find()) return line.length() > 200 ? line.substring(0, 200) + " ..." : line;
        }
        return "";
    }

    /**
     * Show Report; the report is DOWNLOADED as a file. Four verdicts: it was generated (a non-empty .csv), it has no error (a download happened - the console's "No Data Available!" or no file at all = FAIL - and the file is data,
     * not an HTML error page), it is the report that was added (file name / first line match {@code reportRegex}; the first line matches {@code headerRegex}) and it lists records (at least one data line under the header).
     * Returns true when all held.
     */
    private boolean showAndVerifyDownload(com.kpj.pages.Report_page.Report_Console.Master_Report rc, String sec, String reportRegex, String reportLabel, String headerRegex) {
        Path dir = reportDir.getParent() != null ? reportDir.getParent().resolve("downloads").resolve("MasterReport") : Paths.get("downloads", "MasterReport");
        // a big report can take more than a minute to be built (Finance - Employee Master did): wait up to -Dmst.waitSec (default 180); a "No Data" toast still ends the wait early
        com.kpj.pages.Report_page.Report_Console.Master_Report.DownloadResult dr = rc.showReportAndCaptureDownload(dir, Integer.getInteger("mst.waitSec", 180) * 1000);
        boolean clicked = dr.downloaded || !dr.diagnostics.startsWith("Show Report could not be clicked");
        stepL(sec + " - Click Show Report", "Click Show Report (ShowReport($event))", "The report is requested", clicked ? "Show Report clicked (" + rc.filterRows().size() + " filter(s) set)" : "Problem - " + rc.problems, clicked ? "PASS" : "FAIL");
        if (!clicked) { addSummary(sec + " - Result", "FAILED - Show Report: " + rc.problems); return false; }

        // PRIVATE DATA is hidden before anything else is written from the file (Finance - Doctor Master exports login names, passwords, identity and bank account numbers): the saved copy, the report text and the picture
        final java.util.List<String> hiddenColumns = rc.hidePrivateColumns(dr);
        final String hiddenNote = hiddenColumns.isEmpty() ? "" : ". PRIVATE DATA is hidden in the saved copy and in this report, column(s): " + String.join(", ", hiddenColumns);
        if (!hiddenColumns.isEmpty()) {
            addSummary(sec + " - Private columns hidden", String.join(", ", hiddenColumns));
            if (hiddenColumns.stream().anyMatch(c -> c.toLowerCase().contains("password"))) addSummary(sec + " - SECURITY WARNING", "The downloaded report contains a Password column (its values are hidden here)");
        }

        // 1) generated: a non-empty .csv was downloaded
        final boolean isCsv = dr.filename.toLowerCase().endsWith(".csv");
        boolean generated = dr.downloaded && dr.failure.isEmpty() && dr.bytes > 0 && isCsv && !dr.looksLikeZip;
        stepL(sec + " - Verify the report is generated (downloaded as CSV)", "Check a file was downloaded after Show Report", "A non-empty CSV file is downloaded",
                dr.diagnostics + (dr.downloaded ? (isCsv ? "" : " - FAILS: the file is not a .csv") : " - FAILS: nothing was downloaded") + (dr.savedTo.isEmpty() ? "" : ". Saved to " + dr.savedTo) + hiddenNote, generated ? "PASS" : "FAIL");

        // 2) no error: a download happened, it is data and not an error page / the console's refusal
        final String errorFound = errorMessageIn(dr.text);
        boolean errored = !dr.downloaded || !dr.failure.isEmpty() || dr.looksLikeHtml || !errorFound.isEmpty();
        final String errAct = !dr.downloaded ? "FAILS: the report was NOT downloaded - " + dr.diagnostics
                : errored ? "FAILS: the downloaded file is an ERROR, not data - " + (!dr.failure.isEmpty() ? dr.failure
                        : (errorFound.isEmpty() ? "" : "found \"" + errorFound + "\"; the file starts: ") + (dr.text.length() > 300 ? dr.text.substring(0, 300) + " ..." : dr.text))
                : "The report was downloaded without an error (" + dr.bytes + " bytes)";
        stepL(sec + " - Verify the report has no error", "Look at what Show Report produced for an error message", "The report is downloaded without an error", errAct, errored ? "FAIL" : "PASS");
        addSummary(sec + " - Report error", errored ? "YES - " + (dr.downloaded ? "error text in the file" : (dr.errorText.isEmpty() ? "nothing downloaded" : dr.errorText)) : "none");

        // 3) it is the report that was added: the file name or its first line says so, and the first line is the report's header
        String first = dr.lines.isEmpty() ? "" : dr.lines.get(0);
        // the browser reports the file name with underscores for spaces ("Admission_-_Bed_Details.csv"): they count as separators here
        boolean named = Pattern.compile(reportRegex, Pattern.CASE_INSENSITIVE).matcher(dr.filename.replace('_', ' ') + " " + first).find();
        boolean headed = Pattern.compile(headerRegex, Pattern.CASE_INSENSITIVE).matcher(first).find();
        boolean related = generated && named && headed;
        String head = first.length() > 300 ? first.substring(0, 300) + " ..." : first;
        stepL(sec + " - Verify the report is the " + reportLabel + " report", "Read the file's name and first line", "The file is the " + reportLabel + " report",
                !dr.downloaded ? "No readable file to judge (" + dr.diagnostics + ")"
                        : (named ? "The file name / first line mention \"" + reportRegex + "\"" : "FAILS: neither the file name \"" + dr.filename + "\" nor the first line matches \"" + reportRegex + "\"")
                          + "; " + (dr.lines.isEmpty() ? "FAILS: the file is EMPTY - it has no header line, so its content cannot confirm the report (only its file name does)"
                                  : (headed ? "the first line is the report's header" : "FAILS: the first line does not match the header pattern \"" + headerRegex + "\"") + ". First line: \"" + head + "\""),
                related ? "PASS" : "FAIL");

        // 4) it lists records: at least one data row (not only commas / blanks) under the header
        int dataLines = Math.max(0, com.kpj.pages.Report_page.Report_Console.Master_Report.parseCsv(dr.text).size() - 1);
        boolean records = generated && dataLines >= 1;
        final String recAct = !dr.downloaded ? "No readable file to judge (" + dr.diagnostics + ")"
                : records ? dataLines + " data row(s) under the header; the first: \"" + (dr.lines.size() > 1 ? (dr.lines.get(1).length() > 200 ? dr.lines.get(1).substring(0, 200) + " ..." : dr.lines.get(1)) : "") + "\""
                : "FAILS: the file is generated but lists NO RECORD - " + (dr.lines.isEmpty() ? "the file is EMPTY (" + dr.bytes + " bytes: not even a header line)" : dr.lines.size() == 1 ? "only its header line" : "only empty lines under its header") + ". No record matches the chosen filters (or the report returns none).";

        // EVIDENCE: the downloaded CSV shown as a table (the application has no screen for it), photographed and saved next to the file; it is this step's screenshot
        byte[] csvPng = null;
        String pngSaved = "";
        if (dr.downloaded && !dr.looksLikeZip && !dr.savedTo.isEmpty()) {   // an empty file is pictured too ("THE DOWNLOADED FILE IS EMPTY")
            Path pngFile = Paths.get(dr.savedTo.replaceAll("(?i)\\.csv$", "") + ".png");
            csvPng = rc.screenshotCsv(dr, sec, pngFile);
            if (csvPng != null) pngSaved = pngFile.toString();
        }
        final String recName = sec + " - Verify the report shows records", recDesc = "Look for data rows under the CSV's header", recExpected = "The CSV lists the records matching the filters";
        if (csvPng != null) step(csvPng, recName, recDesc, recExpected, recAct + ". The screenshot is the downloaded CSV shown as a table (also saved as " + pngSaved + ").", records ? "PASS" : "FAIL");
        else stepL(recName, recDesc, recExpected, recAct + (dr.downloaded ? " (no picture of the CSV could be made " + rc.problems + "; the screenshot is the console)" : ""), records ? "PASS" : "FAIL");

        addSummary(sec + " - Downloaded file", dr.downloaded ? dr.filename + " (" + dr.bytes + " bytes) - " + dr.savedTo : "(none)");
        addSummary(sec + " - CSV picture (evidence)", pngSaved.isEmpty() ? "(none)" : pngSaved);
        addSummary(sec + " - Full report text", dr.text.isEmpty() ? "(none)" : (dr.text.length() > 1500 ? dr.text.substring(0, 1500) + " ..." : dr.text));
        addSummary(sec + " - Records in the report", records ? dataLines + " data line(s)" : "NONE");
        return related && records;
    }
}
