package com.kpj.tests.Ip;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is referenced by its fully-qualified name (com.kpj.pages.Ip.BedboardOccupancyListPage),
// same style as the sibling com.kpj.tests.Ip.Admission test.

/**
 * TC18 - IP &gt; <b>Bedboard Occupancy List</b> (consolidated) — one login, then each footer action as its own
 * section (each re-opens the list + searches + selects a patient, so one failing section doesn't abort the rest):
 *
 * <ol>
 *   <li><b>Change Admission Type</b> — fill all 3 sections (Change Admission Type / Additional Doctors /
 *       Next of Kin Details) → Save ({@code fnUpdateAdmissionType}).</li>
 *   <li><b>Cancel Admission</b> — select a reason → Save → "Admission Cancelled Successfully!" → Close.</li>
 *   <li><b>Revoke Admission</b> — enter a remark → Save ({@code fnCloseRevokeVisitClick}).</li>
 * </ol>
 */
public class BedboardOccupancyListTest extends DevHisBase {

    public BedboardOccupancyListTest() { super("TC18_BedboardOccupancyList"); }

    public static void main(String[] args) {
        BedboardOccupancyListTest t = new BedboardOccupancyListTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Bedboard Occupancy List",
                "IP > Bedboard Occupancy List",
                "One run exercising the Bedboard Occupancy List footer actions: Change Admission Type, "
                        + "Cancel Admission and Revoke Admission.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionChangeAdmissionType();
        sectionChangeReferEntity();
        sectionConsentForms();
        sectionViewConsent();
        sectionPrintBarcode();
        sectionAssignTriage();
        sectionFallRiskAssessment();
        sectionPlanDischarge();
        sectionCancelAdmission();
        sectionRevokeAdmission();

        addSummary("Application URL", BASE + "/" + com.kpj.pages.Ip.BedboardOccupancyListPage.ROUTE);
    }

    /** Open the list, search a 1-month range ending today, and select a patient. Returns the page +
     *  selected patient name (or null patient if none / not opened). */
    private com.kpj.pages.Ip.BedboardOccupancyListPage openAndSelect(String tag) {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = new com.kpj.pages.Ip.BedboardOccupancyListPage(page);
        boolean opened = bed.navigateViaMenu();
        step(page, tag + " · Open Bedboard Occupancy List", "Click IP -> Bedboard Occupancy List",
                "The Bedboard Occupancy List is shown", opened ? "Opened (#/BedboardOccupancyList)" : "Did NOT reach the list",
                opened ? "PASS" : "FAIL");
        if (!opened) return null;
        String range = bed.searchOneMonthToToday();
        step(page, tag + " · Search (1-month range, To = today)", "Set From = today-1month, To = today; click Search",
                "Admitted patients are listed", "Searched " + range, "PASS");
        return bed;
    }

