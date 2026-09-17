package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Op_page.RegistrationPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.microsoft.playwright.Page;

/**
 * OP &gt; Registration (VisitScreen) for a FOREIGN-nationality patient.
 *
 * <p>Same flow as {@link RegistrationTest}, but the patient is built with
 * {@link PatientProfile#randomForeign(String)} (Nationality = Indonesian, Identification Type = Passport)
 * instead of {@link PatientProfile#random()}. The one Malaysian-specific validation check
 * ("Passport No. not enabled") is INVERTED here — for a non-Malaysian the Passport No. box
 * ({@code Registration.FamilyName}) is expected to be visible and fillable, not blocked.</p>
 *
 * <p>Next of Kin is left Malaysian (unaffected by the patient's nationality) — only the patient's own
 * nationality was in scope for this flow.</p>
 */
public class RegistrationForeignTest extends DevHisBase {

    private static final String NATIONALITY = System.getProperty("devhis.nationality", "Indonesian");

    public RegistrationForeignTest() { super("OP_Registration_ForeignNationality"); }

    public static void main(String[] args) {
        RegistrationForeignTest t = new RegistrationForeignTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OP - Registration - Foreign Nationality",
                "OP > Registration (VisitScreen) - Patient / Correspondence / Other / Payor / Visit / Kin",
                "Registers a foreign-nationality (" + NATIONALITY + ") patient — Identification Type = Passport, "
                        + "Passport No. filled instead of NRIC-only.");

        RegistrationPage reg = new RegistrationPage(page);

        login();
        step("Login", USER + " login", "Patient Dashboard", "Logged in as " + USER, "PASS");

