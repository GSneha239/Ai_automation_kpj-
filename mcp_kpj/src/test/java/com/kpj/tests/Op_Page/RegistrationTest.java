package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Op_page.RegistrationPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.microsoft.playwright.Page;

/**
 * TC02 - OP &gt; Registration (VisitScreen).
 *
 * <p>Login; fill EVERY section for a randomised patient (Patient / Correspondence / Other / Payor /
 * Visit / Next of Kin) plus Photo/Thumb/IC-Card attachments; Save; handle the in-app "Consent Details"
 * (PERSONAL DATA NOTICE &amp; CONSENT) modal; then capture the auto-opened artifacts — the Registration
 * Report label and the Patient Registration Form consent (submitted, full-page) — all in one report.</p>
 *
 * <p>All page mechanics live in {@link RegistrationPage}; this test is just the ordered steps.</p>
 */
public class RegistrationTest extends DevHisBase {

    public RegistrationTest() { super("TC02_Registration"); }

    public static void main(String[] args) {
        RegistrationTest t = new RegistrationTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** How many registrations to create in a single run (one login, looped flow). */
    private static final int TOTAL = 1;

    @Override
    protected void body() {
        meta("Patient Registration x" + TOTAL + " (All Sections)",
                "OP > Registration (VisitScreen) - Patient / Correspondence / Other / Payor / Visit / Kin",
                "&#9888; Creates " + TOTAL + " NEW (randomised) patient registrations in ONE run (single login); "
                        + "each Save auto-opens the Registration Report + Consent form.");

        RegistrationPage reg = new RegistrationPage(page);

        // 1) Login ONCE — then loop the whole registration flow TOTAL times.
        login();
        step("Login", "farisha / Tcare@123", "Patient Dashboard", "Logged in as farisha", "PASS");

        int success = 0;
        for (int iter = 1; iter <= TOTAL; iter++) {
            if (registerOne(reg, iter)) success++;
        }
        addSummary("Registrations created", success + " / " + TOTAL);
    }

    /** One full registration (open → fill every section → Save → capture). Returns true on success. */
    private boolean registerOne(RegistrationPage reg, int iter) {
        String tag = "[" + iter + "/" + TOTAL + "] ";
        // -Ddevhis.nationality=<adjectival nationality, e.g. "Australian"> registers a FOREIGN patient
        // (Identification Type = Passport + a passport expiry) instead of the default Malaysian / New IC one.
        String wantNationality = System.getProperty("devhis.nationality", "").trim();
        PatientProfile p = wantNationality.isEmpty() ? PatientProfile.random()
                                                     : PatientProfile.randomForeign(wantNationality);
        System.out.println(tag + "Registering: " + p.patientLine());

        // Close any leftover report/consent tabs from the previous registration so tabs don't pile up.
        try {
            for (Page pg : new java.util.ArrayList<>(page.context().pages())) {
                if (pg != page && !pg.isClosed()) pg.close();
            }
        } catch (Exception ignore) { }

        // 2) Open OP > Registration (menu) and wait for master-data — a fresh form each iteration.
        boolean masterLoaded = reg.open(BASE);
        step(tag + "Navigate OP > Registration", "Open Registration (VisitScreen)",
                "Registration form + dropdown master-data loaded",
                masterLoaded ? "Registration page loaded (dropdowns populated)"
                             : "Form loaded but dropdown master-data NOT populated",
                masterLoaded ? "PASS" : "FAIL");
        if (!masterLoaded) { addSummary(tag + "Result", "FAILED — master-data not loaded"); return false; }

        // 3) Patient Information (all standard fields)
        reg.fillPatientInformation(p);
        // Show the section as it now stands — the screenshot is viewport-sized, so scroll it into frame first.
        scrollToSection("patient information");
        step(tag + "Fill Patient Information",
                "Title, New IC NRIC, Name+Family Name, Nationality, Gender, DOB, Race, Religion, Marital, Blood, Income, TIN",
                "All patient fields accepted", "Patient Information filled: " + p.patientLine(), "PASS");

        // 3b) VALIDATION DEFECT CHECK — with Nationality = Malaysian, Passport No. must NOT be enabled
        // (a Malaysian registers with an NRIC, not a passport). The screen marks it required (asterisk +
        // red border) → FAIL.
        // Runs on EVERY environment, not just DSH: a Malaysian registers on the NRIC, so the Passport No. field
        // must not accept input at all. Being unstarred is not enough — KLG leaves it unstarred yet fillable, which
        // is how this suite ended up storing a passport number against every Malaysian it registered.
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
                step(page, tag + "Passport No. not enabled for Malaysian",
                        "With Nationality = Malaysian, verify the Passport No. field is not enabled",
                        "Passport No. is not enabled for a Malaysian (NRIC is used, not a passport) — expected false",
                        passportDefect ? "DEFECT: " + why + " — " + passportChk
                                       : "Passport No. not enabled for Malaysian — " + passportChk,
                        passportDefect ? "FAIL" : "PASS");
            } else {
                // A FOREIGN patient is identified by the passport, so the expectation INVERTS: the field must be
                // usable. Reporting "not enabled — PASS" here would have praised the app for hiding the only ID a
                // foreigner has.
                boolean usable = passportEnabled || passportFillable;
                step(page, tag + "Passport No. enabled for a foreign patient",
                        "With a non-Malaysian Nationality, verify the Passport No. field IS available",
                        "Passport No. is enabled for a foreigner (the passport is the identification)",
                        usable ? "Passport No. is available — " + passportChk
                               : "DEFECT: Passport No. is NOT usable for a foreign patient — " + passportChk,
                        usable ? "PASS" : "FAIL");
            }
        }

