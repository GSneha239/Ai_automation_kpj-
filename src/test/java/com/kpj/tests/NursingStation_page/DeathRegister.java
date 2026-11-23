package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.NursingStation_page.DeathRegister.Person;

// The page object is also named DeathRegister — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>DeathRegister</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>DeathRegister</b>. In the Deceased Form enter the MRN of an EXISTING patient and
 *       click <b>Search</b> — patient after patient — until one has no death register record yet.</li>
 *   <li><b>Only if no existing patient is usable</b> (every one tried already has a death register record, or has no
 *       visit) is a new patient registered in OP &gt; Registration and its MRN read from Outpatient Queue Management;
 *       that MRN is then entered in the Deceased Form.</li>
 *   <li>Burial-permit doctor: select <b>Doctor 101</b> (the IC No. / Passport fills in by itself); select the primary
 *       doctor.</li>
 *   <li>The Deceased's Information: Cause of Death and Serial Number JPN.LM02.</li>
 *   <li>Information of The Informant, then Body Claimed By: title + name, ID type + ID No., address + postcode +
 *       country/state/city, phone, occupation, relationship with the deceased.</li>
 *   <li>Mortuary Remark; click <b>Submit</b> and verify the success toast.</li>
 * </ol>
 *
 * <p><b>No MRN is hardcoded.</b> Every saved death register uses its patient up, so this is a regression test that must
 * find a patient each run:</p>
 * <ol>
 *   <li>The <b>Patient Search</b> popup beside the MRN box lists the existing patients; their MRNs are collected, page
 *       by page, up to {@code -Ddeathregister.maxtries=} patients (default 40).</li>
 *   <li>Each MRN is entered and searched, and the SERVER'S answers decide: it needs a visit (an episode) and the
 *       existing-record lookup must have answered with <b>no record</b>. The first patient that qualifies is used.</li>
 *   <li>When none does, a patient is registered ({@link com.kpj.pages.Op_page.NewPatientRegistration}) — with a unique
 *       family name, so the queue search finds exactly this patient — and its MRN is used. The MRN the queue shows is
 *       cross-checked against the one in the registration's save answer.
 *       {@code -Ddeathregister.maxtries=0} skips the existing patients and takes this path straight away.</li>
 * </ol>
 *
 * <p>A search in the Deceased Form LOADS an existing death register record into the form and Submit would then overwrite
 * it, so a record is never reused: the chosen patient is searched once more on a freshly opened screen and the run FAILS
 * rather than submit over a record.</p>
 *
 * <p><b>Order of the address fields.</b> The Country is chosen BEFORE the State and City: the State list stays empty
 * until a Country is chosen, and the City list until a State is. Body Claimed By also needs a Country (Submit
 * rejects it without one), so it is selected too.</p>
 *
 * <p>Each passing run SAVES one new death register record (and, only on the fallback path, one new patient with a visit).</p>
 */
public class DeathRegister extends DevHisBase {

    /** How many EXISTING patients are tried before registering a new one; {@code -Ddeathregister.maxtries=} (0 = go straight to registering). */
    static final int MAX_TRIES = Integer.getInteger("deathregister.maxtries", 40);
    static final String BURIAL_DOCTOR = "Doctor 101";
    static final String PRIMARY_DOCTOR = "Doctor 102";
    static final String CAUSE = "Cardiorespiratory arrest secondary to septic shock";
    static final String REMARK = "Automation test - body released to the claimant after verification.";

    public DeathRegister() { super("NursingStation_DeathRegister"); }

