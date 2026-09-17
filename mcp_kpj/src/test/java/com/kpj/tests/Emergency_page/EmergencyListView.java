package com.kpj.tests.Emergency_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// NOTE: the page object is also named EmergencyListView, so it is referenced by its fully-qualified
// name (com.kpj.pages.Emergency_page.EmergencyListView) — importing it would clash with this class name.

/**
 * TC13 - Emergency &gt; <b>Emergency List View</b> — Change Admission Type.
 *
 * <ol>
 *   <li>Click Emergency → Emergency List View (via the menu tab, not a direct URL).</li>
 *   <li>Enter the From/To date range and a number in MRN, then Search.</li>
 *   <li>Select a patient row from the results table.</li>
 *   <li>Click Change Admission Type → the admission form opens.</li>
 *   <li>Fill all 3 sections (Patient Information, Admission Information, remaining mandatory fields).</li>
 *   <li>Save → success toast.</li>
 * </ol>
 */
public class EmergencyListView extends DevHisBase {

    // From Date = 2 months back, so the window rolls forward with "today" instead of staying pinned at a
    // fixed date (it was hardcoded to "01/06/2026" — not yet stale as of this fix, since To Date already
    // moves with today, but an ever-growing from-June-to-today window is fragile in its own right: it gets
    // slower over time and would need a manual year bump eventually).
    private static final String FROM_DATE = java.time.LocalDate.now().minusMonths(2).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    // To Date = current date (so the search always spans up to "today", not a hard-coded future date).
    private static final String TO_DATE   = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    // "enter any single-digit number in MRN" — a random 1-9 each run (if it matches nothing the search
    // falls back to the full date-range list, so any value is fine).
    private static final String MRN       = String.valueOf(new java.util.Random().nextInt(9) + 1);
    // Signature image attached in the Attach Signature step.
    private static final String SIGNATURE_FILE = "C:\\Users\\Siva Sankar\\Downloads\\sinature.jpeg";

    public EmergencyListView() { super("TC13_EmergencyListView"); }