        // 3c) Visa Details — MANDATORY for a passport holder ("Visa Details are mandatory for Passport holders.
        // Click the Visa button next to Passport No. to add them!"), so a foreign registration cannot save without
        // it. Skipped for a Malaysian, who registers on the NRIC.
        if (!wantNationality.isEmpty()) {
            String visa = reg.fillVisaDetails(ATTACH);
            // Judge on the COMMITTED row (VisaDetails=n), not on "Add was clicked": the first run clicked Add,
            // the app answered "Please select Passport copy!", VisaDetails stayed 0 — and the step said PASS.
            boolean visaOk = visa.matches("(?s).*VisaDetails=[1-9].*");
            step(page, tag + "Fill Visa Details", "Open the Visa modal, fill Visa Type / Entry Type / Duration / "
                            + "validity dates / Remarks, attach the passport+visa copy, then Add",
                    "The visa row is added (mandatory for a passport holder)", visa, visaOk ? "PASS" : "FAIL");
        }

        // 4) Attachments — Photo / Thumb / IC Card
        String attached = reg.attachDocuments(ATTACH);
        // No screenshot for this one — the attachment step shows nothing worth capturing.
        step((byte[]) null, tag + "Attach Photo / Thumb / IC Card", "Attach an image for Photo, Thumbprint and IC Card (" + ATTACH.getFileName() + ")",
                "All three files attached", attached.isEmpty() ? "No file attached" : "Attached: " + attached,
                attached.isEmpty() ? "FAIL" : "PASS");

        // 5) Correspondence Details
        reg.fillCorrespondence(p);
        step(tag + "Fill Correspondence Details", "House No, Street, Address, Postcode (auto City/State/Country), Mobile, Phone, E-mail",
                "Correspondence fields accepted", "Correspondence filled: " + p.correspondenceLine(), "PASS");

        // 6) Other Details
        reg.fillOtherDetails();
        step(tag + "Fill Other Details", "Language Preferred (English), Occupation (Education), Employer/Occupation",
                "Other Details accepted", "Other Details filled", "PASS");

        // 7) Next of Kin (mandatory) — done BEFORE Payor per the required order. Just add the kin here; the grid
        // row is clicked only at Save (and only if the NOK went empty), so we don't disturb the grid now.
        boolean kinAdded = reg.addNextOfKin(p);
        // Judge on the NOK GRID, not on "the Add was clicked": a kin that never reaches KinDetailsList blocks Save
        // with "Please click Add to include the NOK/Guarantor details!", so a MANUAL here hid a broken run.
        // Show the section as it now stands — the screenshot is viewport-sized, so scroll it into frame first.
        scrollToSection("next of kin|nok|guarantor");
        step(tag + "Add Next of Kin", p.kinTitle + " " + p.kinName + " (" + p.kinRel + ") + Same As Patient Address -> Add",
                "Kin row added to the NOK/Guarantor grid",
                kinAdded ? "Kin added: " + p.kinName + " (" + p.kinRel + ") — NOK grid rows: " + reg.kinGridRowCount()
                         : "Kin NOT in the NOK grid (rows: " + reg.kinGridRowCount() + ") — Save will be blocked"
                           + (reg.lastKinFailReason.isEmpty() ? "" : " — CAUSE: " + reg.lastKinFailReason),
                kinAdded ? "PASS" : "FAIL");

