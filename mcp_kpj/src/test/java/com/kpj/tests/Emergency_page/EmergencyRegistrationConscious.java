package com.kpj.tests.Emergency_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Op_page.RegistrationPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.microsoft.playwright.Page;

/**
 * TC16 - Emergency &gt; <b>Emergency Registration (Conscious)</b> (route {@code #/EmergencyRegistrationConsious}).
 *
 * <p>A conscious emergency patient gives their full details, so this screen is the SAME form as OP
 * Registration (identical {@code Registration.*}/{@code Visit.*} ng-models, same {@code IUDRegistration()}
 * save) — it just lives under the Emergency menu. This test therefore reuses {@link RegistrationPage},
 * navigating to the emergency route instead of {@code #/VisitScreen}.</p>
 *
 * <ol>
 *   <li>Click Emergency → Emergency Registration (Conscious).</li>
 *   <li>Fill EVERY section (Patient / Correspondence / Other / NOK / Payor / Visit) + attachments.</li>
 *   <li>Save → confirm → PDPA Consent modal.</li>
 *   <li>Capture all generated reports (Registration Report + submitted Patient Registration Form).</li>
 * </ol>
 */
public class EmergencyRegistrationConscious extends DevHisBase {

    private static final String ROUTE = "#/EmergencyRegistrationConsious"; // app's spelling ("Consious")

    public EmergencyRegistrationConscious() { super("TC16_EmergencyRegistrationConscious"); }

    public static void main(String[] args) {
        EmergencyRegistrationConscious t = new EmergencyRegistrationConscious();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Emergency Registration (Conscious)", "Emergency > Emergency Registration (Conscious)",
                "&#9888; Registers a conscious emergency patient (same form as OP Registration) with ALL sections filled; "
                        + "on Save the app auto-opens the Registration Report + Consent form.");

        // The conscious emergency registration is the SAME form as OP Registration, so its page object EXTENDS
        // RegistrationPage — referenced by its fully-qualified name (this test class shares the simple name).
        com.kpj.pages.Emegency_Page.EmergencyRegistrationConscious reg =
                new com.kpj.pages.Emegency_Page.EmergencyRegistrationConscious(page);
        // Same switch as OP Registration: -Ddevhis.nationality=<adjectival name> registers a FOREIGN patient
        // (Passport + Visa Details); omitted, it stays Malaysian on a New IC.
        String wantNationality = System.getProperty("devhis.nationality", "").trim();
        PatientProfile p = wantNationality.isEmpty() ? PatientProfile.random()
                                                     : PatientProfile.randomForeign(wantNationality);
        System.out.println("Emergency (Conscious) registering: " + p.patientLine());

        // 1) Login
        login();
        step("Login", "farisha / Tcare@123", "Patient Dashboard", "Logged in as farisha", "PASS");

        // 2) Open Emergency > Emergency Registration (Conscious) — reuses the OP Registration form.
        boolean masterLoaded = reg.open(BASE);
        step("Navigate Emergency > Registration (Conscious)", "Open " + ROUTE,
                "Registration form + dropdown master-data loaded",
                masterLoaded ? "Emergency (Conscious) registration loaded (dropdowns populated)"
                             : "Form loaded but dropdown master-data NOT populated",
                masterLoaded ? "PASS" : "FAIL");
        if (!masterLoaded) return;

        // 3) Patient Information
        reg.fillPatientInformation(p);
        // Screenshots are viewport-sized — bring the section into frame so it shows filled.
        scrollToSection("patient information");
        step("Fill Patient Information",
                "Title, New IC NRIC, Name+Family Name, Nationality, Gender, DOB, Race, Religion, Marital, Blood, Income, TIN",
                "All patient fields accepted", "Patient Information filled: " + p.patientLine(), "PASS");

