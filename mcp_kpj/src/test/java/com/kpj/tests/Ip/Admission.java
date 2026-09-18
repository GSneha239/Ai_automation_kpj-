package com.kpj.tests.Ip;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// NOTE: the page object is also named Admission, so it is referenced by its fully-qualified name
// (com.kpj.pages.Ip.Admission) — importing it would clash with this test class name.

/**
 * TC14 - IP &gt; <b>Admission</b> (full IPD admission).
 *
 * <ol>
 *   <li>Click IP → Admission (via the menu tab, not a direct URL).</li>
 *   <li>Fill all sections (patient + admission location/department/doctor/type/source/bed-class/ward/
 *       billing-class + non-presence + admission purpose).</li>
 *   <li>Save ({@code IUDAdmission()}) → "Patient Admitted Successfully."</li>
 *   <li>The admission report(s) open in new tabs — capture them.</li>
 * </ol>
 */
public class Admission extends DevHisBase {

    public Admission() { super("TC14_IP_Admission"); }

    public static void main(String[] args) {
        Admission t = new Admission();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Admission", "IP > Admission",
                "Admit an inpatient (full IPD admission) via the IP menu, then generate the admission report.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " / " + PASS, "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Ip.Admission ip = new com.kpj.pages.Ip.Admission(page);

        boolean opened = ip.navigateViaMenu();
        step(page, "Open Admission (via IP menu tab)", "Click IP → Admission (menu, not direct URL)",
                "The Admission form is shown", opened ? "Admission opened (#/Admission)" : "Did NOT reach Admission",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        // Section 1 — Patient Information + Correspondence Details (same form, one accordion on this screen).
        String patient = ip.fillPatientSection();
        // "City=(empty)" or "State=(empty)" means the postcode -> City/District -> State cascade did not fire —
        // fail the step on that, rather than reporting PASS regardless of what the fill actually left behind.
        boolean patientOk = !patient.contains("City=(empty)") && !patient.contains("State=(empty)");
        step(page, "Fill · Patient Information", "Nationality → Prefix/Gender → Race/Religion/Marital/Blood → New IC → Name → NRIC → DOB → Mobile → TIN Number → Postcode (City/District + State auto-populate)",
                "Patient section populated, incl. TIN Number and City/District + State from the postcode", patient, patientOk ? "PASS" : "FAIL");

        // VALIDATION DEFECT CHECK — the same one OP Registration runs: with Nationality = Malaysian the
        // Passport No. field must NOT be enabled (a Malaysian registers on the NRIC, not a passport). The probe
        // is reused from RegistrationPage rather than re-implemented — this screen renders the same form.
        {
            com.kpj.pages.Op_page.RegistrationPage reg = new com.kpj.pages.Op_page.RegistrationPage(page);
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
                step(page, "Passport No. not enabled for Malaysian",
                        "With Nationality = Malaysian, verify the Passport No. field is not enabled",
                        "Passport No. is not enabled for a Malaysian (NRIC is used, not a passport) — expected false",
                        passportDefect ? "DEFECT: " + why + " — " + passportChk
                                       : "Passport No. not enabled for Malaysian — " + passportChk,
                        passportDefect ? "FAIL" : "PASS");
            } else {
                // A foreigner is identified BY the passport, so the expectation inverts — the field must be usable.
                boolean usable = passportEnabled || passportFillable;
                step(page, "Passport No. enabled for a foreign patient",
                        "With a non-Malaysian Nationality, verify the Passport No. field IS available",
                        "Passport No. is enabled for a foreigner (the passport is the identification)",
                        usable ? "Passport No. is available — " + passportChk
                               : "DEFECT: Passport No. is NOT usable for a foreign patient — " + passportChk,
                        usable ? "PASS" : "FAIL");
            }
        }

        // Visa Details — MANDATORY for a passport holder, so a foreign admission cannot save without it. The modal
        // is the same one OP Registration drives, so its page object does the work rather than a second copy.
        if (com.kpj.pages.Ip.Admission.isForeign()) {
            String visa = new com.kpj.pages.Op_page.RegistrationPage(page).fillVisaDetails(ATTACH);
            boolean visaOk = visa.matches("(?s).*VisaDetails=[1-9].*");
            step(page, "Fill · Visa Details", "Open the Visa modal, fill Visa Type / Entry Type / Duration / "
                            + "validity dates / Remarks, upload the passport+visa copies, click them, then Add",
                    "The visa row is added (mandatory for a passport holder)", visa, visaOk ? "PASS" : "FAIL");
        }

        // Section 2 — NOK / Guarantor.
        String nok = ip.fillNokSection();
        boolean nokOk = nok != null && nok.contains("added=true");
        step(page, "Fill · NOK / Guarantor", "Title/Name/Relationship/Mobile+CountryCode/NRIC/Occupation/Country, same Postcode as the patient (City/District + State auto-populate) → Add",
                "A Next-of-Kin row is added", nok, nokOk ? "PASS" : "MANUAL");

        // Section 3 — Payor Information: select Payor Mode, select Payor Status, enter Payor.
        String payor = ip.fillPayorSection();
        boolean payorOk = payor != null && payor.toLowerCase().contains("self");
        step(page, "Fill · Payor Information", "Select Payor Mode (ASSOCIATE COMPANY) -> select Payor Status (Self) -> enter Payor (self)",
                "Payor Mode, Payor Status and Payor all hold a value", payor, payorOk ? "PASS" : "MANUAL");

        // Section 4 — Admission Information.
        String admission = ip.fillAdmissionSection();
        step(page, "Fill · Admission Information", "Admission Location/Department/Doctor/Type/Source/Billing Class/Purpose (Non-Presence off)",
                "Admission section populated", admission, "PASS");

        // Section 5 — Room Type + Ward, then tick one available row in the Census Bed List grid.
        String bed = ip.selectRoomTypeAndWard();
        boolean bedOk = bed != null && bed.contains("RoomType=") && bed.contains("Ward=") && !bed.startsWith("ERR");
        step(page, "Select Room Type, Ward", "Select Room Type (Bed Class) + Ward",
                "Room Type and Ward are both set", bed, bedOk ? "PASS" : "FAIL");

        // Tick a vacant bed row — per request, added back after the combination-scanning version of this was
        // removed for hanging live 30+ minutes (see tickVacantBedRow()'s Javadoc). Only meaningful when a bed
        // is actually required (Non-Presence off); scrollToSection brings the grid into frame for the screenshot.
        scrollToSection("Vacant\\s*Bed");
        String bedRow = ip.tickVacantBedRow();
        boolean bedRowOk = bedRow != null && bedRow.startsWith("Bed=");
        step(page, "Tick a Vacant Bed row", "In the Census Bed List grid, tick the checkbox of any available (non-occupied) bed",
                "A bed row is selected", bedRow, bedRowOk ? "PASS" : "FAIL");

        // Section 6 — Additional Doctors (expand the panel, pick Classification + Doctor, Add).
        String addDocs = ip.fillAdditionalDoctors();
        boolean addDocsOk = addDocs != null && addDocs.contains("rows=") && !addDocs.contains("rows=0");
        // fillAdditionalDoctors() expands the panel but the page's scroll position was left wherever the PREVIOUS
        // section (Room Type/Ward) put it — a viewport screenshot taken from there shows Payor/Admission
        // Information instead of the Additional Doctors grid this step is actually about. Bring it into view first.
        scrollToSection("Additional\\s*Doctors");
        step(page, "Fill · Additional Doctors", "Expand Additional Doctors (FillAdditionDropDown), select Classification + Additional Doctor, click Add",
                "A doctor row is added to the Additional Doctors grid", addDocs, addDocsOk ? "PASS" : "MANUAL");

        addSummary("NOK / Guarantor", nok == null ? "-" : nok);
        addSummary("Payor", payor == null ? "-" : payor);
        addSummary("Vacant Bed", (bed == null ? "-" : bed) + " | " + (bedRow == null ? "-" : bedRow));
        addSummary("Additional Doctors", addDocs == null ? "-" : addDocs);

        int tabsBefore = page.context().pages().size();
        String toast = ip.saveAdmissionAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("admitted");
        // IUDAdmission rejects an incomplete form WITHOUT a toast, so "no toast" on its own says nothing. Name the
        // mandatory fields that are still empty — otherwise every missing field costs a whole run to discover.
        // Prefer the APP's own verdict where the build offers one (it tags each offender with `has-error`); our
        // asterisk scan misreads select2 widgets as empty and has named innocent fields on that build.
        String flagged = ok ? "" : ip.appFlaggedMandatory();
        // A save that produced no toast, no flagged field AND no API call has a known cause on this build — name
        // it rather than leaving "no toast appeared" for someone to re-diagnose. Only reported when the screen
        // really is in that state (it re-reads the live callingMode), so a genuine validation stop still reads as one.
        String silent = ok ? "" : ip.diagnoseSilentSave();
        String missing = ok ? "" : ip.describeEmptyMandatory();
        String baseActual = toast == null || toast.isEmpty()
                ? "No success toast appeared" + (!flagged.isEmpty()
                    ? " — the screen flagged: " + flagged
                    : !silent.isEmpty()
                        ? " — CAUSE: " + silent
                        : missing.isEmpty()
                            ? " (and no mandatory field is empty — the save was rejected silently)"
                            : " — mandatory fields still EMPTY (our scan, unconfirmed by the app): " + missing)
                : toast;
        // A rejection toast like "Please Enter First Name!" reads as if the field was simply left blank — it
        // was NOT: it was filled, then the app's own async patient-lookup (fired by NRIC/Mobile/Passport) wiped
        // it back out before Save could see it. Lead the FAIL reason with that cause-and-effect explicitly
        // instead of tacking it on, so the report doesn't misreport the field as never having been entered.
        String clearedLog = ip.clearedFieldsSummary();
        String actual;
        if (!clearedLog.isEmpty()) {
            actual = ok
                    ? baseActual + " | NOTE: field(s) below were cleared after being entered (the app's own"
                        + " async patient-lookup) and had to be restored mid-save: " + clearedLog
                    : "Save failed (" + baseActual + ") even though the field(s) below WERE filled — the app's"
                        + " own async patient-lookup cleared them again right after entry, before Save could see"
                        + " them: " + clearedLog;
        } else {
            actual = baseActual;
        }
        step(page, "Save admission", "Click Save (IUDAdmission); wait for the toast",
                "'Patient Admitted Successfully.' toast", actual, ok ? "PASS" : "FAIL");
        if (!clearedLog.isEmpty()) addSummary("Field(s) cleared after entry (app wipe)", clearedLog);

        // Check for report tabs regardless of the toast verdict above: a rejection toast does not prove no
        // tab opened, and the later report tabs open several seconds after Save, so a tab that HASN'T
        // appeared yet at this point is not evidence either way. Returning early here on any "FAIL" toast
        // was silently dropping that evidence for every run that reached this point. The 12s wait below now
        // always runs; a genuine failure just costs those extra seconds rather than losing the tabs.

        // Save opens the report tab(s) — the later ones open a few seconds after save, so wait, then capture.
        page.waitForTimeout(12000);
        java.util.List<Page> newTabs = new java.util.ArrayList<>();
        java.util.List<Page> all = page.context().pages();
        for (int i = tabsBefore; i < all.size(); i++) newTabs.add(all.get(i));
        addSummary("Report tabs generated", String.valueOf(newTabs.size()));
        int n = 0;
        for (Page rpt : newTabs) {
            n++;
            String url = rpt.url();
            String label = url.contains("IPDReport") ? "IPD Admission Report"
                    : url.contains("IPDPatientLabel") ? "IPD Patient Label"
                    : url.contains("WristBand") ? "Patient Wrist Band"
                    : url.contains("RegistrationReport") ? "Patient Sticker"
                    : url.contains("nhisformstest") ? "Consent Form"
                    : "Report " + n;
            byte[] png = ip.captureReportPng(rpt);
            // A tab that opened is not a report that rendered — the Crystal pages happily serve
            // "Server Error in '/' Application / Logon failed" and that screenshots just as well.
            String err = reportPageError(rpt);
            if (!err.isEmpty()) {
                step(png, "Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", "Report FAILED to render — " + err + "  (" + url + ")", "FAIL");
            } else if (png != null && png.length > 0) {
                step(png, "Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", label + " captured (" + url + ")", "PASS");
            } else {
                step("Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", label + " tab opened but nothing could be captured: " + url, "MANUAL");
            }
            // The Consent Form tab is a live PDPA form (Save As Draft / Submit / Print / Back), not a static
            // report — capturing it as opened only proves it RENDERED, not that it was actually submitted.
            if (err.isEmpty() && label.equals("Consent Form")) {
                String submitResult = ip.submitConsentForm(rpt);
                boolean submitOk = !submitResult.toLowerCase().contains("not found")
                        && !submitResult.toLowerCase().contains("failed")
                        && !submitResult.toLowerCase().contains("error");
                byte[] afterPng = ip.captureReportFullPage(rpt);
                if (afterPng != null && afterPng.length > 0) {
                    step(afterPng, "Consent Form · Submit", "Click Submit on the Consent Form tab, answer any confirm",
                            "The consent form is submitted (a confirmation toast, or the form becomes read-only)",
                            submitResult, submitOk ? "PASS" : "FAIL");
                } else {
                    step("Consent Form · Submit", "Click Submit on the Consent Form tab, answer any confirm",
                            "The consent form is submitted (a confirmation toast, or the form becomes read-only)",
                            submitResult, submitOk ? "PASS" : "FAIL");
                }
            }
        }
        if (newTabs.isEmpty()) {
            step("Admission report", "Save opens the admission report in another tab",
                    "The admission report is shown", "No report tab detected", "MANUAL");
        }
        page.bringToFront();

        addSummary("Application URL", BASE + "/#/Admission");
        addSummary("Patient", patient);
    }
}