        // 7b) VALIDATION DEFECT CHECK — the NOK/Guarantor grid must show each value under its CORRECT column header.
        // DSH mis-maps the columns (RELATIONSHIP shows the passport, MOBILE NO shows the nationality, E-MAIL shows
        // the name, etc.) → FAIL.
        // 7a) NOK counterpart of the patient passport check — a Malaysian kin is identified by the NRIC, so the
        // kin Passport No. (the control bound to Registration.KinFamilyName) must hold no value.
        // Judged on the form as it stood BEFORE Add (captured by addNextOfKin) — Add clears the form, so that is
        // the only moment the entered kin can be inspected. Falls back to the committed grid row.
        String kinPassChk = reg.lastKinPassportPreAdd.isEmpty() ? reg.checkKinPassportForLocal() : reg.lastKinPassportPreAdd;
        if (wantNationality.isEmpty()) {
            boolean kinPassOk = reg.kinPassportEmptyForLocal(kinPassChk);
            step(page, tag + "NOK Passport No. empty for a Malaysian kin",
                    "Before Add, with the kin's Nationality = Malaysian, verify the NOK Passport No. carries no value (NRIC only)",
                    "No passport value on the kin — the NRIC identifies a Malaysian",
                    kinPassOk ? "NRIC only, no passport value — " + kinPassChk
                              : "DEFECT: a passport value is present on a Malaysian kin — " + kinPassChk,
                    kinPassOk ? "PASS" : "FAIL");
        } else {
            // The rule is about MALAYSIANS only — they are identified by the NRIC, so a passport on them is wrong.
            // A foreign kin may legitimately carry one, so this cannot fail here; the value is reported for the
            // record.
            boolean kinPassFilled = !kinPassChk.contains("\"passportValue\":\"\"");
            step(page, tag + "NOK Passport No. for a foreign kin",
                    "With the kin's Nationality = " + wantNationality
                            + ", a passport is allowed (the no-passport rule covers Malaysians only)",
                    "No defect — a foreign kin may carry a passport",
                    (kinPassFilled ? "Passport present on the kin (allowed) — " : "No passport value (also allowed) — ")
                            + kinPassChk,
                    "PASS");
        }

        String nokChk = reg.checkNokGridColumnMapping(p.kinRel, p.kinMobile, p.kinName);
        boolean nokDefect = nokChk.contains("\"problems\":[") && !nokChk.contains("\"problems\":[]");
        step(page, tag + "NOK grid column mapping",
                "After adding the Next of Kin, verify the NOK/Guarantor grid shows each value under its CORRECT header",
                "Every NOK grid value appears under its matching column (Relationship, Mobile No, E-mail, Address, ...)",
                nokDefect ? "DEFECT: NOK/Guarantor grid columns are mis-mapped — " + nokChk
                          : "NOK grid columns correctly mapped — " + nokChk,
                nokDefect ? "FAIL" : "PASS");

        // 8) Payor = Self
        boolean payorSelf = reg.selectPayorSelf();
        // Show the section as it now stands — the screenshot is viewport-sized, so scroll it into frame first.
        scrollToSection("payor");
        step(tag + "Fill Payor Information", "Select Payor = Self (Insurer 'Self', value=1)",
                "Patient marked as self-pay",
                payorSelf ? "Payor = Self — payor form " + reg.lastPayorState
                          : "Payor form NOT populated (Save will be blocked by \"Please fill in all the mandatory"
                            + " fields!\") — " + reg.lastPayorState,
                payorSelf ? "PASS" : "FAIL");

        // 9) Visit Information
        reg.fillVisitInformation();
        // Show the section as it now stands — the screenshot is viewport-sized, so scroll it into frame first.
        openSection("visit information");
        // Judge the section on what it HOLDS, not on having run the fill — see EmergencyRegistrationConscious.
        boolean visitOk = reg.visitInformationComplete();
        step(tag + "Fill Visit Information", "Patient Source External, Encounter Outpatient, Department + Doctor, Queue 1, Cash",
                "Visit set — Department, Primary Doctor and Visit Type hold a value",
                (visitOk ? "Visit Information filled — " : "Visit Information INCOMPLETE — ") + reg.lastVisitState,
                visitOk ? "PASS" : "FAIL");