        // 3b) VALIDATION DEFECT CHECK — same rule as OP Registration: with Nationality = Malaysian the Passport No.
        // field must NOT be enabled or accept input, because a Malaysian registers on the NRIC. This screen shares
        // RegistrationPage, so it shares the defect — and without this step it went unreported here.
        {
            String passportChk = reg.checkPassportRequiredForLocal();
            boolean natLocal = passportChk.toLowerCase().contains("malays");
            boolean passportFillable = reg.passportFillableForMalaysian(passportChk);
            boolean passportEnabled = passportChk.contains("\"enabled\":true");
            // A HIDDEN field is not a defect however it is flagged: IP Admission renders Passport No. off-screen for a
            // Malaysian yet still reports enabled=true, and failing on that accused the app of something no user can
            // even reach. The defect is only real when the control is VISIBLE and still enabled or fillable.
            boolean passportVisible = passportChk.contains("\"passportVisible\":true");
            boolean passportDefect = natLocal && passportVisible && (passportFillable || passportEnabled);
            String why = passportFillable
                    ? "for Malaysian nationality passport value is true, expected false (the field accepts input)"
                    : "Passport No. is enabled while Nationality = Malaysian";
            if (natLocal) {
                step("Passport No. not enabled for Malaysian",
                        "With Nationality = Malaysian, verify the Passport No. field is not enabled and cannot be filled",
                        "Passport No. is disabled and not fillable for a Malaysian (NRIC is used, not a passport) — expected false",
                        passportDefect ? "DEFECT: " + why + " — " + passportChk
                                       : "Passport No. not enabled/fillable for Malaysian — " + passportChk,
                        passportDefect ? "FAIL" : "PASS");
            } else {
                // A foreigner is identified BY the passport, so the expectation inverts: the field must be there.
                boolean usable = passportEnabled || passportFillable;
                step("Passport No. enabled for a foreign patient",
                        "With a non-Malaysian Nationality, verify the Passport No. field IS available",
                        "Passport No. is enabled for a foreigner (the passport is the identification)",
                        usable ? "Passport No. is available — " + passportChk
                               : "DEFECT: Passport No. is NOT usable for a foreign patient — " + passportChk,
                        usable ? "PASS" : "FAIL");
            }
        }

        // Visa Details — mandatory for a passport holder, so a foreign registration cannot save without it.
        if (!wantNationality.isEmpty()) {
            String visa = reg.fillVisaDetails(ATTACH);
            boolean visaOk = visa.matches("(?s).*VisaDetails=[1-9].*");
            step(page, "Fill Visa Details", "Open the Visa modal, fill Visa Type / Entry Type / Duration / "
                            + "validity dates / Remarks, upload the passport+visa copies, click them, then Add",
                    "The visa row is added (mandatory for a passport holder)", visa, visaOk ? "PASS" : "FAIL");
        }

        // 4) Attachments — Photo / Thumb / IC Card
        String attached = reg.attachDocuments(ATTACH);
        // No screenshot for this one — the attachment step shows nothing worth capturing.
        step((byte[]) null, "Attach Photo / Thumb / IC Card", "Attach an image for Photo, Thumbprint and IC Card (" + ATTACH.getFileName() + ")",
                "All three files attached", attached.isEmpty() ? "No file attached" : "Attached: " + attached,
                attached.isEmpty() ? "FAIL" : "PASS");

        // 5) Correspondence Details
        reg.fillCorrespondence(p);
        step("Fill Correspondence Details", "House No, Street, Address, Postcode (auto City/State/Country), Mobile, Phone, E-mail",
                "Correspondence fields accepted", "Correspondence filled: " + p.correspondenceLine(), "PASS");

        // 6) Other Details
        reg.fillOtherDetails();
        step("Fill Other Details", "Language Preferred (English), Occupation (Education), Employer/Occupation",
                "Other Details accepted", "Other Details filled", "PASS");

        // 7) Next of Kin (before Payor, per the required order) — add the kin.
        boolean kinAdded = reg.addNextOfKin(p);
        // Screenshots are viewport-sized — bring the section into frame so it shows filled.
        scrollToSection("next of kin|nok|guarantor");
        step("Add Next of Kin", p.kinTitle + " " + p.kinName + " (" + p.kinRel + ") + Same As Patient Address -> Add",
                "Kin row added to the grid",
                kinAdded ? "Kin added: " + p.kinName + " (" + p.kinRel + ")" : "Kin not added yet — will be re-ensured before Save",
                kinAdded ? "PASS" : "MANUAL");

        // 8) Payor = Self
        boolean payorSelf = reg.selectPayorSelf();
        // Screenshots are viewport-sized — bring the section into frame so it shows filled.
        scrollToSection("payor");
        step("Fill Payor Information", "Select Payor = Self (Insurer 'Self', value=1)",
                "Patient marked as self-pay",
                payorSelf ? "Payor = Self (Insurer=Self selected)" : "Could not select Self payor",
                payorSelf ? "PASS" : "FAIL");

        // 9) Visit Information
        reg.fillVisitInformation();
        // Screenshots are viewport-sized — bring the section into frame so it shows filled.
        openSection("visit information");
        // Judge the section on what it HOLDS. Reporting "filled / PASS" regardless meant a run with Department,
        // Primary Doctor and Visit Type all empty passed here and only broke at Save, pointing nowhere.
        boolean visitOk = reg.visitInformationComplete();
        step("Fill Visit Information", "Patient Source External, Encounter Outpatient, Department + Doctor, Queue 1, Cash",
                "Visit set — Department, Primary Doctor and Visit Type hold a value",
                (visitOk ? "Visit Information filled — " : "Visit Information INCOMPLETE — ") + reg.lastVisitState,
                visitOk ? "PASS" : "FAIL");