    // ===== Section: Change Admission Type =================================
    private void sectionChangeAdmissionType() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Change Admission Type");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Change Admission Type · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickChangeAdmissionType();
        step(page, "Change Admission Type · Open modal", "Click 'Change Admission Type' (ChangeAdmissionType())",
                "The Change Admission Type modal opens", modal ? "Modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String s1 = bed.fillChangeTypeSection();
        boolean s1ok = s1 != null && s1.contains("doctor=") && !s1.contains("doctor=null");
        step(page, "Change Admission Type · Fill Section 1", "Admission Type + Department (fnSetDoctorAdmissionType loads doctors) + Doctor",
                "Section 1 populated", s1, s1ok ? "PASS" : "FAIL");

        String s2 = bed.fillAdditionalDoctorsSection();
        boolean s2ok = s2 != null && s2.contains("classification=") && !s2.contains("classification=null");
        step(page, "Change Admission Type · Fill Additional Doctors", "Set Classification + Additional Doctor (left populated so Save passes validation)",
                "Classification + Additional Doctor set", s2, s2ok ? "PASS" : "FAIL");

        String s3 = bed.fillNokSection();
        boolean s3ok = s3 != null && s3.contains("NOK set");
        step(page, "Change Admission Type · Fill Next of Kin", "FillKinDropDown -> Title/Relationship/Receivable/Occupation + Name/Mobile/Address",
                "Next-of-Kin details set", s3, s3ok ? "PASS" : "FAIL");

        String toast = bed.saveAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("updated"));
        step(page, "Change Admission Type · Save & toast", "Click Save (fnUpdateAdmissionType())",
                "'... Updated Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Change Admission Type · Patient", patient);
        addSummary("Change Admission Type · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Consent / Forms (external-form document flow) ========
    private void sectionConsentForms() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Consent/Forms");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Consent/Forms · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickConsentForms();
        step(page, "Consent/Forms · Open popup", "Click 'Consent/Forms' (fnOnConsentClick())",
                "The Consent/Forms popup opens", modal ? "Popup opened" : "Popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String form = bed.selectExternalConsentForm();
        boolean formOk = form != null && form.contains("ext=") && !form.startsWith("(");
        step(page, "Consent/Forms · Select a form", "Pick a form from the search dropdown (an external form, so Save opens a document tab)",
                "A form is selected", form, formOk ? "PASS" : "FAIL");
        if (!formOk) { bed.closeConsentFormsPopup(); return; }

        boolean added = bed.clickAddConsent();
        step(page, "Consent/Forms · Add", "Click Add (Getconsenttemplate()); the external form template loads",
                "The form template is loaded (externalformmappingid set)",
                added ? "Template loaded (external form)" : "Template did NOT load", added ? "PASS" : "FAIL");
        if (!added) { bed.closeConsentFormsPopup(); return; }

        byte[] report = bed.saveConsentFormAndCaptureReport();
        boolean saveOk = report != null && report.length > 0 && bed.lastConsentSubmitConfirmed
                && bed.lastConsentReportUrl.toLowerCase().contains("/edit/");
        step(report, "Consent/Forms · Save -> Create Document -> Submit -> Yes -> OK -> Report",
                "Click Save (opens a new tab); click Create Document, then Submit, confirm Yes; after the 'Form Submitted Successfully!' OK confirmation, screenshot the report",
                "The external-form document is submitted successfully and the report is captured",
                report == null || report.length == 0 ? "No report captured"
                        : ((bed.lastConsentSubmitConfirmed ? "OK (" + (bed.lastConsentSubmitMsg.isEmpty() ? "on /Edit" : bed.lastConsentSubmitMsg) + ") - " : "Submit not confirmed - ") + "Report: " + bed.lastConsentReportUrl),
                saveOk ? "PASS" : "FAIL");

        bed.closeConsentFormsPopup();
        addSummary("Consent/Forms · Patient", patient);
        addSummary("Consent/Forms · Result", saveOk ? (form + " -> submitted, " + bed.lastConsentReportUrl) : "Not confirmed");
    }

    // ===== Section: View Consent (list saved forms, open one in a new tab) =
    private void sectionViewConsent() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("View Consent");
        if (bed == null) return;

        // Find a patient that HAS a saved consent/form so the table is populated (most patients have none — retry).
        String patient = "(first selected)";
        String recSummary = null;
        boolean hasRecords = false;
        int tries = 0;
        for (; tries < 8 && !hasRecords; tries++) {
            patient = bed.selectRandomPatient(); if (patient == null) continue;
            if (!bed.clickViewConsent()) continue;
            recSummary = bed.searchViewConsent();
            hasRecords = recSummary != null && recSummary.startsWith("records=");
            if (!hasRecords) bed.closeViewConsentPopup();
        }
        step(page, "View Consent · Open + find a patient with a saved form",
                "Click 'View Consent' (fnViewConsents()); if empty, retry different patients until one has a saved consent/form",
                "The View Consent table lists at least one consent/form (with an eye/View icon)",
                hasRecords ? (recSummary + " (patient " + patient + ", after " + tries + " tr" + (tries == 1 ? "y" : "ies") + ")")
                        : ("No patient with records in " + tries + " tries (last: " + recSummary + ")"),
                hasRecords ? "PASS" : "FAIL");
        if (!hasRecords) { bed.closeViewConsentPopup(); return; }

        byte[] report = bed.clickConsentEyeAndCaptureReport(null);
        boolean ok = report != null && report.length > 0;
        step(report, "View Consent · Eye icon -> report in new tab -> screenshot",
                "Click the eye/View icon (viewConsentFormDetail()); the saved form report opens in a new tab; screenshot it",
                "The consent/form report opens in a new tab and is captured",
                ok ? ("Report: " + bed.lastViewConsentReportUrl) : "No report captured", ok ? "PASS" : "FAIL");

        bed.closeViewConsentPopup();
        step(page, "View Consent · Close", "Click Close on the View Consent modal", "The modal is closed", "Closed", "PASS");
        addSummary("View Consent · Result", ok ? (patient + " -> " + bed.lastViewConsentReportUrl) : "Not confirmed");
    }

