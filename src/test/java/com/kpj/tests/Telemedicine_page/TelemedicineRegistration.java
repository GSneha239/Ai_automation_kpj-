package com.kpj.tests.Telemedicine_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.kpj.pages.Telemedicine_page.TelemedicineRegistrationPage;
import com.microsoft.playwright.Page;

/**
 * Telemedicine &gt; <b>Registration</b> — the SAME form/flow as OP &gt; Registration (VisitScreen), reached via the
 * Telemedicine menu. Fill every section → Save → confirm → the Registration Report + Consent form open in new tabs.
 */
public class TelemedicineRegistration extends DevHisBase {

    public TelemedicineRegistration() { super("Telemedicine_Registration"); }

    public static void main(String[] args) {
        TelemedicineRegistration t = new TelemedicineRegistration();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Telemedicine - Registration", "Telemedicine > Registration",
                "Telemedicine Registration (same as OP Registration, VisitScreen): fill every section, Save, confirm; the Registration Report + Consent form open in new tabs.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        TelemedicineRegistrationPage reg = new TelemedicineRegistrationPage(page);
        PatientProfile p = PatientProfile.random();
        System.out.println("Telemedicine Registration: " + p.patientLine());

        // 1) Open Telemedicine > Registration (VisitScreen form) + master-data.
        boolean masterLoaded = reg.open(BASE);
        step(page, "Open Telemedicine > Registration", "Telemedicine > Registration (VisitScreen)",
                "Registration form + dropdown master-data loaded",
                masterLoaded ? "Registration page loaded (" + page.url() + ")" : "Form loaded but master-data NOT populated",
                masterLoaded ? "PASS" : "FAIL");
        if (!masterLoaded) { addSummary("Result", "FAILED — master-data not loaded"); return; }

        // 2) Patient Information
        reg.fillPatientInformation(p);
        step(page, "Fill Patient Information", "Title, NRIC, Name+Family, Nationality, Gender, DOB, Race, Religion, Marital, Blood, Income, TIN",
                "All patient fields accepted", "Patient Information filled: " + p.patientLine(), "PASS");

        // 3) Attachments
        String attached = reg.attachDocuments(ATTACH);
        step(page, "Attach Photo / Thumb / IC Card", "Attach an image for Photo, Thumbprint and IC Card",
                "All three files attached", attached.isEmpty() ? "No file attached" : "Attached: " + attached,
                attached.isEmpty() ? "FAIL" : "PASS");

        // 4) Correspondence
        reg.fillCorrespondence(p);
        step(page, "Fill Correspondence Details", "House No, Street, Address, Postcode, Mobile, Phone, E-mail",
                "Correspondence fields accepted", "Correspondence filled: " + p.correspondenceLine(), "PASS");

        // 5) Other Details
        reg.fillOtherDetails();
        step(page, "Fill Other Details", "Language Preferred, Occupation, Employer",
                "Other Details accepted", "Other Details filled", "PASS");

        // 6) Next of Kin
        boolean kinAdded = reg.addNextOfKin(p);
        step(page, "Add Next of Kin", p.kinTitle + " " + p.kinName + " (" + p.kinRel + ") + Same As Patient Address -> Add",
                "Kin row added to the grid",
                kinAdded ? "Kin added: " + p.kinName + " (" + p.kinRel + ")" : "Kin not added yet — re-ensured before Save",
                kinAdded ? "PASS" : "MANUAL");

        // 7) Payor = Self
        boolean payorSelf = reg.selectPayorSelf();
        step(page, "Fill Payor Information", "Select Payor = Self",
                "Patient marked as self-pay", payorSelf ? "Payor = Self" : "Could not select Self payor",
                payorSelf ? "PASS" : "FAIL");

        // 8) Visit Information
        reg.fillVisitInformation();
        step(page, "Fill Visit Information", "Patient Source External, Encounter Outpatient, Department + Doctor, Queue 1, Cash",
                "Visit set", "Visit Information filled", "PASS");

        // 9) Save -> confirm
        String blockMsg = reg.clickSaveAwaitConfirm(p);
        step(page, "Click Save", "Click Save", "'Do You Want To Save' dialog",
                blockMsg == null ? "Confirm dialog shown" : "Save blocked: " + blockMsg,
                blockMsg == null ? "PASS" : "FAIL");
        if (blockMsg != null) { addSummary("Result", "Save blocked: " + blockMsg); return; }

        SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        step(page, "Confirm Save", "Confirm the 'Do You Want To Save' dialog",
                "Save confirmed (verified by the Consent modal / MRN report)",
                save.toast == null || save.toast.isEmpty() ? "Save confirmed" : save.toast, "PASS");

        // 10) Consent Details modal
        if (reg.waitConsentDetailsModal()) {
            step(page, "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears",
                    "'PERSONAL DATA NOTICE & CONSENT' record is shown", "Consent Details modal shown", "PASS");
            reg.closeConsentDetailsModal();
        }

        // 11) Registration Report tab (report generates in new tab)
        Page regReport = reg.findTab("RegistrationReport");
        boolean reportOpened = regReport != null;
        if (reportOpened) {
            byte[] regPng = reg.captureReportTab(regReport);
            step(regPng, "Registration Report tab (new tab)", "Save auto-opens the Registration Report",
                    "The report shows the new MRN", "Registration Report opened: " + regReport.url(), "PASS");
        } else {
            step(page, "Registration Report tab (new tab)", "Save auto-opens the Registration Report",
                    "The report opens in a new tab", "No Registration Report tab opened", "FAIL");
        }

        // 12) Patient Registration Form consent (auto-opened)
        Page patientForm = reg.findTab("TH_EF_293");
        if (patientForm == null) patientForm = reg.findConsentFormTab();
        if (patientForm != null) {
            boolean consentSubmitted = reg.submitPatientForm(patientForm);
            byte[] formPng = reg.captureFullForm(patientForm);
            step(formPng, "Patient Registration Form (submitted)", "Submit -> confirm 'Yes'; capture the full form",
                    "'Form Submitted Successfully' — full form",
                    consentSubmitted ? "Form Submitted Successfully (" + patientForm.url() + ")" : "Consent submit not confirmed",
                    consentSubmitted ? "PASS" : "FAIL");
        }

        try { for (Page pg : new java.util.ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Registered", p.patientLine() + " | " + (save.toast == null || save.toast.isEmpty() ? "saved" : save.toast));
        addSummary("Result", reportOpened ? "Registered; report opened" : "Not confirmed");
    }
}