        // 10) Save -> confirm dialog
        int tabsBeforeSave = page.context().pages().size();
        String blockMsg = reg.clickSaveAwaitConfirm(p);
        // Name the field the screen flagged, and the empty-dropdown cause behind it — the bare
        // "Please fill in all the mandatory fields!" says nothing on its own.
        String blockDetail = "";
        if (blockMsg != null) {
            if (!reg.lastMandatoryFields.isEmpty())
                blockDetail += " — the screen flagged: " + reg.lastMandatoryFields;
            if (!reg.lastSubDeptDiagnosis.isEmpty())
                blockDetail += " — CAUSE: the Sub Dept dropdown is EMPTY (" + reg.lastSubDeptDiagnosis + ")";
        }
        step("Click Save", "Click Save", "'Do You Want To Save' dialog",
                blockMsg == null ? "Confirm dialog shown" : "Save blocked: " + blockMsg + blockDetail,
                blockMsg == null ? "PASS" : "FAIL");
        if (blockMsg != null) return;

        // 11) Confirm Save
        SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        // A rejection toast must FAIL here. Passing this step unconditionally (on the theory that the Consent
        // modal / MRN below proves the save) reported "PASS" for a run the app refused with "Cross Consultation
        // Visit Type Not Allowed For this Patient!" — and those later steps never ran to contradict it.
        String saveToast = save.toast == null ? "" : save.toast.trim();
        String stl = saveToast.toLowerCase();
        boolean rejected = stl.contains("not allowed") || stl.contains("please") || stl.contains("already")
                || stl.contains("invalid") || stl.contains("error") || stl.contains("required");
        step("Confirm Save", "Confirm the 'Do You Want To Save' dialog",
                "Save confirmed (success verified by the Consent modal / MRN report below)",
                saveToast.isEmpty() ? "Save confirmed"
                        : (rejected ? "Save REJECTED — the screen answered: \"" + saveToast + "\"" : saveToast),
                rejected ? "FAIL" : "PASS");
        if (rejected) {
            addSummary("Result", "FAILED — " + saveToast);
            return;
        }

        // 12) In-app "Consent Details" (PERSONAL DATA NOTICE & CONSENT) modal
        if (reg.waitConsentDetailsModal()) {
            step(page, "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears",
                    "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                    "Consent Details modal shown (PERSONAL DATA NOTICE & CONSENT)", "PASS");
            reg.closeConsentDetailsModal();
        } else {
            step(page, "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears",
                    "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                    "Consent Details modal did NOT open", "FAIL");
        }

        // 13) Registration Report (PDF label) — auto-opened tab. A tab existing (matched by URL substring) is
        // NOT proof it rendered — check for a browser-level load failure (DNS/connection error) too, and FAIL
        // rather than silently skip the step when no tab ever opened.
        Page regReport = reg.findTab("RegistrationReport");
        if (regReport != null) {
            String regBroken = reg.describeIfBrokenPage(regReport);
            byte[] regPng = reg.captureReportTab(regReport);
            step(regPng, "Registration Report tab", "Auto-opened patient label", "Report shows the new MRN",
                    regBroken.isEmpty() ? "Registration Report opened: " + regReport.url()
                            : "Registration Report tab FAILED to load — " + regBroken,
                    regBroken.isEmpty() ? "PASS" : "FAIL");
        } else {
            step(page, "Registration Report tab", "Auto-opened patient label", "Report shows the new MRN",
                    "Registration Report tab did NOT open after Save", "FAIL");
        }

        // 14) Patient Registration Form consent (auto-opened) -> submit -> full-page screenshot. Same "tab found
        // is not tab loaded" gap — the consent form's host can come back as a DNS/connection error, and
        // submitPatientForm() now names that explicitly via lastPatientFormLoadError instead of a generic message.
        Page patientForm = reg.findTab("TH_EF_293");
        boolean consentSubmitted = false;
        if (patientForm != null) {
            consentSubmitted = reg.submitPatientForm(patientForm);
            byte[] formPng = reg.captureFullForm(patientForm);
            String failReason = !reg.lastPatientFormLoadError.isEmpty()
                    ? "Consent form tab FAILED to load — " + reg.lastPatientFormLoadError
                    : "Consent submit not confirmed";
            step(formPng, "Patient Registration Form (submitted, full form)",
                    "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                    consentSubmitted ? "Form Submitted Successfully (" + patientForm.url() + ")" : failReason,
                    consentSubmitted ? "PASS" : "FAIL");
        } else {
            step(page, "Patient Registration Form (submitted, full form)",
                    "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                    "Patient Registration Form (consent) tab did NOT open after Save", "FAIL");
        }

        // --- Summary ---
        addSummary("Patient", p.patientLine());
        addSummary("Attachments", attached.isEmpty() ? "none" : attached);
        addSummary("Correspondence", p.correspondenceLine());
        addSummary("Payor", "Self (self-pay)");
        addSummary("Next of Kin", p.kinLine());
        addSummary("Result", save.toast);
        addSummary("Application URL", BASE + "/" + ROUTE);
    }
}