        // 10) Save -> confirm dialog
        String blockMsg = reg.clickSaveAwaitConfirm(p);
        // "Please fill in all the mandatory fields!" names nothing on its own. Say WHICH field the screen flagged
        // and, when the offender is a dropdown with nothing in it, say that too — otherwise the report blames the
        // save for what is really missing master data, and every reader has to re-derive it from the logs.
        String blockDetail = "";
        if (blockMsg != null) {
            if (!reg.lastMandatoryFields.isEmpty())
                blockDetail += " — the screen flagged: " + reg.lastMandatoryFields;
            if (!reg.lastSubDeptDiagnosis.isEmpty())
                blockDetail += " — CAUSE: the Sub Dept dropdown is EMPTY (" + reg.lastSubDeptDiagnosis + ")";
        }
        step(tag + "Click Save", "Click Save", "'Do You Want To Save' dialog",
                blockMsg == null ? "Confirm dialog shown" : "Save blocked: " + blockMsg + blockDetail,
                blockMsg == null ? "PASS" : "FAIL");
        if (blockMsg != null) {
            addSummary(tag + "Result", "Save blocked: " + blockMsg + blockDetail + " | " + p.patientLine());
            return false;
        }

        // 11) Confirm Save (toast verification removed — the actual save success is confirmed by the
        // Consent Details modal + the new-MRN Registration Report that follow).
        SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        step(tag + "Confirm Save", "Confirm the 'Do You Want To Save' dialog",
                "Save confirmed (success is verified by the Consent modal / MRN report below)",
                save.toast == null || save.toast.isEmpty() ? "Save confirmed" : save.toast, "PASS");

        // 12) In-app "Consent Details" (PERSONAL DATA NOTICE & CONSENT) modal — a hard FAIL when it doesn't
        // appear, not a silently-skipped step; this is one of the two ways Save success is verified.
        boolean consentModalShown = reg.waitConsentDetailsModal();
        step(page, tag + "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears",
                "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                consentModalShown ? "Consent Details modal shown (PERSONAL DATA NOTICE & CONSENT)"
                        : "Consent Details modal did NOT appear after Save",
                consentModalShown ? "PASS" : "FAIL");
        if (consentModalShown) reg.closeConsentDetailsModal();

        // 13) Registration Report (PDF label) — auto-opened tab; FAIL, not skipped, if it never opens. A tab
        // existing (matched by URL substring) is NOT proof it rendered — check for a browser-level load failure
        // (DNS/connection error) too.
        Page regReport = reg.findTab("RegistrationReport");
        if (regReport != null) {
            String regBroken = reg.describeIfBrokenPage(regReport);
            byte[] regPng = reg.captureReportTab(regReport);
            step(regPng, tag + "Registration Report tab", "Auto-opened patient label", "Report shows the new MRN",
                    regBroken.isEmpty() ? "Registration Report opened: " + regReport.url()
                            : "Registration Report tab FAILED to load — " + regBroken,
                    regBroken.isEmpty() ? "PASS" : "FAIL");
        } else {
            step(page, tag + "Registration Report tab", "Auto-opened patient label", "Report shows the new MRN",
                    "Registration Report tab did NOT open after Save", "FAIL");
        }

        // 14) Patient Registration Form consent (auto-opened) -> submit -> full-page screenshot.
        // The consent form's template id differs by environment (devhis = TH_EF_293), so log all open tabs and
        // fall back to a generic consent-form-tab detector when the devhis id isn't present (e.g. on DSH).
        System.out.println(tag + "Open tabs after save: " + page.context().pages().stream()
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
            step(formPng, tag + "Patient Registration Form (submitted, full form)",
                    "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                    consentSubmitted ? "Form Submitted Successfully (" + patientForm.url() + ")" : failReason,
                    consentSubmitted ? "PASS" : "FAIL");
        } else {
            step(page, tag + "Patient Registration Form (submitted, full form)",
                    "Submit -> confirm 'Yes'; capture the full submitted form", "'Form Submitted Successfully' — full form",
                    "Patient Registration Form (consent) tab did NOT open after Save", "FAIL");
        }

        // Per-registration summary line.
        addSummary(tag + "Registered", p.patientLine() + " | Kin " + p.kinLine()
                + " | " + (save.toast == null || save.toast.isEmpty() ? "saved" : save.toast));
        return true;
    }
}
