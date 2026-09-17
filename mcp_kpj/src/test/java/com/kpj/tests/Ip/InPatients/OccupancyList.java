package com.kpj.tests.Ip.InPatients;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OccupancyList, so it is referenced by its fully-qualified name
// (com.kpj.pages.Ip.InPatients.OccupancyList) — importing it would clash with this test class name.

/**
 * TC23 - IP &gt; Inpatients &gt; <b>Occupancy List</b> ({@code #/AdmissionList}, consolidated) — one login, then
 * each footer action as its own section (each re-opens the list + searches + selects a patient):
 *
 * <ol>
 *   <li><b>Change Admission Type</b> — fill all 3 sections (Change Admission Type / Additional Doctors /
 *       Next of Kin, each expanded + screenshotted) → Save ({@code fnUpdateAdmissionType}).</li>
 *   <li><b>Expected Discharge Date</b> — enter a future date → Save ({@code IUDExpectedDischarge}).</li>
 * </ol>
 *
 * <p>The Change Admission Type modal is identical to the Bedboard Occupancy List's, so the page reuses
 * {@code com.kpj.pages.Ip.InPatients.OccupancyList} (which extends {@code BedboardOccupancyListPage}).</p>
 */
public class OccupancyList extends DevHisBase {

    public OccupancyList() { super("TC23_InpatientsOccupancyList"); }

    public static void main(String[] args) {
        OccupancyList t = new OccupancyList();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Inpatients - Occupancy List",
                "IP > Inpatients > Occupancy List",
                "One run exercising the Occupancy List footer actions: Change Admission Type and Expected Discharge Date.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionChangeAdmissionType();
        sectionExpectedDischargeDate();
        sectionPlanDischarge();
        sectionChangeReferEntity();
        sectionConsentForms();
        sectionViewConsent();
        sectionPrintBarcode();
        sectionAssignTriage();
        sectionAttachSignature();
        sectionCancelAdmission();
        sectionCloseAdmission();

        addSummary("Application URL", BASE + "/" + com.kpj.pages.Ip.InPatients.OccupancyList.OCC_ROUTE);
    }