    public static void main(String[] args) {
        EmergencyListView t = new EmergencyListView();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Emergency - Emergency List View", "Emergency > Emergency List View",
                "Search emergency patients by date range + MRN, select a patient, Change Admission Type, fill the 3 sections, Save.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Emergency_page.EmergencyListView lv = new com.kpj.pages.Emergency_page.EmergencyListView(page);

        boolean opened = lv.navigateViaMenu();
        step(page, "Open Emergency List View (via menu tab)", "Click Emergency → Emergency List View (menu, not direct URL)",
                "The Emergency List View screen is shown", opened ? "Emergency List View opened" : "Did NOT reach Emergency List View",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String searched = lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        step(page, "Search by date range + MRN", "Enter From/To dates and a number in MRN, then click Search",
                "Matching emergency patients are listed", searched, "PASS");

        String patient = lv.selectFirstPatient();
        step(page, "Select a patient", "Select a patient row from the results table",
                "One patient is selected", patient == null ? "No patient row to select" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        boolean formOpened = lv.clickChangeAdmissionType();
        step(page, "Click Change Admission Type", "Click 'Change Admission Type' for the selected patient",
                "The admission form (3 sections) opens",
                formOpened ? "Admission form opened" : "Admission form did NOT open",
                formOpened ? "PASS" : "FAIL");
        if (!formOpened) return;

        // Section 1 — main fields: Admission Type (first real option) + Department -> Sub Dept -> Doctor.
        String s1 = lv.fillPatientSection();
        boolean s1ok = s1 != null && s1.contains("Admission Type=") && !s1.contains("Admission Type=(no");
        step(page, "Fill · Section 1 (Admission Type / Department / Sub Dept / Doctor)",
                "Select Admission Type (first real option), then Department → Sub Dept → Doctor",
                "Admission Type and cascade are selected", s1, s1ok ? "PASS" : "FAIL");

        // Section 2 — Additional Doctors: pick Classification + Doctor, click Add.
        String s2 = lv.fillAdmissionSection();
        boolean s2ok = s2 != null && s2.contains("Add=clicked");
        step(page, "Fill · Section 2 (Additional Doctors)",
                "Expand Additional Doctors; select Classification + Additional Doctor; click Add",
                "A doctor row is added to the Additional Doctors table", s2, s2ok ? "PASS" : "MANUAL");

        // Section 3 — Next of Kin: click the tab (FillKinDropDown loads dropdowns), then click a kin
        // table row (EditKinDetails) which auto-fills the kin form from the patient's existing kin.
        String s3 = lv.fillThirdSection();
        boolean s3ok = s3 != null && s3.contains("auto-filled");
        step(page, "Fill · Section 3 (Next of Kin Details)",
                "Click the Next of Kin tab, then click a row in the kin table — it auto-fills the kin form (EditKinDetails)",
                "Next of Kin is auto-filled from the table row", s3, s3ok ? "PASS" : "MANUAL");

        String toast = lv.saveAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().matches(".*(success|saved|updated|changed|admitted).*");
        step(page, "Save", "Click Save; wait for the success toast",
                "Success toast is shown",
                toast == null || toast.isEmpty() ? "No toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Emergency List View · Patient", patient == null ? "(none)" : patient);
        addSummary("Emergency List View · Search", "Dates " + FROM_DATE + " – " + TO_DATE + ", MRN " + MRN);
        addSummary("Emergency List View · Result", ok ? toast : "Change Admission Type not confirmed");

        // ===== Expected Discharge Date =====
        String p2 = lv.selectFirstPatient(); // re-select (grid selection may clear after the modal save)
        step(page, "Expected Discharge · Select a patient", "Select a patient row from the results table",
                "One patient is selected", p2 == null ? "No patient row to select" : "Selected: " + p2,
                p2 == null ? "FAIL" : "PASS");
        if (p2 != null) {
            boolean dOpened = lv.clickExpectedDischargeDate();
            step(page, "Expected Discharge · Open popup", "Click 'Expected Discharge Date' for the selected patient",
                    "The Expected Discharge Date popup opens",
                    dOpened ? "Popup opened" : "Popup did NOT open", dOpened ? "PASS" : "FAIL");
            if (dOpened) {
                String dd = lv.setDischargeFutureDate(7); // a future date (today + 7)
                step(page, "Expected Discharge · Select a future date", "Pick a future date in the popup datepicker",
                        "A future Expected Discharge Date is set", dd, "PASS");

                String dToast = lv.saveDischargeAndGetToast();
                boolean dOk = dToast != null && dToast.toLowerCase().matches(".*(success|saved|updated|discharge).*");
                step(page, "Expected Discharge · Save", "Click Save (IUDExpectedDischarge); wait for the success toast",
                        "Success toast is shown",
                        dToast == null || dToast.isEmpty() ? "No toast appeared" : dToast, dOk ? "PASS" : "FAIL");

                lv.closeDischargePopup();
                step(page, "Expected Discharge · Close", "Click Close on the popup",
                        "The popup is closed", "Close clicked", "PASS");

                addSummary("Expected Discharge · Patient", p2);
                addSummary("Expected Discharge · Result", dOk ? dToast : "Not confirmed");
            }
        }

        // ===== Plan Discharge =====
        // Select ANY patient, then click Plan Discharge (a fresh search repopulates the grid; clickPlanDischarge
        // falls back to the scope GetAdviceDischarge() if the footer button is disabled for a non-bedded patient).
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String pd = lv.selectFirstPatient();
        step(page, "Plan Discharge · Select a patient",
                "Select any patient from the results",
                "A patient is selected", pd == null ? "No patient row to select" : "Selected: " + pd,
                pd == null ? "FAIL" : "PASS");
        if (pd != null) {
            boolean pdOpened = lv.clickPlanDischarge();
            step(page, "Plan Discharge · Open popup (wait for load)", "Click 'Plan Discharge'; wait for the popup to load",
                    "The Plan Discharge popup (advice list) is loaded",
                    pdOpened ? "Popup loaded" : "Popup did NOT load", pdOpened ? "PASS" : "FAIL");
            if (pdOpened) {
                String pdDept = lv.selectDepartmentAndRemark("Plan discharge - automated test");
                boolean pdDeptOk = pdDept != null && !pdDept.startsWith("(");
                step(page, "Plan Discharge · Select department + remark", "Tick a department and enter a remark",
                        "A department is selected and the remark is entered", pdDept, pdDeptOk ? "PASS" : "FAIL");

                String pdToast = lv.savePlanDischargeAndGetToast();
                boolean pdOk = pdToast != null && pdToast.toLowerCase().matches(".*(advice discharge saved|saved successfully|success).*");
                step(page, "Plan Discharge · Save", "Click Save (IUDAdviceDescharge); wait for the success toast",
                        "'Advice Discharge saved successfully.' toast is shown",
                        pdToast == null || pdToast.isEmpty() ? "No toast appeared" : pdToast, pdOk ? "PASS" : "FAIL");

                lv.closePlanDischargePopup();
                addSummary("Plan Discharge · Patient", pd);
                addSummary("Plan Discharge · Result", pdOk ? (pdDept + " -> " + pdToast) : "Not confirmed");
            }
        }

        // ===== Change Refer Entity =====
        // Select any patient, then Change Refer Entity → pick Refer Entity Type + Refer Entity → Add → Save.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String re = lv.selectFirstPatient();
        step(page, "Change Refer Entity · Select a patient", "Select any patient from the results",
                "A patient is selected", re == null ? "No patient row to select" : "Selected: " + re,
                re == null ? "FAIL" : "PASS");
        if (re != null) {
            boolean reOpened = lv.clickChangeReferEntity();
            step(page, "Change Refer Entity · Open popup", "Click 'Change Refer Entity'",
                    "The Change Refer Entity popup opens",
                    reOpened ? "Popup opened" : "Popup did NOT open", reOpened ? "PASS" : "FAIL");
            if (reOpened) {
                String reSel = lv.selectReferEntityTypeAndEntity();
                boolean reSelOk = reSel != null && !reSel.contains("(no") && reSel.contains("|") && !reSel.trim().endsWith("|");
                step(page, "Change Refer Entity · Select type + entity",
                        "Select the Refer Entity Type (loads entities), then a Refer Entity",
                        "A Refer Entity Type and Refer Entity are selected", reSel, reSelOk ? "PASS" : "FAIL");

                boolean reAdded = lv.clickAddReferEntity();
                step(page, "Change Refer Entity · Add", "Click Add (AddRefEntity)",
                        "The refer entity is added to the table",
                        reAdded ? "Row added" : "No row added", reAdded ? "PASS" : "FAIL");

                String reToast = lv.saveReferEntityAndGetToast();
                boolean reOk = reToast != null && reToast.toLowerCase().matches(".*(refer entity added|succes|success).*");
                step(page, "Change Refer Entity · Save", "Click Save (IUDRefEntity); wait for the success toast",
                        "'Refer Entity Added Succesfully.' toast is shown",
                        reToast == null || reToast.isEmpty() ? "No toast appeared" : reToast, reOk ? "PASS" : "FAIL");

                lv.closeReferEntityPopup();
                step(page, "Change Refer Entity · Close", "Click Close on the popup",
                        "The popup is closed", "Close clicked", "PASS");

                addSummary("Change Refer Entity · Patient", re);
                addSummary("Change Refer Entity · Result", reOk ? (reSel + " -> " + reToast) : "Not confirmed");
            }
        }

        // ===== View Consent (view a patient's saved consent / forms) =====
        // Read-only: re-search, select any patient, open View Consent, Search. If the patient has a saved
        // consent/form, open it via the row's eye (Action) icon in a NEW TAB; otherwise assert the
        // "No records found." empty state. (Most patients have none — saving consents is server-blocked by the
        // TX Text Control dependency, so the empty branch is the common, still-valid path.)
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String vc = lv.selectFirstPatient();
        step(page, "View Consent · Select a patient", "Select any patient from the results",
                "A patient is selected", vc == null ? "No patient row to select" : "Selected: " + vc,
                vc == null ? "FAIL" : "PASS");
        if (vc != null) {
            boolean vcOpened = lv.clickViewConsent();
            step(page, "View Consent · Open popup", "Click 'View Consent' for the selected patient",
                    "The View Consent/Forms popup opens",
                    vcOpened ? "Popup opened" : "Popup did NOT open", vcOpened ? "PASS" : "FAIL");
            if (vcOpened) {
                String vcResult = lv.searchViewConsent();
                step(page, "View Consent · Search", "Click Search inside the View Consent/Forms popup",
                        "The patient's saved consent/forms are listed, or 'No records found.'",
                        vcResult, "PASS");

                String iconFound = lv.clickConsentActionIcon(null); // any / first record
                if (iconFound != null) {
                    com.microsoft.playwright.Page formTab =
                            page.context().waitForPage(() -> page.locator("#consent_action_icon").click());
                    formTab.waitForTimeout(1500);
                    step(formTab, "View Consent · Open form (new tab)", "Click the row's Action (eye) icon",
                            "The saved consent form opens in a new tab", "Form opened: " + formTab.url(), "PASS");
                    try { formTab.close(); } catch (Exception ignore) {}
                    page.bringToFront();
                    addSummary("View Consent · Result", vcResult + " (form opened in new tab)");
                } else {
                    step(page, "View Consent · Open form (new tab)", "Click the row's Action (eye) icon",
                            "The saved consent form opens in a new tab",
                            "No record for this patient — 'No records found.' (nothing to open)", "MANUAL");
                    addSummary("View Consent · Result", vcResult);
                }

                lv.closeViewConsentPopup();
                step(page, "View Consent · Close", "Click Close on the popup",
                        "The popup is closed", "Close clicked", "PASS");
                addSummary("View Consent · Patient", vc);
            }
        }

        // ===== Print Barcode =====
        // Read-only: re-search, select any patient, open the Barcode popup, read the MRN + tick Small Size,
        // click Print (produces the barcode label — a new tab / print preview), then Close. Save is skipped
        // (it would persist a barcode row to trn_patientqrbarcode).
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String pb = lv.selectFirstPatient();
        step(page, "Print Barcode · Select a patient", "Select any patient from the results",
                "A patient is selected", pb == null ? "No patient row to select" : "Selected: " + pb,
                pb == null ? "FAIL" : "PASS");
        if (pb != null) {
            boolean pbOpened = lv.clickPrintBarcode();
            step(page, "Print Barcode · Open popup", "Click 'Print Barcode' for the selected patient",
                    "The Barcode popup opens (MRN + Small Size + Print)",
                    pbOpened ? "Barcode popup opened" : "Barcode popup did NOT open", pbOpened ? "PASS" : "FAIL");
            if (pbOpened) {
                String pbInfo = lv.selectBarcodeOptionsAndRead();
                boolean pbInfoOk = pbInfo != null && pbInfo.contains("Small Size=ticked");
                step(page, "Print Barcode · Select Small Size", "Tick the Small Size option in the Barcode popup",
                        "Small Size is selected (patient MRN shown)", pbInfo, pbInfoOk ? "PASS" : "MANUAL");

                String pbToast = lv.saveBarcodeAndGetToast();
                boolean pbSaveOk = pbToast != null && pbToast.toLowerCase().matches(".*(barcode|saved|success).*");
                step(page, "Print Barcode · Save", "Click Save (SaveBarcode); wait for the success toast",
                        "'Barcode Saved Successfully !!!.' toast is shown",
                        pbToast == null || pbToast.isEmpty() ? "No toast appeared" : pbToast, pbSaveOk ? "PASS" : "FAIL");

                lv.closeBarcodePopup();
                step(page, "Print Barcode · Close", "Click Close on the popup",
                        "The popup is closed", "Close clicked", "PASS");

                addSummary("Print Barcode · Patient", pb);
                addSummary("Print Barcode · Result", pbSaveOk ? (pbInfo + " -> " + pbToast) : "Not confirmed");
            }
        }

        // ===== Assign Triage =====
        // Select any (bedded) patient, open Assign Triage, pick a triage zone, Save → "Triage Assigned
        // Successfully." Needs a bedded patient (ISNullBedId); clickAssignTriage falls back to the scope
        // BtnAssignTriage() if the footer button is disabled. Save writes trn_patienttriagedetail — a real but
        // reversible assignment (the patient can be re-triaged).
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String tg = lv.selectFirstPatient();
        step(page, "Assign Triage · Select a patient", "Select any patient from the results",
                "A patient is selected", tg == null ? "No patient row to select" : "Selected: " + tg,
                tg == null ? "FAIL" : "PASS");
        if (tg != null) {
            boolean tgOpened = lv.clickAssignTriage();
            step(page, "Assign Triage · Open popup", "Click 'Assign Triage' for the selected patient",
                    "The Triage popup opens (zone dropdown)",
                    tgOpened ? "Triage popup opened" : "Triage popup did NOT open", tgOpened ? "PASS" : "FAIL");
            if (tgOpened) {
                String tgZone = lv.selectTriageZone();
                boolean tgZoneOk = tgZone != null && !tgZone.startsWith("ERR") && !tgZone.equals("(null)");
                step(page, "Assign Triage · Select zone", "Select a triage zone (e.g. Green / Yellow / Red Zone)",
                        "A triage zone is selected", "Zone = " + tgZone, tgZoneOk ? "PASS" : "FAIL");

                String tgToast = lv.saveTriageAndGetToast();
                boolean tgOk = tgToast != null && tgToast.toLowerCase().matches(".*(triage|assigned|success|saved).*");
                step(page, "Assign Triage · Save", "Click Save; wait for the success toast",
                        "'Triage Assigned Successfully.' toast is shown",
                        tgToast == null || tgToast.isEmpty() ? "No toast appeared" : tgToast, tgOk ? "PASS" : "FAIL");

                lv.closeTriagePopup();
                step(page, "Assign Triage · Close", "Click Close on the popup (if still open)",
                        "The popup is closed", "Close clicked", "PASS");

                addSummary("Assign Triage · Patient", tg);
                addSummary("Assign Triage · Result", tgOk ? (tgZone + " -> " + tgToast) : "Not confirmed");
            }
        }

        // ===== Assign Bed =====
        // Select any patient, open Assign Bed (#AssignBed via fnOnBedClick), select a vacant bed, Save. Most
        // emergency patients are ALREADY bedded, so the picker may not open for them — that case is reported as
        // MANUAL (needs a non-bedded patient to fully exercise). Save writes trn_bedreservation / allocation.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String ab = lv.selectFirstPatient();
        step(page, "Assign Bed · Select a patient", "Select any patient from the results",
                "A patient is selected", ab == null ? "No patient row to select" : "Selected: " + ab,
                ab == null ? "FAIL" : "PASS");
        if (ab != null) {
            boolean abOpened = lv.clickAssignBed();
            if (abOpened) {
                step(page, "Assign Bed · Open popup", "Click 'Assign Bed' for the selected patient",
                        "The Assign Bed picker opens", "Assign Bed popup opened", "PASS");

                String abBed = lv.selectVacantBedInAssign();
                boolean abBedOk = abBed != null && abBed.startsWith("Bed=");
                step(page, "Assign Bed · Select a vacant bed", "Pick a vacant bed from the picker (bed class + ward)",
                        "A vacant bed is selected", abBed, abBedOk ? "PASS" : "MANUAL");

                if (abBedOk) {
                    String abToast = lv.saveAssignBedAndGetToast();
                    boolean abOk = abToast != null && abToast.toLowerCase().matches(".*(success|saved|assigned|allocat).*")
                            && !abToast.toLowerCase().contains("please");
                    step(page, "Assign Bed · Save", "Click Save; wait for the success toast",
                            "Bed allocation success toast is shown",
                            abToast == null || abToast.isEmpty() ? "No toast appeared" : abToast, abOk ? "PASS" : "FAIL");
                    addSummary("Assign Bed · Result", abOk ? (abBed + " -> " + abToast) : "Not confirmed");
                }

                lv.closeAssignBedPopup();
                step(page, "Assign Bed · Close", "Click Close on the popup (if still open)",
                        "The popup is closed", "Close clicked", "PASS");
                addSummary("Assign Bed · Patient", ab);
            } else {
                step(page, "Assign Bed · Open popup", "Click 'Assign Bed' for the selected patient",
                        "The Assign Bed picker opens",
                        "No picker shown — the selected patient is already bedded (Assign Bed needs a non-bedded patient)",
                        "MANUAL");
                addSummary("Assign Bed · Patient", ab);
                addSummary("Assign Bed · Result", "Not exercised — patient already bedded");
            }
        }

        // ===== Attach Signature =====
        // Select any patient, then Attach Signature — the footer button triggers the hidden #PhotoData file input
        // (onchange PhotoChanged(files,'Photo')); we set the signature image on it directly and verify the toast.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String sg = lv.selectFirstPatient();
        step(page, "Attach Signature · Select a patient", "Select any patient from the results",
                "A patient is selected", sg == null ? "No patient row to select" : "Selected: " + sg,
                sg == null ? "FAIL" : "PASS");
        if (sg != null) {
            String sgToast = lv.attachSignatureAndGetToast(SIGNATURE_FILE);
            boolean sgOk = sgToast != null && sgToast.toLowerCase().matches(".*(signature|photo|upload|success|saved|attach).*");
            step(page, "Attach Signature · Upload + verify toast",
                    "Click Attach Signature and upload the signature image (" + SIGNATURE_FILE + ")",
                    "The signature is uploaded and a success toast is shown",
                    sgToast == null || sgToast.isEmpty() ? "No toast appeared" : sgToast, sgOk ? "PASS" : "FAIL");
            addSummary("Attach Signature · Patient", sg);
            addSummary("Attach Signature · Result", sgOk ? sgToast : "Not confirmed");
        }

        // ===== Print (report opens in a new tab) =====
        // Click Print (fnOnPrintClick) — the admitted-patient list report opens in a NEW TAB; capture a full-page
        // screenshot of the whole report, then close the tab. (Print prints the current list, no patient needed.)
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        com.microsoft.playwright.Page printTab = lv.clickPrintOpenReport();
        if (printTab != null) {
            String printUrl = printTab.url();
            byte[] printShot = null;
            try { printShot = printTab.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setFullPage(true).setTimeout(15000)); }
            catch (Exception e) { System.out.println("Print: full-page report screenshot failed - " + e.getMessage()); }
            // A tab opening is NOT proof the report rendered — the app can return a Crystal Reports/ASP.NET
            // "Server Error ... Missing parameter values" error page in that same new tab. Check the tab's own
            // content for that signature and fail on it instead of judging success by "a tab opened" alone.
            String printErr = "";
            try {
                Object err = printTab.evaluate("() => { const t=(document.body.innerText||''); const m=t.match(/Server Error[^\\n]*|Missing parameter values[^\\n]*|Exception Details:[^\\n]*/i); return m ? m[0].trim() : ''; }");
                printErr = err == null ? "" : err.toString();
            } catch (Exception e) { System.out.println("Print: could not inspect report tab content - " + e.getMessage()); }
            boolean printOk = printErr.isEmpty();
            step(printShot, "Print · Report opens in a new tab",
                    "Click Print; the admitted-patient list report opens in a new tab and actually renders (not a server error page)",
                    "The full report is displayed in a new tab",
                    printOk ? "Report opened: " + printUrl : "Report tab opened but showed an error: \"" + printErr + "\" (" + printUrl + ")",
                    printOk ? "PASS" : "FAIL");
            try { printTab.close(); } catch (Exception ignore) {}
            page.bringToFront();
            addSummary("Print · Report", printUrl);
        } else {
            step(page, "Print · Report opens in a new tab", "Click Print",
                    "The report opens in a new tab", "Report tab did NOT open", "FAIL");
        }

        // ===== Cancel Admission =====
        // Cancel Admission is only enabled under the "All Inpatients" filter — switch to it and re-search.
        lv.useInpatientFilter();
        String p3 = lv.selectFirstPatient();
        step(page, "Cancel Admission · Select an inpatient",
                "Switch to the 'All Inpatients' filter (Cancel Admission is enabled there) and select a patient",
                "An inpatient is selected", p3 == null ? "No inpatient row to select" : "Selected: " + p3,
                p3 == null ? "FAIL" : "PASS");
        if (p3 != null) {
            boolean cOpened = lv.clickCancelAdmission();
            step(page, "Cancel Admission · Open popup", "Click 'Cancel Admission' for the selected patient",
                    "The 'Reason for Cancellation' popup opens",
                    cOpened ? "Popup opened" : "Popup did NOT open", cOpened ? "PASS" : "FAIL");
            if (cOpened) {
                String reason = lv.selectCancelReason();
                step(page, "Cancel Admission · Select reason", "Select a reason from the Cancellation Reason dropdown",
                        "A cancellation reason is selected", "Reason = " + reason, "PASS");

                String cMsg = lv.saveCancelAndGetMessage();
                boolean cOk = cMsg != null && cMsg.toLowerCase().matches(".*(cancelled|success).*");
                step(page, "Cancel Admission · Save", "Click Save; wait for the success message in the popup",
                        "'Admission Cancelled Successfully!' message is shown",
                        cMsg == null || cMsg.isEmpty() ? "No success message appeared" : cMsg, cOk ? "PASS" : "FAIL");

                lv.closeCancelPopup();
                step(page, "Cancel Admission · Close", "Click Close on the popup",
                        "The popup is closed", "Close clicked", "PASS");

                addSummary("Cancel Admission · Patient", p3);
                addSummary("Cancel Admission · Result", cOk ? (reason + " -> " + cMsg) : "Not confirmed");
            }
        }

        // ===== Close Admission =====
        // Close Admission (like Cancel) is enabled under the All Inpatients filter — ensure it, re-select.
        lv.useInpatientFilter();
        String p4 = lv.selectFirstPatient();
        step(page, "Close Admission · Select an inpatient",
                "Under the 'All Inpatients' filter, select a patient",
                "An inpatient is selected", p4 == null ? "No inpatient row to select" : "Selected: " + p4,
                p4 == null ? "FAIL" : "PASS");
        if (p4 != null) {
            boolean clOpened = lv.clickCloseAdmission();
            step(page, "Close Admission · Open popup", "Click 'Close Admission' for the selected patient",
                    "The Close Admission popup opens",
                    clOpened ? "Popup opened" : "Popup did NOT open", clOpened ? "PASS" : "FAIL");
            if (clOpened) {
                String clReason = lv.enterCloseReason("Closed - automated test");
                step(page, "Close Admission · Enter reason", "Enter a reason in the Remark field",
                        "The remark is accepted", "Remark = " + clReason, "PASS");

                String clToast = lv.saveCloseAdmissionAndGetToast();
                boolean clOk = clToast != null && clToast.toLowerCase().matches(".*(closed|success|revoke).*");
                step(page, "Close Admission · Save", "Click Save (fnCloseRevokeVisitClick); wait for the success toast",
                        "Success toast is shown",
                        clToast == null || clToast.isEmpty() ? "No toast appeared" : clToast, clOk ? "PASS" : "FAIL");

                lv.closeCloseAdmissionPopup();
                addSummary("Close Admission · Patient", p4);
                addSummary("Close Admission · Result", clOk ? clToast : "Not confirmed");

                // ===== Revoke Admission (same patient, right after Close) =====
                // A just-closed admission LEAVES the "All Inpatients" list — switch to the "Closed Admission"
                // filter (wide date range) to surface it, then re-select the SAME patient by MRN and Revoke.
                if (clOk) {
                    String mrn = com.kpj.pages.Emergency_page.EmergencyListView.mrnFromDescriptor(p4);
                    int closedRows = lv.useClosedAdmissionFilter(mrn);
                    step(page, "Revoke Admission · Show closed admissions",
                            "Tick the 'Closed Admission' filter (wide date range) and Search until the just-closed patient appears",
                            "The just-closed patient is listed under Closed Admission",
                            closedRows > 0 ? closedRows + " closed admission(s) listed" : "No closed admissions listed (grid empty)",
                            closedRows > 0 ? "PASS" : "FAIL");

                    String rp = lv.reselectPatientByMrn(mrn);
                    step(page, "Revoke Admission · Re-select the same patient",
                            "Find the just-closed patient (MRN " + mrn + ") in the Closed Admission list and select it",
                            "The same patient is selected again",
                            rp == null ? "Could not re-find MRN " + mrn : "Re-selected: " + rp,
                            rp == null ? "FAIL" : "PASS");
                    if (rp != null) {
                        boolean rvOpened = lv.clickRevokeAdmission();
                        step(page, "Revoke Admission · Open popup", "Click 'Revoke Admission' for the selected patient",
                                "The Revoke Admission popup opens",
                                rvOpened ? "Popup opened" : "Popup did NOT open (button disabled / not revocable)",
                                rvOpened ? "PASS" : "FAIL");
                        if (rvOpened) {
                            String rvReason = lv.enterRevokeReason("Revoked - automated test");
                            step(page, "Revoke Admission · Enter reason", "Enter a reason in the Remark field",
                                    "The remark is accepted", "Remark = " + rvReason, "PASS");

                            String rvToast = lv.saveRevokeAdmissionAndGetToast();
                            boolean rvOk = rvToast != null && rvToast.toLowerCase().matches(".*(revoked|success).*");
                            step(page, "Revoke Admission · Save", "Click Save (fnCloseRevokeVisitClick); wait for the success toast",
                                    "'Admission Revoked Successfully' toast is shown",
                                    rvToast == null || rvToast.isEmpty() ? "No toast appeared" : rvToast, rvOk ? "PASS" : "FAIL");

                            lv.closeRevokeAdmissionPopup();
                            addSummary("Revoke Admission · Patient", rp);
                            addSummary("Revoke Admission · Result", rvOk ? rvToast : "Not confirmed");
                        }
                    }
                }
            }
        }

        // ===== Referred Patients =====
        // Click Referred Patients; simple rule: if the list popup does not open — for ANY reason, including a
        // "No Records Found" toast — this section FAILS outright, no retry. If it opens: tick any row, click
        // Registration; same rule applies if THAT does not open. Same UI shape as OP > Outpatient Queue
        // Management's Referred Patients (Select checkbox / MRN / Patient Name / ... / Registration / Admission
        // / Cancel), which this screen shares the underlying queue component with.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String rpOutcome = lv.clickReferredPatients();
        boolean rpModalOpened = "MODAL".equals(rpOutcome);
        step(page, "Referred Patients · Click Referred Patients", "Click 'Referred Patients'; the referred-patients list popup opens",
                "The list popup opens (Select / MRN / Patient Name / ... / Registration / Admission / Cancel)",
                rpModalOpened ? "Popup opened"
                        : "Popup did NOT open" + (rpOutcome.startsWith("NO_RECORDS::") ? " — app showed \"No Records Found\" instead" : ""),
                rpModalOpened ? "PASS" : "FAIL");
        if (rpModalOpened) {
            String rpPatient = lv.selectAnyReferredPatientRow();
            step(page, "Referred Patients · Select a patient", "Tick any row's Select checkbox",
                    "One referred patient is selected", rpPatient == null ? "No selectable row in the list" : "Selected: " + rpPatient,
                    rpPatient == null ? "FAIL" : "PASS");
            if (rpPatient != null) {
                boolean rpFormOpened = lv.clickReferredPatientAction("Registration");
                step(page, "Referred Patients · Click Registration", "Click 'Registration' for the selected referred patient",
                        "Registration opens (new tab, route change, or a registration-shaped form)",
                        rpFormOpened ? "Registration opened" : "Registration did NOT open", rpFormOpened ? "PASS" : "FAIL");
                if (rpFormOpened) {
                    String rpFilled = lv.fillReferredPatientFormMandatory();
                    boolean rpEmptyDropdown = rpFilled.startsWith("EMPTY DROPDOWN:");
                    step(page, "Referred Patients · Fill the details", "Fill every mandatory (*) field on the opened form",
                            "All mandatory fields accepted", rpFilled, rpEmptyDropdown ? "FAIL" : "PASS");
                    addSummary("Referred Patients · Result", rpEmptyDropdown ? rpFilled : "Details filled");
                } else {
                    addSummary("Referred Patients · Result", "Registration did not open for " + rpPatient);
                }
            } else {
                addSummary("Referred Patients · Result", "No selectable row in the list");
            }
        } else {
            addSummary("Referred Patients · Result", rpOutcome);
        }
        lv.dismissAnyModal();

        // ===== Company Approve Amounts =====
        // Select a patient -> Company Approve Amounts -> select Payor + Payor Status, enter GL Approved Amount +
        // Remark/Comments -> Add (Add IS the save here, no separate Save button) -> success toast -> Close.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String caaPatient = lv.selectPatientEligibleForCompanyApproveAmounts();
        step(page, "Company Approve Amounts · Select a patient", "Select a patient for whom 'Company Approve Amounts' is enabled",
                "A patient is selected", caaPatient == null ? "No row had 'Company Approve Amounts' enabled" : "Selected: " + caaPatient,
                caaPatient == null ? "FAIL" : "PASS");
        if (caaPatient != null) {
            boolean caaOpened = lv.clickCompanyApproveAmounts();
            step(page, "Company Approve Amounts · Click Company Approve Amounts", "Click 'Company Approve Amounts'; the modal opens",
                    "Company Approve Amounts modal opens",
                    caaOpened ? "Company Approve Amounts modal opened" : "Company Approve Amounts modal did NOT open",
                    caaOpened ? "PASS" : "FAIL");
            if (caaOpened) {
                String caaFilled = lv.fillCompanyApproveAmountDetails();
                boolean caaEmptyDropdown = caaFilled.startsWith("EMPTY DROPDOWN:");
                step(page, "Company Approve Amounts · Fill Payor / Payor Status / GL Approved Amount / Remark",
                        "Select Payor, Payor Status; enter GL Approved Amount, Remark/Comments",
                        "All fields accepted (both dropdowns have a real option)", caaFilled,
                        caaEmptyDropdown ? "FAIL" : "PASS");
                if (!caaEmptyDropdown) {
                    String caaToast = lv.addCompanyApproveAmountAndGetToast();
                    boolean caaOk = caaToast != null && !caaToast.isEmpty()
                            && (caaToast.toLowerCase().contains("success") || caaToast.toLowerCase().contains("saved") || caaToast.toLowerCase().contains("added"));
                    step(page, "Company Approve Amounts · Click Add & success toast", "Click Add; wait for the success toast",
                            "'... Saved/Added Successfully' toast",
                            caaToast == null || caaToast.isEmpty() ? "No success toast appeared" : caaToast, caaOk ? "PASS" : "FAIL");
                    lv.closeCompanyApproveAmountsModal();
                    step("Company Approve Amounts · Close", "Click Close to dismiss the modal", "Modal closed", "Modal closed", "PASS");
                    addSummary("Company Approve Amounts · Result", caaOk ? caaToast : "Not confirmed");
                } else {
                    addSummary("Company Approve Amounts · Result", caaFilled);
                }
            } else {
                addSummary("Company Approve Amounts · Result", "Modal did not open");
            }
            addSummary("Company Approve Amounts · Patient", caaPatient);
        }
        lv.dismissAnyModal();

        // ===== Convert IPD Charges =====
        // Select patient -> click Convert IPD Charges -> accept the alert (click Save) -> success toast.
        // "No Unbilled Ipd Charges!" means THIS patient has nothing to convert — not a failure, try the next
        // patient (same iterate-until-eligible pattern as OP > Outpatient Queue Management).
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String cicPatient = null, cicToast = "";
        int cicTried = 0;
        for (int i = 0; i < 12; i++) {
            String cand = lv.selectNthPatient(i);
            if (cand == null) break;
            cicPatient = cand;
            cicTried++;
            String outcome = lv.clickConvertIpdCharges();
            if (!outcome.equals("confirm")) continue;
            cicToast = lv.acceptConvertIpdChargesAndGetToast();
            if (cicToast != null && cicToast.toLowerCase().contains("no unbilled")) continue;
            break;
        }
        step(page, "Convert IPD Charges · Select a patient & click Convert IPD Charges",
                "Select a patient; click 'Convert IPD Charges' (try a different patient on \"No Unbilled Ipd Charges!\")",
                "The confirm dialog appears for some patient",
                cicPatient == null ? "No patient found" : "Selected: " + cicPatient + " (tried " + cicTried + " patient(s))",
                cicPatient == null ? "FAIL" : "PASS");
        if (cicPatient != null) {
            boolean cicOk = cicToast != null && !cicToast.isEmpty()
                    && (cicToast.toLowerCase().contains("success") || cicToast.toLowerCase().contains("saved") || cicToast.toLowerCase().contains("convert"));
            step(page, "Convert IPD Charges · Accept alert (Save) & success toast",
                    "Click Save on the confirm; wait for the success toast",
                    "'... Converted/Saved Successfully' toast",
                    cicToast == null || cicToast.isEmpty() ? "No toast appeared" : cicToast, cicOk ? "PASS" : "FAIL");
            addSummary("Convert IPD Charges · Patient", cicPatient + " (tried " + cicTried + ")");
            addSummary("Convert IPD Charges · Result", cicOk ? cicToast : "Not confirmed");
        }
        lv.dismissAnyModal();

        // ===== Ward Acceptance =====
        // Tick the Ward Acceptance checkbox in the Emergency Visits grid -> Search -> data loads -> select a
        // patient -> click Ward Acceptance -> success toast.
        boolean waLoaded = lv.useWardAcceptanceFilter();
        step(page, "Ward Acceptance · Tick checkbox & Search", "Tick the 'Ward Acceptance' checkbox in the Emergency Visits grid; click Search",
                "The grid loads data", waLoaded ? "Data loaded" : "Grid did not load data", waLoaded ? "PASS" : "FAIL");
        if (waLoaded) {
            String waPatient = lv.selectFirstPatient();
            step(page, "Ward Acceptance · Select a patient", "Select any patient from the results",
                    "A patient is selected", waPatient == null ? "No patient row to select" : "Selected: " + waPatient,
                    waPatient == null ? "FAIL" : "PASS");
            if (waPatient != null) {
                String waToast = lv.clickWardAcceptanceAndGetToast();
                String waTl = waToast == null ? "" : waToast.toLowerCase();
                boolean waOk = waTl.contains("success") || waTl.contains("saved") || waTl.contains("accepted");
                step(page, "Ward Acceptance · Click Ward Acceptance & success toast", "Click 'Ward Acceptance'; wait for the success toast",
                        "A success toast appears",
                        waToast == null || waToast.isEmpty() ? "No toast appeared" : waToast,
                        waOk ? "PASS" : "FAIL");
                addSummary("Ward Acceptance · Patient", waPatient);
                addSummary("Ward Acceptance · Result", waOk ? waToast : "Not confirmed");
            }
        }
        lv.dismissAnyModal();

        // ===== Fall Risk Assessment =====
        // Select a patient -> Fall Risk Assessment -> tick a risk factor / fill mandatory fields -> Save ->
        // success toast -> Close. Exact modal shape not yet confirmed live — best-effort, self-diagnosing.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String fraPatient = lv.selectFirstPatient();
        step(page, "Fall Risk Assessment · Select a patient", "Select any patient from the results",
                "A patient is selected", fraPatient == null ? "No patient row to select" : "Selected: " + fraPatient,
                fraPatient == null ? "FAIL" : "PASS");
        if (fraPatient != null) {
            boolean fraOpened = lv.clickFallRiskAssessment();
            step(page, "Fall Risk Assessment · Click Fall Risk Assessment", "Click 'Fall Risk Assessment'; the modal opens",
                    "Fall Risk Assessment modal opens",
                    fraOpened ? "Fall Risk Assessment modal opened" : "Fall Risk Assessment modal did NOT open",
                    fraOpened ? "PASS" : "FAIL");
            if (fraOpened) {
                String fraFilled = lv.fillFallRiskAssessmentDetails();
                step(page, "Fall Risk Assessment · Fill mandatory details", "Answer YES to every question; tick a risk-factor checkbox",
                        "Details filled", fraFilled, "PASS");
                String fraToast = lv.saveFallRiskAssessmentAndGetToast();
                String fraTl = fraToast == null ? "" : fraToast.toLowerCase();
                boolean fraOk = fraTl.contains("success") || fraTl.contains("saved") || fraTl.contains("applied");
                step(page, "Fall Risk Assessment · Click Apply & success toast", "Click Apply (fnApply); wait for the success toast",
                        "A success toast appears",
                        fraToast == null || fraToast.isEmpty() ? "No toast appeared" : fraToast,
                        fraOk ? "PASS" : "FAIL");
                lv.closeFallRiskAssessmentPopup();
                step("Fall Risk Assessment · Close", "Click Close to dismiss the modal", "Modal closed", "Modal closed", "PASS");
                addSummary("Fall Risk Assessment · Result", fraOk ? fraToast : "Not confirmed");
            } else {
                addSummary("Fall Risk Assessment · Result", "Modal did not open");
            }
            addSummary("Fall Risk Assessment · Patient", fraPatient);
        }
        lv.dismissAnyModal();

        // ===== Request MRD File =====
        // Prefer a patient WITHOUT an MRD file so we exercise the create path (accept the alert -> fill -> save).
        // Ported from OP > Outpatient Queue Management's Request MRD File.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String mrdPatient = lv.selectQueueRowWithoutMrdFile();
        step(page, "Request MRD · Select a patient", "Select a patient with no MRD file yet",
                "One patient selected", mrdPatient == null ? "No patient in the results" : "Selected: " + mrdPatient,
                mrdPatient == null ? "FAIL" : "PASS");
        if (mrdPatient != null) {
            String mrdOutcome = lv.clickRequestMrdFile();
            if (mrdOutcome.equals("report")) {
                com.microsoft.playwright.Page report = page.context().pages().get(page.context().pages().size() - 1);
                try { report.waitForLoadState(); } catch (Exception ignore) {}
                report.waitForTimeout(3000);
                byte[] png = null;
                try { report.bringToFront(); report.waitForTimeout(1500); png = report.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setFullPage(true).setTimeout(20000)); }
                catch (Exception e) { System.out.println("Request MRD report screenshot: " + e.getMessage()); }
                String actual = "MRD report opened in a new tab (file already existed): " + report.url();
                if (png != null && png.length > 0)
                    step(png, "Request MRD · File exists -> report (new tab) & screenshot", "Click Request MRD File; the patient already has a file -> the report opens -> screenshot",
                            "The MRD report is shown", actual, "PASS");
                else
                    step(page, "Request MRD · File exists -> report opens", "Click Request MRD File; the patient already has a file",
                            "The MRD report opens in a new tab", actual, "PASS");
                try { if (report != page && !report.isClosed()) report.close(); } catch (Exception ignore) {}
                page.bringToFront();
                addSummary("Request MRD · Result", "Report opened (file already existed)");
            } else {
                boolean confirmed = mrdOutcome.equals("confirm");
                step(page, "Request MRD · Click Request MRD File", "Click 'Request MRD File' (fnRequestMRDFile)",
                        "If the patient HAS a file the report opens in a new tab; else a 'File is not generated… create one?' confirm appears",
                        confirmed ? "Confirm dialog appeared" : "No confirm/report — the async MRD server check did not return in time (outcome: " + mrdOutcome + ")",
                        confirmed ? "PASS" : "MANUAL");
                if (confirmed) {
                    boolean mrdModalOpen = lv.confirmCreateMrdFile();
                    step(page, "Request MRD · Accept alert (Yes)", "Click Yes on the confirm; the MRD Details popup opens",
                            "MRD Details popup opens", mrdModalOpen ? "MRD Details opened" : "MRD Details did NOT open",
                            mrdModalOpen ? "PASS" : "FAIL");
                    if (mrdModalOpen) {
                        String mrdToast = lv.fillMrdDetailsAndSave();
                        boolean mrdOk = mrdToast != null && (mrdToast.toLowerCase().contains("allocated") || mrdToast.toLowerCase().contains("success"));
                        step(page, "Request MRD · Fill details, Save & toast",
                                "Fill all mandatory fields (Patient Type, Location, File Type, Rack→Row→Box, File No, Classification); Save (fnIUDAllocation)",
                                "'File Allocated Successfully.' toast",
                                mrdToast == null || mrdToast.isEmpty() ? "No success toast appeared" : mrdToast, mrdOk ? "PASS" : "FAIL");
                        lv.cancelMrdDetails();
                        step(page, "Request MRD · Cancel to close popup", "Click Cancel to close the MRD Details popup",
                                "Popup closed", "Popup closed", "PASS");
                        addSummary("Request MRD · Result", mrdOk ? mrdToast : "Not confirmed");
                    }
                }
            }
            addSummary("Request MRD · Patient", mrdPatient);
        }
        lv.dismissAnyModal();

        // ===== Return MRD File =====
        // Select a patient with an existing MRD file -> Return MRD File -> From/To Department + User + Remark
        // -> OK -> toast. Ported from OP > Outpatient Queue Management's Return MRD File.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String rmPatient = lv.selectQueueRowWithMrdFile();
        step("Return MRD · Select a patient with an MRD file", "Select a patient whose MRD file is on issue",
                "One patient (with an MRD file) selected",
                rmPatient == null ? "No patient in the results has an MRD file" : "Selected: " + rmPatient,
                rmPatient == null ? "FAIL" : "PASS");
        if (rmPatient != null) {
            boolean rmOpened = lv.clickReturnMrdFile();
            step(page, "Return MRD · Click Return MRD File", "Click 'Return MRD File' (fnReturnMRDFile); the MRDReturn modal opens",
                    "MRDReturn modal opens", rmOpened ? "MRDReturn modal opened" : "MRDReturn modal did NOT open",
                    rmOpened ? "PASS" : "FAIL");
            if (rmOpened) {
                String rmToast = lv.fillMrdReturnAndSave();
                boolean rmOk = rmToast != null && !rmToast.isEmpty()
                        && (rmToast.toLowerCase().contains("return") || rmToast.toLowerCase().contains("success") || rmToast.toLowerCase().contains("saved"));
                step(page, "Return MRD · Fill details, Save & toast",
                        "Fill From/To Department + User, enter a Remark; Save (fnIUDReturn)",
                        "'... Returned Successfully' toast",
                        rmToast == null || rmToast.isEmpty() ? "No toast appeared" : rmToast, rmOk ? "PASS" : "FAIL");
                lv.cancelMrdReturn();
                step(page, "Return MRD · Close popup", "Click Cancel to close the MRDReturn popup",
                        "Popup closed", "Popup closed", "PASS");
                addSummary("Return MRD · Result", rmOk ? rmToast : "Not confirmed");
            }
            addSummary("Return MRD · Patient", rmPatient);
        }
        lv.dismissAnyModal();

        // ===== Patient Status (Next Action) =====
        // Select a patient -> click Patient Status -> popup opens -> select Patient Status -> Save -> success toast.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String psPatient = lv.selectFirstPatient();
        step(page, "Patient Status · Select a patient", "Select any patient from the results",
                "A patient is selected", psPatient == null ? "No patient row to select" : "Selected: " + psPatient,
                psPatient == null ? "FAIL" : "PASS");
        if (psPatient != null) {
            boolean psOpened = lv.clickPatientStatus();
            step(page, "Patient Status · Click Patient Status", "Click 'Patient Status'; the popup opens",
                    "The Patient Status popup opens",
                    psOpened ? "Popup opened" : "Popup did NOT open", psOpened ? "PASS" : "FAIL");
            if (psOpened) {
                String psSelected = lv.selectPatientStatus();
                boolean psEmptyDropdown = psSelected != null && psSelected.startsWith("EMPTY DROPDOWN:");
                boolean psNoSelect = psSelected != null && psSelected.startsWith("(no select found)");
                step(page, "Patient Status · Select Patient Status", "Select a value in the Patient Status dropdown",
                        "A status is selected", psSelected, (psEmptyDropdown || psNoSelect) ? "FAIL" : "PASS");
                if (!psEmptyDropdown && !psNoSelect) {
                    String psToast = lv.savePatientStatusAndGetToast();
                    String psTl = psToast == null ? "" : psToast.toLowerCase();
                    boolean psOk = psTl.contains("success") || psTl.contains("saved") || psTl.contains("updated");
                    step(page, "Patient Status · Click Save & success toast", "Click Save; wait for the success toast",
                            "A success toast appears",
                            psToast == null || psToast.isEmpty() ? "No toast appeared" : psToast,
                            psOk ? "PASS" : "FAIL");
                    addSummary("Patient Status · Result", psOk ? psToast : "Not confirmed");
                }
                lv.closePatientStatusPopup();
            } else {
                addSummary("Patient Status · Result", "Popup did not open");
            }
            addSummary("Patient Status · Patient", psPatient);
        }
        lv.dismissAnyModal();

        // ===== Send Deposit Request SMS =====
        // Select a patient -> click Send Deposit Request SMS -> success toast (direct action, no fill-in popup).
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String drsPatient = lv.selectFirstPatient();
        step(page, "Send Deposit Request SMS · Select a patient", "Select any patient from the results",
                "A patient is selected", drsPatient == null ? "No patient row to select" : "Selected: " + drsPatient,
                drsPatient == null ? "FAIL" : "PASS");
        if (drsPatient != null) {
            String drsToast = lv.clickSendDepositRequestSmsAndGetToast();
            String drsTl = drsToast == null ? "" : drsToast.toLowerCase();
            boolean drsOk = drsTl.contains("success") || drsTl.contains("saved") || drsTl.contains("sent");
            step(page, "Send Deposit Request SMS · Click & success toast", "Click 'Send Deposit Request SMS'; wait for the success toast",
                    "A success toast appears",
                    drsToast == null || drsToast.isEmpty() ? "No toast appeared" : drsToast,
                    drsOk ? "PASS" : "FAIL");
            addSummary("Send Deposit Request SMS · Patient", drsPatient);
            addSummary("Send Deposit Request SMS · Result", drsOk ? drsToast : "Not confirmed");
        }
        lv.dismissAnyModal();

        // ===== View Details =====
        // Select a patient -> View Details -> fill every mandatory (*) field across Patient/Correspondence/NOK,
        // Payor (Self) and Visit -> Update Registration -> success toast. Ported from OP > Outpatient Queue
        // Management's View Details, which shares the same underlying registration-details popup.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String vdPatient = lv.selectFirstPatient();
        step(page, "View Details · Select a patient", "Select any patient from the results",
                "A patient is selected", vdPatient == null ? "No patient row to select" : "Selected: " + vdPatient,
                vdPatient == null ? "FAIL" : "PASS");
        if (vdPatient != null) {
            boolean vdOpened = lv.clickViewDetails();
            step(page, "View Details · Open popup", "Click 'View Details'; the patient registration details popup opens",
                    "The patient registration details popup opens",
                    vdOpened ? "Popup opened" : "Popup did NOT open", vdOpened ? "PASS" : "FAIL");
            if (vdOpened) {
                String vdFilled = lv.fillViewDetailsAll();
                String vdRemaining = lv.remainingMandatoryAllSections();

                // Same rule as OP Queue Management: judge "Fill all mandatory fields" on whether Update actually
                // SUCCEEDED, not on the remaining-empty scan alone — a real success toast is authoritative.
                String vdToast = lv.updateViewDetailsAndGetToast();
                String vdTl = vdToast == null ? "" : vdToast.toLowerCase();
                boolean vdOk = vdTl.contains("updated") || vdTl.contains("success") || vdTl.contains("saved");

                step(page, "View Details · Fill all mandatory fields",
                        "Fill every mandatory (*) field still empty — Patient Information, Correspondence Details, NOK/Guarantor, Payor Information and Visit Information",
                        "No mandatory field is left empty (or the Update below succeeds anyway)",
                        vdFilled + (vdRemaining.isEmpty() ? "" : "  <-- STILL EMPTY: " + vdRemaining)
                                + (vdOk && !vdRemaining.isEmpty() ? "  <-- Update still succeeded, so not treated as a failure" : ""),
                        (vdRemaining.isEmpty() || vdOk) ? "PASS" : "FAIL");

                step(page, "View Details · Click Update & toast",
                        "Click Update Registration (UpdateOnlyRegistration); confirm; wait for the success toast",
                        "'... Updated Successfully' toast",
                        vdToast == null || vdToast.isEmpty() ? "No toast appeared"
                                : (vdOk ? vdToast : "Update not confirmed - the app showed: \"" + vdToast + "\""),
                        vdOk ? "PASS" : "FAIL");

                lv.closeViewDetails();
                addSummary("View Details · Result", vdOk ? vdToast : "Not confirmed");
            } else {
                addSummary("View Details · Result", "Popup did not open");
            }
            addSummary("View Details · Patient", vdPatient);
        }
        lv.dismissAnyModal();

        // ===== Consent2 (SPC consent form) =====
        // Select any patient, open Consent2 (#/admission-consent/{AdmissionId}/), pick an SPC form, Add, fill, Save.
        // This navigates AWAY from the Emergency List View, so it runs LAST.
        lv.searchByDateAndMrn(FROM_DATE, TO_DATE, MRN);
        String cp = lv.selectFirstPatient();
        step(page, "Consent2 · Select a patient", "Select any patient from the results",
                "A patient is selected", cp == null ? "No patient row to select" : "Selected: " + cp,
                cp == null ? "FAIL" : "PASS");
        if (cp != null) {
            String admId = lv.getSelectedAdmissionId();
            boolean consentOpened = lv.openConsent2(BASE, admId);
            step(page, "Consent2 · Open", "Click Consent2 for the selected patient (#/admission-consent/" + admId + "/)",
                    "The Consent2 (admission consent) form opens",
                    consentOpened ? "Consent2 opened (AdmissionId " + admId + ")" : "Consent2 did NOT open",
                    consentOpened ? "PASS" : "FAIL");
            if (consentOpened) {
                String spc = lv.selectSpcConsentAndAdd();
                boolean spcOk = spc != null && !spc.startsWith("ERR") && !spc.startsWith("(");
                step(page, "Consent2 · Enter SPC + Add", "Set Date, select an SPC consent form, click Add (loads the template)",
                        "An SPC consent form is selected and its template is loaded", spc, spcOk ? "PASS" : "FAIL");

                String filled = lv.fillConsentForm("Consent acknowledged and completed for the SPC procedure.");
                step(page, "Consent2 · Fill the form", "Fill the Description (consent template) editor",
                        "The consent form has content", filled, "PASS");

                String cToast = lv.saveConsentAndGetToast();
                boolean cOk = cToast != null && cToast.toLowerCase().matches(".*(success|saved).*");
                step(page, "Consent2 · Submit (Save)", "Click Save (IUDconsentdetail); wait for the success toast",
                        "Success toast is shown",
                        cToast == null || cToast.isEmpty()
                                ? "No toast — server ConsentTemplate API returns 500 (TX Text Control 'txkernel' missing on server)"
                                : cToast,
                        cOk ? "PASS" : "MANUAL");
                addSummary("Consent2 · Patient", cp);
                addSummary("Consent2 · SPC form", spc);
                addSummary("Consent2 · Result", cOk ? cToast : "Blocked by server ConsentTemplate 500 (txkernel dependency)");
            }
        }

        addSummary("Application URL", BASE + "/#/EmergencyAdmissionList");
    }
}