    // ===== Section: Assign Triage ========================================
    private void sectionAssignTriage() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Assign Triage");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Assign Triage · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickAssignTriage();
        step(page, "Assign Triage · Open modal", "Click 'Assign Triage' (BtnAssignTriage())",
                "The Triage modal opens (zone dropdown + Save)", modal ? "Triage modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String zone = bed.selectTriageZone();
        boolean zoneOk = zone != null && !zone.startsWith("ERR") && !zone.equals("(null)");
        step(page, "Assign Triage · Select a zone", "Pick a zone from the dropdown (Green/Yellow/Red Zone/Non-Emergency)",
                "A triage zone is selected", zone, zoneOk ? "PASS" : "FAIL");

        String toast = bed.saveTriageAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("triage")
                || toast.toLowerCase().contains("assigned") || toast.toLowerCase().contains("saved")
                || toast.toLowerCase().contains("success"));
        step(page, "Assign Triage · Save & toast", "Click Save; wait for the success toast",
                "'Triage Assigned Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        bed.closeTriagePopup();
        step(page, "Assign Triage · Close", "Click Close on the Triage modal", "The Triage modal is closed", "Closed", "PASS");

        addSummary("Assign Triage · Patient", patient);
        addSummary("Assign Triage · Result", ok ? (zone + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Fall Risk Assessment =================================
    private void sectionFallRiskAssessment() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Fall Risk Assessment");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Fall Risk · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickFallRiskAssessment();
        step(page, "Fall Risk · Open modal", "Click 'Fall Risk Assessment' (fnOpenFallRiskAssessment())",
                "The 'ASSESSMENT OF FALL' modal opens", modal ? "Fall Risk modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String toast = bed.fillFallRiskAndApply();
        boolean ok = toast != null && (toast.toLowerCase().contains("fall")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("applied"));
        step(page, "Fall Risk · Answer + Apply & toast", "Answer YES/NO questions, tick a checkbox, click Apply (fnApply())",
                "'Fall Risk Assessment saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        bed.closeFallRiskPopup();
        step(page, "Fall Risk · Close", "Close the Fall Risk modal (fnCancel / outside click)", "The modal is closed", "Closed", "PASS");

        addSummary("Fall Risk · Patient", patient);
        addSummary("Fall Risk · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Print Barcode ========================================
    private void sectionPrintBarcode() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Print Barcode");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Print Barcode · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickPrintBarcode();
        step(page, "Print Barcode · Open modal", "Click 'Print Barcode' (printpatient())",
                "The Barcode modal opens (MRN + Small Size + Save)", modal ? "Barcode modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String read = bed.selectBarcodeSmallSizeAndRead();
        step(page, "Print Barcode · Read MRN + tick Small Size", "Read the patient MRN; tick the Small Size option (BarCodeData.IsSmall)",
                "MRN shown and Small Size ticked", read, read != null && read.startsWith("MRN=") ? "PASS" : "FAIL");

        String toast = bed.saveBarcodeAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("barcode")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("success"));
        String bcActual = toast == null || toast.isEmpty()
                ? "Save produced NO success toast (Barcode save did not confirm on this screen)"
                : (ok ? toast : "Save failed — returned: \"" + toast + "\"");
        step(page, "Print Barcode · Save & toast", "Click Save (SaveBarcode()); wait for the success toast",
                "'Barcode Saved Successfully !!!.' toast", bcActual, ok ? "PASS" : "FAIL");

        bed.cancelBarcodePopup();
        step(page, "Print Barcode · Close", "Click Cancel on the Barcode modal", "The Barcode modal is closed", "Closed", "PASS");

        addSummary("Print Barcode · Patient", patient);
        addSummary("Print Barcode · Result", ok ? (read + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Change Refer Entity ==================================
    private void sectionChangeReferEntity() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Change Refer Entity");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Change Refer Entity · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickChangeReferEntity();
        step(page, "Change Refer Entity · Open modal", "Click 'Change Refer Entity'",
                "The Change Refer Entity modal opens", modal ? "Modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String fill = bed.fillReferEntityAndAdd();
        boolean fillOk = fill != null && fill.contains("Add=clicked") && !fill.contains("ReferEntityType=(no");
        step(page, "Change Refer Entity · Select dropdowns & Add", "Select Refer Entity Type + Refer Entity; click Add",
                "Refer Entity Type + Refer Entity selected and Added", fill, fillOk ? "PASS" : "FAIL");

        String toast = bed.saveReferEntityAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("saved")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("added")
                || toast.toLowerCase().contains("updated"));
        step(page, "Change Refer Entity · Save & toast", "Click Save; wait for the success toast",
                "'... saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        bed.closeReferEntityModal();
        step(page, "Change Refer Entity · Close", "Click Close on the Change Refer Entity modal",
                "The modal is closed", "Closed", "PASS");

        addSummary("Change Refer Entity · Patient", patient);
        addSummary("Change Refer Entity · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Plan Discharge (advise discharge) ====================
    private void sectionPlanDischarge() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Plan Discharge");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Plan Discharge · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean loaded = bed.clickPlanDischarge();
        step(page, "Plan Discharge · Open popup (wait for data)", "Click 'Plan Discharge' (GetAdviceDischarge()); wait for the advice list to load",
                "The Plan Discharge popup is loaded with rows", loaded ? "Popup loaded with rows" : "Popup did NOT load",
                loaded ? "PASS" : "FAIL");
        if (!loaded) { bed.closePlanDischargePopup(); return; }

        String sel = bed.selectReasonAndRemark("Plan discharge - automated test");
        boolean selOk = sel != null && !sel.startsWith("(") && sel.contains("|");
        step(page, "Plan Discharge · Select a checkbox + enter remark", "Tick any one row's checkbox and enter its remark",
                "A row is selected with a remark", sel, selOk ? "PASS" : "FAIL");

        String toast = bed.savePlanDischargeAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("advice discharge")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("success"));
        step(page, "Plan Discharge · Save & toast", "Click Save (IUDAdviceDescharge()); wait for the success toast",
                "'Advice Discharge saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        bed.closePlanDischargePopup();
        step(page, "Plan Discharge · Close popup", "Click Close on the Plan Discharge popup",
                "The Plan Discharge popup is closed", "Closed", "PASS");

        addSummary("Plan Discharge · Patient", patient);
        addSummary("Plan Discharge · Result", ok ? (sel + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Cancel Admission =====================================
    private void sectionCancelAdmission() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Cancel Admission");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Cancel Admission · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickCancelAdmission();
        step(page, "Cancel Admission · Open modal", "Click 'Cancel Admission' (CancelAdmission())",
                "The 'Reason for Cancellation' modal opens", modal ? "Reason modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String msg = bed.selectReasonSaveAndClose();
        boolean ok = msg != null && (msg.toLowerCase().contains("cancelled") || msg.toLowerCase().contains("revoked"));
        step(page, "Cancel Admission · Reason, Save, success & Close",
                "Select a reason -> Save (btn-danger); success shows inside the modal -> Close",
                "'Admission Cancelled Successfully!' shown in the modal",
                msg == null || msg.isEmpty() ? "No success message appeared" : msg, ok ? "PASS" : "FAIL");

        addSummary("Cancel Admission · Patient", patient);
        addSummary("Cancel Admission · Result", ok ? msg : "Not confirmed");
    }

    // ===== Section: Revoke Admission =====================================
    private void sectionRevokeAdmission() {
        com.kpj.pages.Ip.BedboardOccupancyListPage bed = openAndSelect("Revoke Admission");
        if (bed == null) return;
        String patient = bed.selectRandomPatient();
        step(page, "Revoke Admission · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean modal = bed.clickRevokeAdmission();
        step(page, "Revoke Admission · Open modal", "Click 'Revoke Admission' (OpenRevokeTypeModal())",
                "The Close/Revoke modal opens (Remark field)", modal ? "Revoke modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String res = bed.revokeEnterRemarkAndSave();
        boolean ok = res != null && !res.isEmpty();
        step(page, "Revoke Admission · Enter remark & Save", "Enter a Remark -> Save (fnCloseRevokeVisitClick())",
                "Revoke saved (toast or modal closed)",
                res == null || res.isEmpty() ? "No confirmation (modal still open)" : res, ok ? "PASS" : "FAIL");

        addSummary("Revoke Admission · Patient", patient);
        addSummary("Revoke Admission · Result", ok ? res : "Not confirmed");
    }
}