    /** Open Occupancy List, search a 1-month range (To = today), and select a patient. Returns the page
     *  (null if it didn't open / no patient). */
    private com.kpj.pages.Ip.InPatients.OccupancyList openSearchSelect(String tag) {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = new com.kpj.pages.Ip.InPatients.OccupancyList(page);
        boolean opened = occ.navigateViaMenu();
        step(page, tag + " · Open Occupancy List", "Click IP -> Occupancy List",
                "The Occupancy List is shown", opened ? "Opened (#/AdmissionList)" : "Did NOT reach the list",
                opened ? "PASS" : "FAIL");
        if (!opened) return null;
        String range = occ.searchOneMonthToToday();
        step(page, tag + " · Search (1-month range, To = today)", "Set From = today-1month, To = today; click Search",
                "Admitted patients are listed", "Searched " + range, "PASS");
        String patient = occ.selectRandomPatient();
        step(page, tag + " · Select a patient", "Select any admitted patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        return patient == null ? null : occ;
    }

    // ===== Section: Change Admission Type ================================
    private void sectionChangeAdmissionType() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Change Admission Type");
        if (occ == null) return;

        boolean modal = occ.clickChangeAdmissionType();
        step(page, "Change Admission Type · Open modal", "Click 'Change Admission Type' (ChangeAdmissionType())",
                "The Change Admission Type modal opens", modal ? "Modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String s1 = occ.fillChangeTypeSection();
        occ.focusSection("Change Admission Type");
        boolean s1ok = s1 != null && s1.contains("doctor=") && !s1.contains("doctor=null");
        step(page, "Change Admission Type · Fill Section 1", "Admission Type + Department (fnSetDoctorAdmissionType loads doctors) + Doctor",
                "Section 1 populated", s1, s1ok ? "PASS" : "FAIL");

        String s2 = occ.fillAdditionalDoctorsSection();
        occ.focusSection("Additional Doctors");
        boolean s2ok = s2 != null && s2.contains("classification=") && !s2.contains("classification=null");
        step(page, "Change Admission Type · Fill Additional Doctors", "Set Classification + Additional Doctor",
                "Classification + Additional Doctor set", s2, s2ok ? "PASS" : "FAIL");

        String s3 = occ.fillNokSection();
        occ.focusSection("Next of Kin");
        boolean s3ok = s3 != null && s3.contains("NOK set");
        step(page, "Change Admission Type · Fill Next of Kin", "FillKinDropDown -> Title/Relationship/Receivable/Occupation + Name/Mobile/Address",
                "Next-of-Kin details set", s3, s3ok ? "PASS" : "FAIL");

        String toast = occ.saveAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("updated"));
        step(page, "Change Admission Type · Save & toast", "Click Save (fnUpdateAdmissionType())",
                "'... Updated Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Change Admission Type · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Expected Discharge Date =============================
    private void sectionExpectedDischargeDate() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Expected Discharge Date");
        if (occ == null) return;

        boolean modal = occ.clickExpectedDischargeDate();
        step(page, "Expected Discharge Date · Open modal", "Click 'Expected Discharge Date' (OpenExpectedDischargeModal())",
                "The Expected Discharge Date modal opens", modal ? "Modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String toast = occ.setFutureDateAndSaveExpectedDischarge();
        boolean ok = toast != null && (toast.toLowerCase().contains("expected discharge")
                || toast.toLowerCase().contains("added") || toast.toLowerCase().contains("success"));
        step(page, "Expected Discharge Date · Enter future date & Save",
                "Enter a future date (today+7); Save (IUDExpectedDischarge())",
                "'Expected Discharge Date Added Succesfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Expected Discharge Date · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Plan Discharge (advise discharge) ===================
    private void sectionPlanDischarge() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Plan Discharge");
        if (occ == null) return;

        boolean loaded = occ.clickPlanDischarge();
        step(page, "Plan Discharge · Open popup (wait for data)", "Click 'Plan Discharge' (GetAdviceDischarge()); wait for the advice list to load",
                "The Plan Discharge popup is loaded with rows", loaded ? "Popup loaded with rows" : "Popup did NOT load",
                loaded ? "PASS" : "FAIL");
        if (!loaded) return;

        String sel = occ.selectReasonAndRemark("Plan discharge - automated test");
        boolean selOk = sel != null && sel.contains("|") && !sel.startsWith("(");
        step(page, "Plan Discharge · Select a reason + enter remark", "Tick a reason/department row and enter its remark",
                "A reason is selected and the remark is entered", sel, selOk ? "PASS" : "FAIL");

        String toast = occ.savePlanDischargeAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("advice discharge saved")
                || toast.toLowerCase().contains("saved successfully") || toast.toLowerCase().contains("success"));
        step(page, "Plan Discharge · Save & toast", "Click Save (IUDAdviceDescharge()); wait for the success toast",
                "'Advice Discharge saved successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        occ.closePlanDischargePopup();
        addSummary("Plan Discharge · Result", ok ? (sel + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Change Refer Entity =================================
    private void sectionChangeReferEntity() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Change Refer Entity");
        if (occ == null) return;

        boolean modal = occ.clickChangeReferEntity();
        step(page, "Change Refer Entity · Open popup", "Click 'Change Refer Entity' (OpenRefEntityModal())",
                "The Change Refer Entity popup opens", modal ? "Popup opened" : "Popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String sel = occ.selectReferEntityTypeAndEntity();
        boolean selOk = sel != null && sel.contains("|") && !sel.contains("(no") && !sel.trim().endsWith("|");
        step(page, "Change Refer Entity · Select type + entity", "Select the Refer Entity Type (loads entities), then a Refer Entity",
                "A Refer Entity Type and Refer Entity are selected", sel, selOk ? "PASS" : "FAIL");

        boolean added = occ.clickAddReferEntity();
        step(page, "Change Refer Entity · Add", "Click Add (AddRefEntity())",
                "The refer entity is added to the table", added ? "Row added" : "No row added",
                added ? "PASS" : "FAIL");

        String toast = occ.saveReferEntityAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("refer entity added")
                || toast.toLowerCase().contains("succes"));
        step(page, "Change Refer Entity · Save & toast", "Click Save (IUDRefEntity()); wait for the success toast",
                "'Refer Entity Added Succesfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        occ.closeReferEntityPopup();
        addSummary("Change Refer Entity · Result", ok ? (sel + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Consent / Forms (external-form document flow) ========
    private void sectionConsentForms() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Consent/Forms");
        if (occ == null) return;

        boolean modal = occ.clickConsentForms();
        step(page, "Consent/Forms · Open popup", "Click 'Consent/Forms' (fnOnConsentClick())",
                "The Consent/Forms popup opens", modal ? "Popup opened" : "Popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String form = occ.selectExternalConsentForm();
        boolean formOk = form != null && form.contains("ext=") && !form.startsWith("(");
        step(page, "Consent/Forms · Select a form", "Pick a form from the search dropdown (an external form, so Save opens a document tab)",
                "A form is selected", form, formOk ? "PASS" : "FAIL");
        if (!formOk) { occ.closeConsentFormsPopup(); return; }

        boolean added = occ.clickAddConsent();
        step(page, "Consent/Forms · Add", "Click Add (Getconsenttemplate()); the external form template loads",
                "The form template is loaded (externalformmappingid set)",
                added ? "Template loaded (external form)" : "Template did NOT load", added ? "PASS" : "FAIL");
        if (!added) { occ.closeConsentFormsPopup(); return; }

        byte[] report = occ.saveConsentFormAndCaptureReport();
        boolean saveOk = report != null && report.length > 0 && occ.lastConsentSubmitConfirmed
                && occ.lastConsentReportUrl.toLowerCase().contains("/edit/");
        step(report, "Consent/Forms · Save -> Create Document -> Submit -> Report",
                "Click Save (opens a new tab); in the tab click Create Document, then Submit, confirm Yes; after the 'Form Submitted Successfully!' confirmation, screenshot the report",
                "The external-form document is submitted successfully and the report is captured",
                report == null || report.length == 0 ? "No report captured"
                        : ((occ.lastConsentSubmitConfirmed ? "OK (" + (occ.lastConsentSubmitMsg.isEmpty() ? "on /Edit" : occ.lastConsentSubmitMsg) + ") - " : "Submit not confirmed - ") + "Report: " + occ.lastConsentReportUrl),
                saveOk ? "PASS" : "FAIL");

        addSummary("Consent/Forms · Result", saveOk ? (form + " -> submitted, " + occ.lastConsentReportUrl) : "Not confirmed");
    }

    // ===== Section: View Consent (open a saved form report in a new tab) =
    private void sectionViewConsent() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("View Consent");
        if (occ == null) return;

        // Find a patient that HAS a saved consent/form so the table is populated (retry different patients — most
        // patients have none; those with a submitted external form show a row with an eye/View icon).
        String patient = "(first selected)";
        String recSummary = null;
        boolean hasRecords = false;
        int tries = 0;
        for (; tries < 8 && !hasRecords; tries++) {
            if (tries > 0) { patient = occ.selectRandomPatient(); if (patient == null) continue; }
            if (!occ.clickViewConsent()) continue;
            recSummary = occ.searchViewConsent();
            hasRecords = recSummary != null && recSummary.startsWith("records=");
            if (!hasRecords) occ.closeViewConsentPopup();
        }
        step(page, "View Consent · Open + find a patient with a saved form",
                "Click 'View Consent' (fnViewConsents()); if empty, retry different patients until one has a saved consent/form",
                "The View Consent table lists at least one consent/form (with an eye/View icon)",
                hasRecords ? (recSummary + " (patient " + patient + ", after " + tries + " tr" + (tries == 1 ? "y" : "ies") + ")")
                        : ("No patient with records in " + tries + " tries (last: " + recSummary + ")"),
                hasRecords ? "PASS" : "FAIL");
        if (!hasRecords) { occ.closeViewConsentPopup(); return; }

        byte[] report = occ.clickConsentEyeAndCaptureReport(null);
        boolean ok = report != null && report.length > 0;
        step(report, "View Consent · Eye icon -> report in new tab -> screenshot",
                "Click the eye/View icon (viewConsentFormDetail()); the saved form report opens in a new tab; screenshot it",
                "The consent/form report opens in a new tab and is captured",
                ok ? ("Report: " + occ.lastViewConsentReportUrl) : "No report captured", ok ? "PASS" : "FAIL");

        occ.closeViewConsentPopup();
        addSummary("View Consent · Result", ok ? (patient + " -> " + occ.lastViewConsentReportUrl) : "Not confirmed");
    }

    // ===== Section: Print Barcode =======================================
    private void sectionPrintBarcode() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Print Barcode");
        if (occ == null) return;

        boolean modal = occ.clickPrintBarcode();
        step(page, "Print Barcode · Open popup", "Click 'Print Barcode' (printpatient())",
                "The Barcode popup opens (MRN + Small Size + Save)", modal ? "Barcode popup opened" : "Popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String sel = occ.selectBarcodeSmallSizeAndRead();
        boolean selOk = sel != null && sel.contains("Small Size=ticked");
        step(page, "Print Barcode · Tick the checkbox (Small Size)", "Tick the Small Size checkbox (BarCodeData.IsSmall)",
                "The Small Size checkbox is ticked", sel, selOk ? "PASS" : "FAIL");

        String toast = occ.saveBarcodeAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("barcode")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("success"));
        step(page, "Print Barcode · Save & toast", "Click Save (SaveBarcode()); wait for the success toast",
                "'Barcode Saved Successfully !!!.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        occ.cancelBarcodePopup();
        step(page, "Print Barcode · Cancel", "Click Cancel to close the Barcode popup",
                "The Barcode popup is closed", "Popup closed (Cancel)", "PASS");

        addSummary("Print Barcode · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Assign Triage =======================================
    private void sectionAssignTriage() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Assign Triage");
        if (occ == null) return;

        boolean modal = occ.clickAssignTriage();
        step(page, "Assign Triage · Open popup", "Click 'Assign Triage' (BtnAssignTriage())",
                "The Triage popup opens (zone dropdown + Save)", modal ? "Triage popup opened" : "Popup did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String zone = occ.selectTriageZone();
        boolean zoneOk = zone != null && !zone.startsWith("ERR") && !zone.equals("(null)");
        step(page, "Assign Triage · Select a value from the dropdown", "Pick a triage zone from the dropdown",
                "A triage zone is selected", zone, zoneOk ? "PASS" : "FAIL");
        if (!zoneOk) { occ.closeTriagePopup(); return; }

        String toast = occ.saveTriageAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("triage")
                || toast.toLowerCase().contains("assigned") || toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("saved"));
        step(page, "Assign Triage · Save & toast", "Click Save; wait for the success toast",
                "'Triage Assigned Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        occ.closeTriagePopup();
        addSummary("Assign Triage · Result", ok ? (zone + " -> " + toast) : "Not confirmed");
    }

    // ===== Section: Attach Signature ====================================
    private static final String SIGNATURE_FILE = "C:\\Users\\Siva Sankar\\Downloads\\sinature.jpeg";

    private void sectionAttachSignature() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Attach Signature");
        if (occ == null) return;

        String toast = occ.attachSignatureAndGetToast(SIGNATURE_FILE);
        boolean ok = toast != null && (toast.toLowerCase().contains("signature")
                || toast.toLowerCase().contains("saved") || toast.toLowerCase().contains("success")
                || toast.toLowerCase().contains("uploaded"));
        step(page, "Attach Signature · Upload signature & toast",
                "Click 'Attach Signature' and upload " + SIGNATURE_FILE + " (set on the hidden #PhotoData input)",
                "'Digital Signature Saved Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Attach Signature · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Cancel Admission ====================================
    private void sectionCancelAdmission() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Cancel Admission");
        if (occ == null) return;

        boolean modal = occ.clickCancelAdmission();
        step(page, "Cancel Admission · Open modal", "Click 'Cancel Admission' (CancelAdmission())",
                "The 'Reason for Cancellation' modal opens", modal ? "Reason modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String msg = occ.selectReasonSaveAndClose();
        boolean ok = msg != null && (msg.toLowerCase().contains("cancelled") || msg.toLowerCase().contains("revoked"));
        // Use the screenshot captured WHILE the success message was visible (before Close), so the report shows it.
        step(occ.lastCancelSuccessPng, "Cancel Admission · Reason, Save & success message",
                "Select a reason -> Save (btn-danger); wait for the success message inside the popup, then screenshot it",
                "'Admission Cancelled Successfully!' shown in the modal",
                msg == null || msg.isEmpty() ? "No success message appeared" : msg, ok ? "PASS" : "FAIL");

        addSummary("Cancel Admission · Result", ok ? msg : "Not confirmed");
    }

    // ===== Section: Close Admission -> Revoke Admission (same patient) ===
    private void sectionCloseAdmission() {
        com.kpj.pages.Ip.InPatients.OccupancyList occ = openSearchSelect("Close Admission");
        if (occ == null) return;
        String mrn = occ.getSelectedMrn();   // capture the patient so we can Revoke the SAME one after Close

        boolean modal = occ.clickCloseAdmission();
        step(page, "Close Admission · Open modal", "Click 'Close Admission' (OpenRevokeTypeModal('closeAdmission'))",
                "The Close Admission modal opens (Remark field)", modal ? "Close modal opened" : "Modal did NOT open",
                modal ? "PASS" : "FAIL");
        if (!modal) return;

        String toast = occ.revokeEnterRemarkAndSave();
        boolean ok = toast != null && (toast.toLowerCase().contains("closed")
                || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved")
                || toast.contains("modal closed"));
        step(page, "Close Admission · Enter remark & Save", "Enter a Remark -> Save (fnCloseRevokeVisitClick('closeAdmission'))",
                "'Admission Closed Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("Close Admission · Result", ok ? toast : "Not confirmed");
        if (!ok) return;

        // ===== Revoke the SAME patient (right after Close) =====
        // A just-closed admission leaves the default inpatient list — re-find it via the 'Closed Admission' filter,
        // re-select by MRN, then Revoke: Revoke Admission -> enter remark -> Save -> success toast.
        boolean re = occ.reselectClosedByMrn(mrn);
        step(page, "Revoke Admission · Re-find the closed patient",
                "Tick 'Closed Admission' (wide date range), Search until MRN " + mrn + " appears, then select it",
                "The just-closed patient is re-selected",
                re ? "Re-selected MRN " + mrn : "Could not re-find MRN " + mrn, re ? "PASS" : "FAIL");
        if (!re) return;

        boolean rvModal = occ.clickRevokeAdmission();
        step(page, "Revoke Admission · Open modal", "Click 'Revoke Admission' (OpenRevokeTypeModal())",
                "The Revoke modal opens (Remark field)", rvModal ? "Revoke modal opened" : "Modal did NOT open",
                rvModal ? "PASS" : "FAIL");
        if (!rvModal) return;

        String rvToast = occ.revokeEnterRemarkAndSave();
        // Require an actual REVOKED confirmation — a stale "Admission Closed Successfully." (from the Close step) or a
        // generic "success" must NOT pass this step.
        boolean rvOk = rvToast != null && rvToast.toLowerCase().contains("revok");
        step(page, "Revoke Admission · Enter remark & Save", "Enter a Remark -> Save (fnCloseRevokeVisitClick())",
                "'Admission Revoked Successfully.' toast",
                rvToast == null || rvToast.isEmpty() ? "No success toast appeared" : rvToast, rvOk ? "PASS" : "FAIL");
        addSummary("Revoke Admission · Patient", mrn);
        addSummary("Revoke Admission · Result", rvOk ? rvToast : "Not confirmed");
    }
}