    public static void main(String[] args) {
        DeathRegister t = new DeathRegister();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static Person informant() {
        Person p = new Person();
        p.title = "Mister"; p.name = "Auto Test Informant";
        p.idType = "MyKad / Old IC"; p.idNo = "900515105234";
        p.street = "No 12 Jalan SS 2/10"; p.postcode = "47300";
        p.country = "Malaysia"; p.state = "Selangor"; p.city = "Petaling Jaya";
        p.phone = "123456789"; p.occupationRe = "^engineer$"; p.relationship = "Son";
        return p;
    }

    private static Person claimant() {
        Person p = new Person();
        p.title = "Mrs"; p.name = "Auto Test Claimant";
        p.idType = "MyKad / Old IC"; p.idNo = "880420085678";
        p.street = "No 5 Jalan Tun Razak"; p.postcode = "50400";
        p.country = "Malaysia"; p.state = "Wilayah Persekutuan Kuala Lumpur"; p.city = "Kuala Lumpur";
        p.phone = "198765432"; p.occupationRe = "^teacher$"; p.relationship = "Wife";
        return p;
    }

    @Override
    protected void body() {
        meta("Nursing Station - DeathRegister", "Nursing Station > DeathRegister",
                "Search existing patients' MRNs in the Deceased Form until one has no death register record (only if none is usable: register a new patient and read its MRN in Outpatient Queue Management), select the burial-permit doctor (Doctor 101, IC auto-filled) and the primary "
                        + "doctor, enter the cause of death and serial number, fill the Informant and Body Claimed By blocks, enter the "
                        + "mortuary remark and Submit; verify the success toast. SAVES a death register record.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.DeathRegister dr =
                new com.kpj.pages.NursingStation_page.DeathRegister(page);

        // 1) Nursing Station > DeathRegister
        boolean on = dr.navigateViaMenu(BASE);
        step(page, "Open DeathRegister", "Click Nursing Station -> DeathRegister", "The Deceased Form is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — DeathRegister not reached"); return; }

        // 2) EXISTING patients first: enter each MRN in the Deceased Form and click Search, until one has a visit and no
        //    death register record
        java.util.List<String> pool = MAX_TRIES > 0 ? dr.findCandidateMrns(MAX_TRIES, 8) : java.util.Collections.emptyList();
        StringBuilder tried = new StringBuilder();
        String mrn = null;
        int attempts = 0;
        for (String c : pool) {
            if (attempts >= MAX_TRIES) break;
            attempts++;
            com.kpj.pages.NursingStation_page.DeathRegister.Screen sc = dr.screenMrn(c);
            tried.append(tried.length() == 0 ? "" : "; ").append(c).append(" (")
                    .append(sc.eligible() ? "eligible" : sc.reason).append(")");
            if (sc.eligible()) { mrn = c; break; }
        }
        boolean existingPatient = mrn != null;
        step(page, "Look for an existing patient without a death register",
                "Enter existing patients' MRNs (listed by Patient Search) in the Deceased Form and click Search, until one has no death register record",
                "An existing patient with a visit and no death register record is found (or none is usable)",
                existingPatient ? "Found MRN " + mrn + " after trying " + attempts + " existing patient(s): " + tried
                  : MAX_TRIES == 0 ? "Existing patients were skipped (-Ddeathregister.maxtries=0) — a new patient will be registered"
                  : pool.isEmpty() ? "Patient Search listed no existing patients — a new patient will be registered"
                  : "None of the " + attempts + " existing patient(s) tried could be used — " + tried
                      + " — so a new patient will be registered",
                "PASS");

        com.kpj.pages.Op_page.NewPatientRegistration.Result patient = null;
        boolean mrnFromQueue = false;
        if (!existingPatient) {
            // 2b) FALLBACK — register a new patient and read its MRN in Outpatient Queue Management
            com.kpj.pages.Op_page.NewPatientRegistration np = new com.kpj.pages.Op_page.NewPatientRegistration(page);
            patient = np.registerPatient(BASE);
            boolean registered = patient.saved && patient.problem.isEmpty();
            step(page, "Register a new patient (OP Registration)",
                    "OP > Registration: fill every section for a new patient with a unique name, Save and confirm",
                    "A new patient is registered with a visit",
                    registered ? "Registered " + patient.name + " — " + patient.saveToast
                                 + (patient.visitNote.isEmpty() ? "" : " (note: " + patient.visitNote + ")")
                               : "Registration failed: " + patient.problem
                                 + (patient.name.isEmpty() ? "" : " (patient " + patient.name + ")"),
                    registered ? "PASS" : "FAIL");
            if (!registered) { addSummary("Result", "FAILED — the new patient could not be registered: " + patient.problem); return; }

            np.findMrnInQueue(BASE, patient);
            mrn = !patient.mrn.isEmpty() ? patient.mrn : patient.saveMrn;   // the save answer only if the queue had nothing
            mrnFromQueue = !patient.mrn.isEmpty() && patient.mrnsAgree;
            step(page, "Get the new patient's MRN from Outpatient Queue Management",
                    "OP > Outpatient Queue Management: search today's queue for the patient just registered and read the MRN",
                    "The new patient is listed in the queue and its MRN is read",
                    mrnFromQueue ? "Found " + patient.queueRow + (patient.saveMrn.isEmpty() ? "" : "; it matches the MRN in the registration's save answer (" + patient.saveMrn + ")")
                                 : "NOT confirmed from the queue — " + patient.problem
                                   + (mrn.isEmpty() ? "" : "; continuing with MRN " + mrn + " from the " + (patient.mrn.isEmpty() ? "save answer" : "queue")),
                    mrnFromQueue ? "PASS" : "FAIL");
            if (mrn.isEmpty() || (!patient.mrn.isEmpty() && !patient.mrnsAgree)) {
                addSummary("Result", "FAILED — the new patient's MRN could not be established: " + patient.problem);
                return;
            }
            // back to DeathRegister (a fresh screen: it was left for OP Registration)
            on = dr.navigateViaMenu(BASE);
            step(page, "Open DeathRegister", "Click Nursing Station -> DeathRegister", "The Deceased Form is shown",
                    on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
            if (!on) { addSummary("Result", "FAILED — DeathRegister not reached"); return; }
        } else if (!dr.reopenScreen()) {
            // the screening may have loaded other patients' records into the form: the chosen one needs a blank screen
            step(page, "Reopen DeathRegister blank", "Open the screen afresh before entering the chosen MRN",
                    "A blank Deceased Form", "The screen would not reopen blank (it came back with earlier form data)", "FAIL");
            addSummary("Result", "FAILED — the DeathRegister screen would not reopen blank");
            return;
        }

        // 3) enter the MRN and click Search (on a blank screen)
        String found = dr.searchMrn(mrn);
        boolean searched = !dr.lastEpisode.isEmpty() && !dr.existingRecord;
        step(page, "Enter the MRN and click Search", "Enter MRN " + mrn + " in the Deceased Form and click Search",
                "The patient's episode is filled in and there is no death register record yet",
                searched ? found : "The patient cannot be used: " + found,
                searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — MRN " + mrn + " could not be used in DeathRegister: " + found); return; }

        // 3) burial-permit doctor
        dr.scrollTo("Issuing Burial Permits");
        String burial = dr.selectBurialDoctor(BURIAL_DOCTOR);
        step(page, "Select the burial-permit doctor", "Select " + BURIAL_DOCTOR + " in Information of Doctor's who Issuing Burial Permits",
                "The doctor is selected and the IC No. / Passport fills in by itself", burial + (dr.ok() ? "" : " | " + dr.problems),
                dr.ok() ? "PASS" : "FAIL");

        // 4) primary doctor
        dr.scrollTo("Primary Doctor");
        String primary = dr.selectPrimaryDoctor(PRIMARY_DOCTOR);
        step(page, "Select the primary doctor", "Select a doctor in Primary Doctor's Information",
                "The doctor is selected", primary + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        // 5) the deceased's information
        dr.scrollTo("The Deceased's Information");
        String cause = dr.fillCauseOfDeath(CAUSE);
        step(page, "Enter cause of death", "Enter the Cause of Death", "The cause of death is entered",
                cause + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");
        String serial = String.valueOf(System.currentTimeMillis() % 1000000);
        String serialDone = dr.fillSerialNumber(serial);
        step(page, "Enter Serial Number JPN.LM02", "Enter the Serial Number JPN.LM02", "The serial number is entered",
                serialDone + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        // 6) informant, 7) body claimed by — same four groups each
        person(dr, "informant", "Information of The Informant", "Informant", informant());
        person(dr, "claim", "Body Claimed By", "Body claimed by", claimant());

        // 8) mortuary remark
        String remark = dr.fillMortuaryRemark(REMARK);
        dr.scrollTo("Body Claimed By");
        step(page, "Enter mortuary remark", "Enter the Mortuary Remark", "The mortuary remark is entered",
                remark + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        // 9) Submit + success toast
        String toast = dr.clickSubmitAndGetToast();
        boolean ok = dr.submitSucceeded();
        step(page, "Click Submit", "Click Submit (IUDSaveDeathRegister)", "The death register is saved",
                dr.lastSubmit + (toast.isEmpty() ? "; no toast appeared" : "; the screen answered \"" + toast + "\""),
                toast.isEmpty() ? "FAIL" : "PASS");
        step(page, "Verify success toast", "Read the toast shown after Submit", "A success toast appears",
                ok ? "PASSES because the toast is a success message: \"" + toast + "\""
                   : (toast.isEmpty() ? "FAILS because no toast appeared"
                                      : "FAILS because the toast is not a success message: \"" + toast + "\""),
                ok ? "PASS" : "FAIL");

        addSummary("MRN used", mrn);
        addSummary("Patient source", existingPatient
                ? "Existing patient (Patient Search; tried " + attempts + ": " + tried + ")"
                : "NEW patient registered in OP Registration — " + patient.name + (patient.visitId.isEmpty() ? "" : " (visit " + patient.visitId + ")")
                  + "; MRN read from " + (mrnFromQueue ? "Outpatient Queue Management" : "the save answer (NOT confirmed from the queue)")
                  + (MAX_TRIES == 0 ? "" : "; none of the " + attempts + " existing patient(s) tried was usable: " + tried));
        addSummary("Patient", dr.lastPatient);
        addSummary("Episode", dr.lastEpisode);
        addSummary("Burial-permit doctor", burial);
        addSummary("Primary doctor", primary);
        addSummary("Serial Number JPN.LM02", serial);
        addSummary("Result", ok ? toast : (toast.isEmpty() ? "Not confirmed" : toast));
    }

    /** One person block as four steps: title + name; ID type + No.; address + postcode + country/state/city; phone/occupation/relationship. */
    private void person(com.kpj.pages.NursingStation_page.DeathRegister dr, String p, String headingRe, String label, Person d) {
        dr.scrollTo(headingRe);
        String a = dr.fillTitleName(p, d);
        step(page, label + ": title and name", "Select the title and enter the name", "The title and name are entered",
                a + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        String b = dr.fillIdentity(p, d);
        step(page, label + ": IC type and IC No.", "Select the IC type and enter the IC No.", "The IC type and number are entered",
                b + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        String c = dr.fillAddress(p, d);
        step(page, label + ": address, postcode, country, state, city",
                "Enter the address and postcode; select the country, state and city/district",
                "The address block is filled (the Country is chosen first: State and City stay empty until it is)",
                c + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");

        dr.scrollTo(headingRe);
        String e = dr.fillContact(p, d);
        step(page, label + ": phone, occupation, relationship",
                "Enter the phone number; select the occupation and the relationship with the deceased",
                "The contact block is filled", e + (dr.ok() ? "" : " | " + dr.problems), dr.ok() ? "PASS" : "FAIL");
    }
}