        boolean ok = registerOne(reg);
        addSummary("Result", ok ? "Registration created" : "Registration NOT completed");
    }

    private boolean registerOne(RegistrationPage reg) {
        PatientProfile p = PatientProfile.randomForeign(NATIONALITY);
        System.out.println("Registering (foreign): " + p.patientLine());

        try {
            for (Page pg : new java.util.ArrayList<>(page.context().pages())) {
                if (pg != page && !pg.isClosed()) pg.close();
            }
        } catch (Exception ignore) { }

        boolean masterLoaded = reg.open(BASE);
        step("Navigate OP > Registration", "Open Registration (VisitScreen)",
                "Registration form + dropdown master-data loaded",
                masterLoaded ? "Registration page loaded (dropdowns populated)"
                             : "Form loaded but dropdown master-data NOT populated",
                masterLoaded ? "PASS" : "FAIL");
        if (!masterLoaded) { addSummary("Result", "FAILED — master-data not loaded"); return false; }

        reg.fillPatientInformation(p);
        step("Fill Patient Information",
                "Title, Passport No., Name, Nationality=" + NATIONALITY + ", Gender, DOB, Race, Religion, Marital, Blood, Income, TIN",
                "All patient fields accepted", "Patient Information filled: " + p.patientLine(), "PASS");

        // INVERTED Malaysian check: for a foreign nationality, Passport No. (Registration.FamilyName) MUST be
        // visible and fillable — the opposite of RegistrationTest's "not enabled for Malaysian" assertion.
        {
            String passportChk = reg.checkPassportRequiredForLocal();
            boolean natForeign = !passportChk.toLowerCase().contains("\"nat\":\"malays");
            boolean visible = passportChk.contains("\"passportVisible\":true");
            boolean fillable = passportChk.contains("\"fillable\":true");
            boolean ok = natForeign && visible && fillable;
            step(page, "Passport No. enabled for foreign nationality",
                    "With Nationality = " + NATIONALITY + ", verify the Passport No. field is visible and fillable",
                    "Passport No. is visible and fillable for a non-Malaysian (a passport identifies them, not an NRIC)",
                    ok ? "Passport No. visible and fillable — " + passportChk
                       : "DEFECT: Passport No. not usable for a foreign nationality — " + passportChk,
                    ok ? "PASS" : "FAIL");
        }

        // Visa Details — MANDATORY for a passport holder (Save is blocked without it: "Visa Details are
        // mandatory for Passport holders"). Same modal Ip.Admission drives for its own foreign-patient case.
        String visa = reg.fillVisaDetails(ATTACH);
        boolean visaOk = visa != null && visa.matches("(?s).*VisaDetails=[1-9].*");
        step(page, "Fill Visa Details", "Open the Visa modal, fill Visa Type / Entry Type / Duration / validity "
                        + "dates / Remarks, upload the passport+visa copies, click them, then Add",
                "The visa row is added (mandatory for a passport holder)", visa, visaOk ? "PASS" : "FAIL");

        reg.fillCorrespondence(p);
        step("Fill Correspondence Details", "House No, Street, Address, Postcode (auto City/State/Country), Mobile, Phone, E-mail",
                "Correspondence fields accepted", "Correspondence filled: " + p.correspondenceLine(), "PASS");

        reg.fillOtherDetails();
        step("Fill Other Details", "Language Preferred (English), Occupation (Education), Employer/Occupation",
                "Other Details accepted", "Other Details filled", "PASS");

        // Next of Kin — left Malaysian; only the patient's own nationality was in scope here.
        boolean kinAdded = reg.addNextOfKin(p);
        step("Add Next of Kin", p.kinTitle + " " + p.kinName + " (" + p.kinRel + ") + Same As Patient Address -> Add",
                "Kin row added to the NOK/Guarantor grid",
                kinAdded ? "Kin added: " + p.kinName + " (" + p.kinRel + ") — NOK grid rows: " + reg.kinGridRowCount()
                         : "Kin NOT in the NOK grid (rows: " + reg.kinGridRowCount() + ") — Save will be blocked",
                kinAdded ? "PASS" : "FAIL");

        boolean payorSelf = reg.selectPayorSelf();
        step("Fill Payor Information", "Select Payor = Self (Insurer 'Self', value=1)",
                "Patient marked as self-pay",
                payorSelf ? "Payor = Self (Insurer=Self selected)" : "Could not select Self payor",
                payorSelf ? "PASS" : "FAIL");

        reg.fillVisitInformation();
        step("Fill Visit Information", "Patient Source External, Encounter Outpatient, Department + Doctor, Queue 1, Cash",
                "Visit set", "Visit Information filled", "PASS");

        String blockMsg = reg.clickSaveAwaitConfirm(p);
        step("Click Save", "Click Save", "'Do You Want To Save' dialog",
                blockMsg == null ? "Confirm dialog shown" : "Save blocked: " + blockMsg,
                blockMsg == null ? "PASS" : "FAIL");
        if (blockMsg != null) { addSummary("Result", "Save blocked: " + blockMsg + " | " + p.patientLine()); return false; }

        SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        step("Confirm Save", "Confirm the 'Do You Want To Save' dialog",
                "Save confirmed (success is verified by the Consent modal / MRN report below)",
                save.toast == null || save.toast.isEmpty() ? "Save confirmed" : save.toast, "PASS");

        // Consent Details modal, Registration Report tab, Patient Registration Form — all a hard FAIL when they
        // don't appear, not a silently-skipped step (these are how Save success is actually verified).
        boolean consentModalShown = reg.waitConsentDetailsModal();
        step(page, "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears",
                "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                consentModalShown ? "Consent Details modal shown (PERSONAL DATA NOTICE & CONSENT)"
                        : "Consent Details modal did NOT appear after Save",
                consentModalShown ? "PASS" : "FAIL");
        if (consentModalShown) reg.closeConsentDetailsModal();

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

        System.out.println("Open tabs after save: " + page.context().pages().stream()
                .map(Page::url).collect(java.util.stream.Collectors.joining("  |  ")));
        Page patientForm = reg.findTab("TH_EF_293");
        if (patientForm == null) patientForm = reg.findConsentFormTab();
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

        addSummary("Registered", p.patientLine() + " | Kin " + p.kinLine()
                + " | " + (save.toast == null || save.toast.isEmpty() ? "saved" : save.toast));
        return true;
    }
}
