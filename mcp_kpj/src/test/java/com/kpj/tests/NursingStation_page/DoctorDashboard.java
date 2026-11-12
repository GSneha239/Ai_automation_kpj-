package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// The page object is also named DoctorDashboard — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Doctor Dashboard</b> ({@code #/DoctorDashboard}).
 *
 * <ol>
 *   <li>Open the screen from the menu, click the <b>OP</b> tab.</li>
 *   <li>Select the From Date and To Date (a 1 month range), click <b>Search</b>.</li>
 *   <li>Select a patient from the table, click <b>New Case</b>, change the Department and the Doctor in the
 *       <i>View Case</i> pop up, <b>Save</b> and verify the success toast.</li>
 *   <li><b>Forms</b>: pick a form from the drop down, <b>Add</b> (the form opens in the next tab), <b>Submit</b> -&gt;
 *       <b>Yes</b> -&gt; verify the success message -&gt; <b>OK</b> -&gt; Submit again.</li>
 *   <li>Back on the dashboard: select the patient, <b>View Forms</b> — the <i>View Consent/Forms</i> pop up opens
 *       (screenshot) — <b>Close</b>.</li>
 * </ol>
 *
 * <p>Knobs: {@code -Dmrn=} the patient (default: the first row of the table), {@code -Dform=} the form to pick
 * (default {@link #DEFAULT_FORM}). &#9888; A run RECORDS REAL data: a new EMR case and a submitted form for that patient.</p>
 */
public class DoctorDashboard extends DevHisBase {

    /** Used when {@code -Dform=} is not supplied (an external form: Add opens it in a new tab). */
    public static final String DEFAULT_FORM = "ADMISSION ORDER";

    public DoctorDashboard() { super("NursingStation_DoctorDashboard"); }

    public static void main(String[] args) {
        DoctorDashboard t = new DoctorDashboard();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private void skipped(String name, String desc, String expected, String why) {
        step(name, desc, expected, "Not run - " + why, "SKIPPED");
    }

    /** A step whose screenshot was taken elsewhere (a toast, another tab); falls back to the dashboard when there is none. */
    private void stepPng(byte[] png, Page fallback, String name, String desc, String expected, String actual, String status) {
        if (png != null && png.length > 0) step(png, name, desc, expected, actual, status);
        else step(fallback, name, desc, expected, actual, status);
    }

    private static String nz(String s) { return s == null ? "" : s.trim(); }

    private static boolean sameText(String a, String b) { return nz(a).equalsIgnoreCase(nz(b)); }

    /** Close every tab except the dashboard's (report / consent tabs a Save opened). */
    private void closeExtraTabs() {
        try {
            for (Page pg : new java.util.ArrayList<>(page.context().pages())) if (pg != page && !pg.isClosed()) pg.close();
        } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }
    }

    /**
     * The Referred Patients pop up. For each action button (Book Appointment, Registration, Admission): back on the dashboard, open the pop
     * up, SELECT THE PATIENT (tick the row's Select box), click the button; when its page opens, check it carries the referral over and fill
     * it in and save it the way the existing tests do (OP Existing Patient Appointment, OP Registration visit, IP Admission).
     * Knobs: {@code -Dreferral.mrn=} pins the referral; {@code -Dreferral.save=false} only opens and checks each page (nothing is filled or saved);
     * {@code -Dreferral.noTick=true} adds the "button clicked with nothing ticked" checks. Returns the one-line result for the Test Summary.
     */
    private String referredPatientsFlow(com.kpj.pages.NursingStation_page.DoctorDashboard dd) {
        String pin = System.getProperty("referral.mrn", "").trim();
        boolean fill = !"false".equalsIgnoreCase(System.getProperty("referral.save", "true").trim());
        boolean noTick = Boolean.parseBoolean(System.getProperty("referral.noTick", "false").trim());
        boolean rp = dd.clickReferredPatients();
        java.util.List<java.util.Map<String, String>> refs = rp ? dd.referralRows() : new java.util.ArrayList<>();
        java.util.Map<String, String> ref = null;
        for (java.util.Map<String, String> r : refs) if (pin.isEmpty() || pin.equals(r.get("MRN"))) { ref = r; break; }
        boolean have = ref != null;
        String mrn = have ? nz(ref.get("MRN")) : "", name = have ? nz(ref.get("Patient Name")) : "";
        String deptTo = have ? nz(ref.get("Department To")) : "", doctorTo = have ? nz(ref.get("Referred To")) : "";
        String deptFrom = have ? nz(ref.get("Department From")) : "";
        String actual22 = "";
        if (have) actual22 = "Pop up opened: " + refs.size() + " referral(s) listed; " + name + " (MRN " + mrn + "), " + nz(ref.get("Category"))
                + ", referred by " + nz(ref.get("Referred By")) + " to " + doctorTo + " (" + deptTo + "), remark \"" + nz(ref.get("Remark")) + "\""
                + (deptFrom.isEmpty() ? "; the Department From column is blank" : "; Department From " + deptFrom);
        else if (rp) actual22 = "The pop up opened but lists no referral" + (pin.isEmpty() ? "" : " for MRN " + pin) + " (button: " + dd.referredButtonText() + ")";
        // The button carries the count: "Referred Patients (0)" means nobody is referred, so there is no list to open (every referral is used up
        // by a Book Appointment / Registration / Admission, so the list empties as this flow runs). That is a data state, not a broken button.
        String referredBtn = dd.referredButtonText();
        boolean noneReferred = !rp && referredBtn != null && referredBtn.matches(".*\\(\\s*0\\s*\\).*");
        if (!rp && noneReferred) actual22 = "The button reads \"" + referredBtn.trim() + "\" - no patient is referred right now, so there is no list to open (each Book Appointment / Registration / Admission uses a referral up)";
        else if (!rp) actual22 = "The Referred Patients pop up did not open (button: " + referredBtn + "; " + dd.describeScreen() + ")";
        step(page, "Click Referred Patients", "Click Referred Patients under the table",
                "The Referred Patients pop up opens and lists the referred patients", actual22, have ? "PASS" : (noneReferred ? "MANUAL" : "FAIL"));
        if (!have) {
            // One row per flow, in the order they run (each finishes before the next starts), so the report shows exactly which one was not run.
            String why = noneReferred ? "no patient is referred - the button reads \"Referred Patients (0)\"" : "there is no referred patient to work with";
            skipped("Book Appointment flow", "Tick the referred patient, click Book Appointment, fill the Appointment page and Save",
                    "The Appointment page opens for the patient and the appointment is saved", why);
            skipped("Registration flow", "Back on the dashboard: tick the referred patient, click Registration, fill the visit and Save",
                    "The Registration page opens for the patient and the visit is saved", why);
            skipped("Admission flow", "Back on the dashboard: tick the referred patient, click Admission, fill the admission and Save",
                    "The Admission page opens for the patient and the admission is saved", why);
            return "no referred patient on the list";
        }

        if (noTick) {
            String[] r1 = dd.clickReferralButton("fnBookAppointment");
            boolean w1 = r1[0].toLowerCase().contains("select a patient");
            step(page, "Book Appointment with no patient ticked", "Click Book Appointment without ticking a row", "A message asks to select a patient from the referral list",
                    w1 ? "Message: \"" + r1[0] + "\"" : (r1[0].isEmpty() ? "No message and nothing happened" : "Message: \"" + r1[0] + "\""), w1 ? "PASS" : "FAIL");
            String[] r2 = dd.clickReferralButton("fnPatientRegistration");
            boolean w2 = r2[0].toLowerCase().contains("select a patient");
            step(page, "Registration with no patient ticked", "Click Registration without ticking a row", "A message asks to select a patient from the referral list",
                    w2 ? "Message: \"" + r2[0] + "\"" : (r2[0].isEmpty() ? "Nothing happened - no message (" + r2[1] + ")" : "Message: \"" + r2[0] + "\""), w2 ? "PASS" : "FAIL");
            String[] r3 = dd.clickReferralButton("fnPatientAdmission");
            boolean w3 = r3[0].toLowerCase().contains("select a patient");
            step(page, "Admission with no patient ticked", "Click Admission without ticking a row", "A message asks to select a patient from the referral list",
                    w3 ? "Message: \"" + r3[0] + "\"" : (r3[0].isEmpty() ? "Nothing happened - no message (" + r3[1] + ")" : "Message: \"" + r3[0] + "\" (" + r3[1] + ")"), w3 ? "PASS" : "FAIL");
        }

        String wanted = System.getProperty("referral.buttons", "appointment,registration,admission").toLowerCase();
        String[] sums = new String[3];
        String[][] kinds = {
                {"fnBookAppointment", "Book Appointment", "New", "PatientData.MRNO", "PatientData.FirstName", "PatientData.DepartmentID", "PatientData.DoctorModalityID", "Appointment"},
                {"fnPatientRegistration", "Registration", "Visit", "Registration.MRNo", "Registration.FirstName", "Visit.DepartmentID", "Visit.DoctorID", "Registration"},
                {"fnPatientAdmission", "Admission", "Admission", "Registration.MRNo", "Registration.FirstName", "Admission.DepartmentID", "Admission.AttendingDoctorID,Admission.DoctorID", "Admission"}};
        for (int k = 0; k < kinds.length; k++) {
            String[] kd = kinds[k];
            String btn = kd[1], screen = kd[7];
            if (!wanted.contains(screen.toLowerCase())) { sums[k] = btn + ": not run (-Dreferral.buttons)"; continue; }
            boolean first = k == 0 && !noTick;
            // back on the dashboard with the pop up open
            if (!first) {
                closeExtraTabs();
                String why = "";
                if (!dd.backToDashboard()) why = "the Doctor Dashboard could not be reopened";
                else if (!dd.clickReferredPatients()) why = "the Referred Patients pop up did not open again";
                if (!why.isEmpty()) {
                    step(page, "Select the patient (for " + btn + ")", "Tick the referred patient's Select box", "The row is ticked", "Could not get to the pop up - " + why, "FAIL");
                    skipped("Click " + btn + " - the " + screen + " page opens", "Click " + btn, "The " + screen + " page opens for the patient", "the pop up did not open");
                    sums[k] = btn + ": not tested (" + why + ")";
                    continue;
                }
            }
            // SELECT THE PATIENT
            boolean ticked = dd.tickReferral(mrn);
            step(page, "Select the patient (for " + btn + ")", "Tick the Select box of the referred patient's row (" + name + ", MRN " + mrn + ")",
                    "The row is ticked (selected)", ticked ? "Row of " + name + " (MRN " + mrn + ") is ticked" : "The row could not be ticked", ticked ? "PASS" : "FAIL");
            if (!ticked) {
                skipped("Click " + btn + " - the " + screen + " page opens", "Click " + btn, "The " + screen + " page opens for the patient", "the patient could not be selected");
                sums[k] = btn + ": the patient could not be selected";
                continue;
            }
            // CLICK THE BUTTON
            String[] clk = dd.clickReferralButton(kd[0]);
            String[] f = dd.screenFacts(kd[2], kd[3], kd[4], kd[5], kd[6]);
            boolean open = f.length > 1 && mrn.equals(f[1]);
            dd.scrollTop();
            step(page, "Click " + btn + " - the " + screen + " page opens", "Click " + btn + " with the patient selected",
                    "The " + screen + " page opens for the referred patient",
                    f.length == 1 ? "Nothing opened - " + f[0] + (clk[0].isEmpty() ? "" : " (message: " + clk[0] + ")")
                            : "Opened the " + screen + " page (" + clk[1] + ") for MRN " + f[1] + " " + f[2] + (open ? "" : " - but the MRN is not the referred patient's (" + mrn + ")"),
                    open ? "PASS" : "FAIL");
            if (!open) {
                sums[k] = btn + ": the " + screen + " page did not open for the patient";
                continue;
            }
            // does the page carry the referral's department and doctor?
            boolean carry = sameText(f[3], deptTo) && !doctorTo.isEmpty() && f[4].toLowerCase().contains(doctorTo.toLowerCase());
            dd.revealField(kd[5]);
            step(page, screen + " page carries the referral's Department and Doctor", "Read Department and Doctor on the " + screen + " page",
                    "Department " + deptTo + " and Doctor " + doctorTo + " are filled in",
                    "Department " + (f[3].isEmpty() ? "(none)" : f[3]) + ", Doctor " + (f[4].isEmpty() ? "(none)" : f[4]) + (f[5].isEmpty() ? "" : "; the app said: " + f[5]),
                    carry ? "PASS" : "FAIL");
            String carried = "opens, Department " + (f[3].isEmpty() ? "(none)" : f[3]) + ", Doctor " + (f[4].isEmpty() ? "(none)" : f[4]);
            if (!fill) { sums[k] = btn + " " + carried + " (not filled)"; continue; }
            // FILL THE PAGE like the existing test, and save
            String saved;
            if (k == 0) saved = fillAppointmentPage(dd, mrn, deptTo);
            else if (k == 1) saved = fillRegistrationPage(dd, mrn);
            else saved = fillAdmissionPage(dd, mrn);
            sums[k] = btn + " " + carried + "; " + saved;
        }
        closeExtraTabs();
        return name + " (MRN " + mrn + ") referred to " + doctorTo + " / " + deptTo + ": " + nz(sums[0]) + " | " + nz(sums[1]) + " | " + nz(sums[2]);
    }

    /** Book Appointment page: filled and saved with the same methods as OP > Appointment > Existing. Returns a short result. */
    private String fillAppointmentPage(com.kpj.pages.NursingStation_page.DoctorDashboard dd, String mrn, String referralDept) {
        com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage appt = new com.kpj.pages.Op_page.Appointment.ExistingPatientAppointmentPage(page);
        try { appt.attachToOpenForm(); }
        catch (Exception e) {
            step(page, "Fill the Appointment page", "Wait for the Appointment form", "The form and its master data are ready", "The form was not ready - " + e.getMessage().split("\n")[0], "FAIL");
            return "the Appointment form was not ready";
        }
        String nric = appt.fillNricIfEmpty();
        // Appointment Type = Department: the booking then uses the Department's own schedule and needs no Doctor (the referral's Doctor has no clinic, so a Doctor booking gets "No clinic on this date").
        String typeNow = dd.selectAppointmentTypeDepartment();
        boolean typeOk = typeNow.equalsIgnoreCase("Department");
        step(page, "Select Appointment Type = Department", "Choose Department in the Appointment Type drop-down", "Appointment Type shows Department (the Doctor box is then not needed)",
                "Appointment Type shows \"" + (typeNow.isEmpty() ? "(box not found)" : typeNow) + "\"", typeOk ? "PASS" : "FAIL");
        String config = appt.fillAppointmentConfig(false);
        // Department list is walked starting at the referral's own Department, until one has a free slot in the Next Schedule.
        String walk = dd.walkAppointmentDepartments(12, false, referralDept);
        boolean walkOk = !walk.startsWith("(");
        boolean configOk = walkOk && !config.contains("no-opt") && !config.contains("no-select");
        step(page, "Fill Department, Booking Type, Payor Type",
                "Select Booking Type, Payor Type as OP > Appointment > Existing does; choose the referral's Department (else walk the Departments) until it has a free slot",
                "All selects accept a real value, and the Department has a free slot",
                (config.indexOf(" | Department=") > 0 ? config.substring(0, config.indexOf(" | Department=")) : config) + " | " + (walkOk ? walk : "NO Department with a free slot - " + walk) + (nric.isEmpty() ? "" : " | NRIC was empty - entered " + nric), configOk ? "PASS" : "FAIL");
        // Days: the page refuses an age of 0 days ("Please Enter Days")
        String days = dd.enterDays(1);
        boolean daysOk = !days.startsWith("(");
        step(page, "Enter Days", "Enter 1 in Days when the box holds 0 or is blank", "Days holds a number above 0 so the page accepts the age",
                days.isEmpty() ? "(no answer)" : days + " - age now " + dd.ageText(), daysOk ? "PASS" : "FAIL");
        // when the walk had to move to a later date (no clinic on the first ones) that date stays; otherwise tomorrow
        String tomorrow = dd.walkDate.isEmpty() ? appt.setAppointmentDateTomorrow() : dd.walkDate;
        String slot = appt.selectFreeSlot();
        step(page, "Select the appointment slot", "Set the date to tomorrow; pick a free slot from Current / Next Schedule",
                "A slot is selected and From/To are populated", slot == null ? "No free slot found (date " + tomorrow + ")" : "Slot selected: " + slot + " (date " + tomorrow + ")",
                slot == null ? "FAIL" : "PASS");
        String mandatory = appt.checkAndFillMandatoryFields(false);
        boolean stillEmpty = mandatory.contains("STILL EMPTY");
        step(page, "Check all mandatory fields are filled (fill if not)", "Re-check Payor Type/Booking Type/Appointment Type/Department/Date/From/To (no Doctor for a Department appointment)",
                "No mandatory field is left unfilled", mandatory.isEmpty() ? "All mandatory fields were already filled" : mandatory, stillEmpty ? "FAIL" : "PASS");
        int listSize = appt.clickAdd();
        step(page, "Click Add", "Click Add (fnAddToMultipleApptList) before Save", "The appointment is added to the booking list",
                listSize > 0 ? "Added (list size " + listSize + ")" : "Add did not register (list empty)", listSize > 0 ? "PASS" : "FAIL");
        if (listSize <= 0) return "the appointment was not added to the list";
        Page rpt = appt.saveAndCaptureReport();
        java.util.List<String> msgs = appt.capturedMessages();
        boolean ok = rpt != null || com.kpj.pages.Op_page.Appointment.BookAppointmentPage.saveSucceeded(msgs);
        String said = msgs.isEmpty() ? "(no message captured)" : String.join(" | ", msgs);
        String age = dd.ageText();
        String ageNote = !ok && said.toLowerCase().contains("enter days") && age.endsWith(" 0D")
                ? " - the Days box still reads 0 (" + age + ")" : "";
        step(page, "Click Save and confirm", "Click Save; confirm 'Do You Want To Save'; accept the slot prompt",
                "The appointment is saved (success message, report opens)",
                (ok ? "Saved - " : "NOT saved - ") + said + ageNote + (rpt != null ? "; the appointment report opened: " + rpt.url() : "; no report tab opened"), ok ? "PASS" : "FAIL");
        return ok ? "appointment saved (" + said + ")" : "appointment NOT saved (" + said + ")";
    }

    /** Registration page (VisitScreen): the Visit / Payor sections filled and saved with the same methods as OP > Registration. Returns a short result. */
    private String fillRegistrationPage(com.kpj.pages.NursingStation_page.DoctorDashboard dd, String mrn) {
        com.kpj.pages.Op_page.RegistrationPage reg = new com.kpj.pages.Op_page.RegistrationPage(page);
        String kin = dd.fixNextOfKinMobile();
        if (!kin.isEmpty()) step(page, "Correct the Next of Kin mobile number", "The page refuses to save a kin whose mobile is not 10 digits: Edit the kin, enter 10 digits, Update",
                "Every kin mobile has 10 digits", kin, kin.startsWith("(") ? "FAIL" : "PASS");
        reg.fillVisitInformation();
        boolean visitOk = reg.visitInformationComplete();
        try { openSection("visit information"); } catch (Exception ignore) { }
        step(page, "Fill Visit Information", "Patient Source External, Encounter Outpatient, Department + Doctor, Queue 1, Cash - as OP > Registration does",
                "Visit set - Department, Primary Doctor and Visit Type hold a value", (visitOk ? "Visit Information filled - " : "Visit Information INCOMPLETE - ") + reg.lastVisitState,
                visitOk ? "PASS" : "FAIL");
        boolean payorSelf = reg.selectPayorSelf();
        try { scrollToSection("payor"); } catch (Exception ignore) { }
        step(page, "Fill Payor Information", "Select Payor = Self", "The patient is marked self-pay",
                payorSelf ? "Payor = Self - payor form " + reg.lastPayorState : "Payor form NOT populated - " + reg.lastPayorState, payorSelf ? "PASS" : "MANUAL");
        // the patient's own title must survive the Save's retry loop, which re-selects the profile's title
        com.kpj.pages.Op_page.RegistrationPage.PatientProfile p = com.kpj.pages.Op_page.RegistrationPage.PatientProfile.random();
        Object title = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.PrefixID'); return e&&e.options[e.selectedIndex]?e.options[e.selectedIndex].text.trim():''; }");
        if (title != null && !title.toString().isEmpty() && !title.toString().startsWith("-")) p.prefix = title.toString();
        String block = reg.clickSaveAwaitConfirm(p);
        String detail = "";
        if (block != null) {
            if (!reg.lastMandatoryFields.isEmpty()) detail += " - the screen flagged: " + reg.lastMandatoryFields;
            String warn = dd.visibleAlertText();
            if (!warn.isEmpty()) detail += " - the app warned: " + warn;
        }
        step(page, "Click Save", "Click Save", "'Do You Want To Save' dialog", block == null ? "Confirm dialog shown" : "Save blocked: " + block + detail, block == null ? "PASS" : "FAIL");
        if (block != null) return "visit NOT saved (" + block + detail + ")";
        com.kpj.pages.Op_page.RegistrationPage.SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        boolean queueTaken = !save.saved && com.kpj.pages.Op_page.RegistrationPage.queueNumberTaken(save.toast);
        step(page, "Confirm Save", "Confirm the 'Do You Want To Save' dialog", "The registration/visit is saved",
                (save.toast == null || save.toast.isEmpty() ? "Save confirmed" : save.toast) + " [queue no. " + reg.lastQueueNo + "]" + (queueTaken ? " - queue number already assigned" : ""),
                save.saved ? "PASS" : "FAIL");
        boolean consent = reg.waitConsentDetailsModal();
        step(page, "Consent Details modal (PDPA)", "After Save the in-app 'Consent Details' modal appears", "'PERSONAL DATA NOTICE & CONSENT' record is shown",
                consent ? "Consent Details modal shown" : "The Consent Details modal did not appear (an existing patient - not shown after a visit save)", consent ? "PASS" : "MANUAL");
        if (consent) reg.closeConsentDetailsModal();
        boolean ok = save.saved || consent;
        return ok ? "visit saved (queue " + reg.lastQueueNo + ")" : "visit NOT saved (" + (save.toast == null ? "" : save.toast) + ")";
    }

    /** Admission page: the Admission section, bed, doctors filled and saved with the same methods as IP > Admission. Returns a short result. */
    private String fillAdmissionPage(com.kpj.pages.NursingStation_page.DoctorDashboard dd, String mrn) {
        com.kpj.pages.Ip.Admission ip = new com.kpj.pages.Ip.Admission(page);
        String kin = dd.fixNextOfKinMobile();
        if (!kin.isEmpty()) step(page, "Correct the Next of Kin mobile number", "The page refuses to save a kin whose mobile is not 10 digits: Edit the kin, enter 10 digits, Update",
                "Every kin mobile has 10 digits", kin, kin.startsWith("(") ? "FAIL" : "PASS");
        String admission = ip.fillAdmissionSection();
        step(page, "Fill Admission Information", "Admission Location/Department/Doctor/Type/Source/Billing Class/Purpose - as IP > Admission does",
                "The Admission section is populated", admission, "PASS");
        String bed = ip.selectRoomTypeAndWard();
        boolean bedOk = bed != null && bed.contains("RoomType=") && bed.contains("Ward=") && !bed.startsWith("ERR");
        step(page, "Select Room Type, Ward", "Select Room Type (Bed Class) + Ward", "Room Type and Ward are both set", bed, bedOk ? "PASS" : "FAIL");
        try { scrollToSection("Vacant\\s*Bed"); } catch (Exception ignore) { }
        String bedRow = ip.tickVacantBedRow();
        boolean bedRowOk = bedRow != null && (bedRow.startsWith("Bed=") || bedRow.startsWith("NonPresence="));
        step(page, "Tick a Vacant Bed row", "Tick an available bed (Census, else Non-Census, else Non-Presence)", "A bed row is selected, or the admission is switched to Non-Presence",
                bedRow, bedRowOk ? "PASS" : "FAIL");
        String addDocs = ip.fillAdditionalDoctors();
        boolean addDocsOk = addDocs != null && addDocs.contains("rows=") && !addDocs.contains("rows=0");
        try { scrollToSection("Additional\\s*Doctors"); } catch (Exception ignore) { }
        step(page, "Fill Additional Doctors", "Expand Additional Doctors, select Classification + Additional Doctor, click Add", "A doctor row is added", addDocs, addDocsOk ? "PASS" : "MANUAL");
        String recheck = ip.recheckAndRefillAll();
        boolean recheckOk = ip.recheckEmptyAfter.isEmpty();
        step(page, "Re-check all fields and refill any empty one", "Scan the whole form for empty mandatory fields and refill them", "No mandatory field is left empty before Save",
                recheck, recheckOk ? "PASS" : "MANUAL");
        String toast = ip.saveAdmissionAndGetToast();
        String allToasts = dd.visibleToastTexts();
        String toastText = toast == null ? "" : toast.replaceFirst("^[\\u00d7x\\s]*KPJ\\s*Portal\\s*", "").trim();
        boolean ok = toastText.toLowerCase().matches(".*\\bpatient\\s+admitted\\s+successfully\\b.*");
        String why = "";
        if (!ok) {
            String flagged = ip.appFlaggedMandatory();
            String silent = ip.diagnoseSilentSave();
            String warn = dd.visibleAlertText();
            why = (toast == null || toast.isEmpty()
                    ? "No success toast appeared" + (!flagged.isEmpty() ? " - the screen flagged: " + flagged : (!silent.isEmpty() ? " - CAUSE: " + silent : ""))
                    : toast) + (warn.isEmpty() ? "" : " - the app warned: " + warn);
        }
        boolean alreadyAdmitted = !ok && allToasts.toLowerCase().contains("active admission");
        String refusal = alreadyAdmitted ? allToasts.replaceFirst("(?s).*?(This patient already has an active admission[^!]*!?[^|]*).*", "$1").trim() : "";
        step(page, "Save admission", "Click Save (IUDAdmission); wait for the toast", "'Patient Admitted Successfully.' toast",
                ok ? "Toast verified - \"" + toastText + "\""
                   : (alreadyAdmitted ? "The app refused the admission: \"" + refusal + "\" (business rule - not a defect; use a referred patient who is not admitted)" : why + (allToasts.isEmpty() ? "" : " | on screen: " + allToasts)),
                ok ? "PASS" : (alreadyAdmitted ? "MANUAL" : "FAIL"));
        return ok ? "admission saved (" + toastText + ")" : (alreadyAdmitted ? "admission refused - the patient already has an active admission" : "admission NOT saved (" + why + ")");
    }

    @Override
    protected void body() {
        meta("Nursing Station - Doctor Dashboard", "Nursing Station > Doctor Dashboard",
                "&#9888; Records REAL data: a new EMR case for the selected patient and a submitted form. OP tab: 1 month range, Search, "
                        + "select a patient, New Case (change Department + Doctor) and Save; Forms: pick a form, Add (opens in the next tab), "
                        + "Submit, Yes, OK; then View Forms and Close.");

        String wantMrn = System.getProperty("mrn", "").trim();
        String formName = System.getProperty("form", DEFAULT_FORM).trim();

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.DoctorDashboard dd = new com.kpj.pages.NursingStation_page.DoctorDashboard(page);

        // 1) Navigate — FAIL and stop if the app serves a different screen.
        boolean on = dd.navigateViaMenu();
        step(page, "Open Doctor Dashboard screen", "Click Nursing Station -> Doctor Dashboard",
                "The Doctor Dashboard is shown (OP List, IP List, Bed Availability ... tabs)",
                on ? "Opened " + dd.currentScreen() + " - tabs: " + dd.tabNames() : "Did NOT reach the screen (" + dd.currentScreen() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Doctor Dashboard not reached"); return; }

        // 2) OP tab
        boolean op = dd.clickOpTab();
        step(page, "Click the OP tab", "Click the OP List tab",
                "The OP List filters (From Date, To Date, Location ...) and the visit table are shown",
                op ? "OP List tab is open (From Date " + dd.readDate("queue.fromdate") + ", To Date " + dd.readDate("queue.todate") + ")"
                   : "The OP List tab did not show its filters (" + dd.describeScreen() + ")",
                op ? "PASS" : "FAIL");
        if (!op) { addSummary("Result", "FAILED - OP tab not shown"); return; }

        // -Dreferral.only=true runs just the Referred Patients part (login, open the screen, then straight to the pop up)
        if (Boolean.parseBoolean(System.getProperty("referral.only", "false").trim())) {
            addSummary("Referred Patients", referredPatientsFlow(dd));
            return;
        }

        // 3) From / To date, 1 month
        String range = dd.setOneMonthRange();
        boolean rangeOk = !range.startsWith("(");
        if (rangeOk) {
            try {
                DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                LocalDate from = LocalDate.parse(dd.lastFrom, f), to = LocalDate.parse(dd.lastTo, f);
                rangeOk = from.equals(to.minusMonths(1));
                if (!rangeOk) range = range + " - but that is not a 1 month range";
            } catch (Exception e) { rangeOk = false; range = "the dates could not be read back (" + dd.lastFrom + " / " + dd.lastTo + ")"; }
        }
        dd.scrollTop();
        step(page, "Select From Date and To Date (1 month range)", "Pick the From Date one month back and the To Date (today) in the calendars",
                "From Date is one month before the To Date", rangeOk ? range : "Could not set the range - " + range, rangeOk ? "PASS" : "FAIL");
        if (!rangeOk) { addSummary("Result", "FAILED - date range not set"); return; }

        // 4) Search
        int rows = dd.clickSearch();
        dd.scrollTop();
        step(page, "Click Search", "Click Search with the 1 month range",
                "The table lists the visits of the month",
                rows > 0 ? rows + " visit row(s) loaded" + (dd.lastTotalText.isEmpty() ? "" : " (" + dd.lastTotalText + ")")
                         : (rows == 0 ? "Search returned no visit for " + dd.lastFrom + " - " + dd.lastTo : "The Search button was not found"),
                rows > 0 ? "PASS" : "FAIL");
        if (rows <= 0) { addSummary("Result", "FAILED - no visit in the table"); return; }

        // 5) Select a patient from the table
        String sel = dd.selectPatient(wantMrn);
        boolean selOk = !sel.startsWith("(");
        step(page, "Select a patient from the table",
                wantMrn.isEmpty() ? "Select the first patient of the table" : "Select the patient with MRN " + wantMrn,
                "The patient's row is selected", selOk ? sel : "Could not select - " + sel, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED - patient not selected"); return; }
        String patient = dd.describeSelection();

        // 6) New Case -> View Case pop up
        boolean nc = dd.clickNewCase();
        boolean mrnMatches = nc && dd.selectedMrn.equals(dd.caseFormMrn());
        step(page, "Click New Case", "Click New Case",
                "The View Case pop up opens for the selected patient (MRN, Episode No, NRIC, Patient Name, Department, Doctor)",
                nc ? "View Case pop up opened: " + dd.caseFormSummary() + (mrnMatches ? "" : "  - but its MRN is not the selected patient's (" + dd.selectedMrn + ")")
                   : "The View Case pop up did not open (" + dd.describeScreen() + ")",
                mrnMatches ? "PASS" : "FAIL");

        // 7) change Department + Doctor, 8) Save + toast
        String change = "";
        boolean changeOk = false, toastOk = false;
        String toast = "";
        int refused = 0;
        if (nc) {
            // The app keeps ONE case per visit for each Department + Doctor pair and every run saves one, so a pair that is
            // refused ("Case alredy generated with same department and doctor!") is replaced by another and saved again.
            for (int attempt = 1; attempt <= 8; attempt++) {
                change = dd.changeDepartmentAndDoctor();
                changeOk = !change.startsWith("(");
                if (!changeOk) break;
                toast = dd.saveCaseAndGetToast();
                String tl = toast.toLowerCase();
                toastOk = !toast.isEmpty() && !"error".equals(dd.caseToastKind) && (tl.contains("success") || tl.contains("added") || tl.contains("saved"));
                if (toastOk || !tl.contains("same department")) break;
                refused++;
                System.out.println("DoctorDashboard: \"" + toast + "\" for " + change + " - trying another Department + Doctor");
            }
            String note = refused == 0 ? "" : "  [" + refused + " pair(s) were refused as 'already generated' first]";
            stepPng(dd.caseFilledPng, page, "Fill the View Case pop up (change Department and Doctor)", "Select another Department, then another Case Against Doctor",
                    "The Department and the Doctor now hold different values", changeOk ? change + note : "Could not change - " + change, changeOk ? "PASS" : "FAIL");
        } else {
            skipped("Fill the View Case pop up (change Department and Doctor)", "Select another Department, then another Case Against Doctor",
                    "The Department and the Doctor now hold different values", "the View Case pop up did not open");
        }
        if (nc && changeOk) {
            stepPng(dd.caseToastPng, page, "Click Save and verify the success toast", "Click Save in the View Case pop up",
                    "A success toast ('... Case added successfully.') is shown and the pop up closes",
                    toast.isEmpty() ? "No toast appeared"
                            : (toastOk ? "Toast: \"" + toast + "\"" + (dd.caseClosed ? "; the pop up closed" : "; the pop up is still open")
                                       : "Save not confirmed - the app said: \"" + toast + "\" (" + dd.caseToastKind + ")"),
                    toastOk ? "PASS" : "FAIL");
        } else {
            skipped("Click Save and verify the success toast", "Click Save in the View Case pop up",
                    "A success toast ('... Case added successfully.') is shown and the pop up closes",
                    nc ? "the Department / Doctor could not be changed" : "the View Case pop up did not open");
        }
        if (nc && !dd.caseClosed) dd.closeOpenPopup();

        // 9) Forms
        String again = dd.ensureSelected();                       // the grid auto-refreshes and may have dropped the row
        boolean againOk = !again.startsWith("(");
        boolean forms = againOk && dd.clickForms();
        step(page, "Click Forms", "Click Forms",
                "The Forms Details pop up opens (Date, Forms box, Add)",
                forms ? "Forms Details pop up opened for " + patient + (again.isEmpty() ? "" : " (" + again + ")")
                      : (againOk ? "The Forms Details pop up did not open (" + dd.describeScreen() + ")" : "Could not re-select the patient - " + again),
                forms ? "PASS" : "FAIL");

        // 10) pick the form from the drop down
        String pick = "";
        boolean pickOk = false;
        if (forms) {
            pick = dd.pickForm(formName);
            pickOk = !pick.startsWith("(");
            step(page, "Select a form from the Forms drop down", "Open the Forms box and pick \"" + formName + "\" from the list",
                    "The form is chosen in the Forms box", pickOk ? pick : "Could not select the form - " + pick, pickOk ? "PASS" : "FAIL");
        } else {
            skipped("Select a form from the Forms drop down", "Open the Forms box and pick \"" + formName + "\" from the list",
                    "The form is chosen in the Forms box", "the Forms Details pop up did not open");
        }

        // 11) Add -> the form opens in the next tab
        Page ft = null;
        boolean tabOk = false;
        String formUrl = "";
        if (pickOk) {
            ft = dd.clickAddOpenFormTab();
            if (ft != null) {
                String[] facts = dd.formTabFacts(ft, formName);
                String err = reportPageError(ft);
                boolean submitThere = dd.formSubmitOffered(ft);
                formUrl = ft.url();
                tabOk = err.isEmpty() && facts[0].equals(dd.selectedMrn) && "true".equals(facts[2]) && submitThere;
                String actual = err.isEmpty()
                        ? "The form opened in the next tab: " + formUrl + " - " + (facts[1].isEmpty() ? "" : facts[1] + ", ") + "MRN " + facts[0]
                          + (submitThere ? ", Submit button shown" : ", NO Submit button")
                          + ("true".equals(facts[2]) ? "" : ", the form name \"" + formName + "\" is not on the page")
                          + (facts[0].equals(dd.selectedMrn) ? "" : " (expected MRN " + dd.selectedMrn + ")")
                        : "The form tab shows an error - " + err + " (" + formUrl + ")";
                step(dd.shot(ft), "Click Add - the form opens in the next tab", "Click Add in the Forms Details pop up",
                        "The form opens in a new tab for MRN " + dd.selectedMrn + " with a Submit button", actual, tabOk ? "PASS" : "FAIL");
            } else {
                step(page, "Click Add - the form opens in the next tab", "Click Add in the Forms Details pop up",
                        "The form opens in a new tab for MRN " + dd.selectedMrn + " with a Submit button",
                        "No new tab opened after Add (" + dd.describeScreen() + ")", "FAIL");
            }
        } else {
            skipped("Click Add - the form opens in the next tab", "Click Add in the Forms Details pop up",
                    "The form opens in a new tab with a Submit button", "no form was selected");
        }

        // 12..16) Submit, Yes, success message, OK, Submit again — on the form tab
        String confirm = "", msg = "", urlAfter = "";
        boolean confirmOk = false, yesOk = false, msgOk = false, okOk = false;
        if (tabOk) {
            confirm = dd.clickFormSubmit(ft);
            confirmOk = !confirm.startsWith("(") && confirm.toLowerCase().contains("are you sure");
            step(dd.shot(ft), "Click Submit", "Click Submit on the form",
                    "A confirmation pop up asks \"Are you sure ...?\" with No / Yes",
                    confirmOk ? "Confirmation pop up: " + confirm : "No confirmation - " + confirm, confirmOk ? "PASS" : "FAIL");

            if (confirmOk) {
                msg = dd.clickConfirmYes(ft);
                yesOk = !msg.startsWith("(");
                byte[] successPng = dd.shot(ft);
                msgOk = yesOk && msg.toLowerCase().contains("submitted successfully");
                stepPng(successPng, page, "Click Yes in the pop up", "Click Yes in the confirmation pop up",
                        "The form is submitted and a success pop up opens",
                        yesOk ? "Clicked Yes; a pop up opened saying: " + msg : "After Yes - " + msg, yesOk ? "PASS" : "FAIL");
                stepPng(successPng, page, "Verify the success message in the pop up", "Read the message of the pop up",
                        "The pop up says \"Form Submitted Successfully!\"",
                        yesOk ? (msgOk ? "The pop up says: " + msg : "The pop up says something else: " + msg) : "There is no success pop up - " + msg,
                        msgOk ? "PASS" : "FAIL");
                if (yesOk) {
                    urlAfter = dd.clickMessageOk(ft);
                    okOk = !urlAfter.isEmpty();
                    step(dd.shot(ft), "Click OK", "Click OK in the success pop up",
                            "The pop up closes and the submitted form is shown",
                            okOk ? "The pop up closed; the submitted form is now at " + urlAfter : "The success pop up did not close",
                            okOk ? "PASS" : "FAIL");
                } else {
                    skipped("Click OK", "Click OK in the success pop up", "The pop up closes and the submitted form is shown", "no success pop up appeared");
                }
            } else {
                skipped("Click Yes in the pop up", "Click Yes in the confirmation pop up", "The form is submitted and a success pop up opens", "Submit did not raise the confirmation");
                skipped("Verify the success message in the pop up", "Read the message of the pop up", "The pop up says \"Form Submitted Successfully!\"", "Submit did not raise the confirmation");
                skipped("Click OK", "Click OK in the success pop up", "The pop up closes and the submitted form is shown", "Submit did not raise the confirmation");
            }

            // Submit once more, as asked. After a successful submission the app shows the form read-only (Print / Back only).
            if (okOk) {
                boolean offered = dd.formSubmitOffered(ft);
                if (offered) {
                    String c2 = dd.clickFormSubmit(ft);
                    step(dd.shot(ft), "Click Submit again", "Click Submit on the form again", "Submit responds",
                            c2.startsWith("(") ? "Submit was clicked - " + c2 : "Submit was clicked; the page answered: " + c2, c2.startsWith("(") ? "FAIL" : "PASS");
                } else {
                    step(dd.shot(ft), "Click Submit again", "Click Submit on the form again", "Submit is clicked",
                            "Not possible - after the submission the form (" + urlAfter + ") is read-only and offers only: " + dd.formTabButtons(ft)
                                    + ". There is no Submit button to click.", "MANUAL");
                }
            } else {
                skipped("Click Submit again", "Click Submit on the form again", "Submit is clicked", "the form was not submitted");
            }
        } else {
            for (String[] s : new String[][]{
                    {"Click Submit", "Click Submit on the form", "A confirmation pop up asks \"Are you sure ...?\" with No / Yes"},
                    {"Click Yes in the pop up", "Click Yes in the confirmation pop up", "The form is submitted and a success pop up opens"},
                    {"Verify the success message in the pop up", "Read the message of the pop up", "The pop up says \"Form Submitted Successfully!\""},
                    {"Click OK", "Click OK in the success pop up", "The pop up closes and the submitted form is shown"},
                    {"Click Submit again", "Click Submit on the form again", "Submit is clicked"}}) {
                skipped(s[0], s[1], s[2], "the form did not open in the next tab");
            }
        }

        // 17) back to the Doctor Dashboard
        if (ft != null) { try { ft.close(); } catch (Exception ignore) { } }
        page.bringToFront();
        boolean popupGone = !forms || dd.closeFormsPopup();
        boolean back = popupGone && dd.onScreen();
        dd.scrollTop();
        step(page, "Go back to the Doctor Dashboard", "Close the form tab and the Forms Details pop up",
                "The Doctor Dashboard OP List is shown again", back ? "Back on " + dd.currentScreen() : "Not back on the dashboard (" + dd.describeScreen() + ")",
                back ? "PASS" : "FAIL");
        if (!back) { addSummary("Result", "FAILED - could not return to the dashboard"); return; }

        // 18) select the patient again
        String re = dd.ensureSelected();
        boolean reOk = !re.startsWith("(");
        step(page, "Select the patient", "Select the patient's row in the table", "The patient is selected",
                reOk ? (re.isEmpty() ? "Still selected: " + patient : re) : "Could not select the patient - " + re, reOk ? "PASS" : "FAIL");

        // 19) View Forms
        boolean vf = reOk && dd.clickViewForms();
        String[] vff = vf ? dd.viewFormsFacts() : new String[]{"", "", ""};
        boolean vfOk = vf && "View Consent/Forms".equals(vff[0]);
        boolean listed = vfOk && vff[2].toLowerCase().contains(formName.toLowerCase()) && vff[2].contains(dd.selectedMrn);
        step(page, "Click View Forms and verify the View Consent/Forms pop up",
                "Click View Forms", "The View Consent/Forms pop up opens (Search & Filter, Consent/Forms List)",
                vfOk ? "The View Consent/Forms pop up opened: " + (vff[1].isEmpty() ? "no record count shown" : vff[1])
                        + (tabOk && okOk ? (listed ? "; it lists " + formName + " for MRN " + dd.selectedMrn : "; it does NOT list " + formName + " for MRN " + dd.selectedMrn) : "")
                     : "The View Consent/Forms pop up did not open (" + dd.describeScreen() + ")",
                vfOk ? "PASS" : "FAIL");

        // 20) Close
        if (vfOk) {
            boolean closed = dd.closeViewForms();
            step(page, "Click Close", "Click Close in the View Consent/Forms pop up", "The pop up closes",
                    closed ? "The pop up closed" : "The pop up is still open", closed ? "PASS" : "FAIL");
        } else {
            skipped("Click Close", "Click Close in the View Consent/Forms pop up", "The pop up closes", "the pop up did not open");
        }

        // 22..32) Referred Patients: Book Appointment / Registration / Admission
        String referred = referredPatientsFlow(dd);

        addSummary("Patient", patient);
        addSummary("Date range", dd.lastFrom + " - " + dd.lastTo + (dd.lastTotalText.isEmpty() ? "" : " (" + dd.lastTotalText + ")"));
        addSummary("New Case", changeOk ? change + (toastOk ? "  ->  " + toast : "  ->  " + (toast.isEmpty() ? "not saved" : toast)) : "not completed");
        addSummary("Form", pickOk ? pick : "not selected");
        addSummary("Form submitted", okOk ? msg + "  (" + urlAfter + ")" : "not submitted");
        addSummary("View Forms", vfOk ? vff[1] : "not opened");
        addSummary("Referred Patients", referred);
        if (!tabOk || !okOk || !vfOk) addDiagnostic("Screen at the end", dd.describeScreen());
    }
}
