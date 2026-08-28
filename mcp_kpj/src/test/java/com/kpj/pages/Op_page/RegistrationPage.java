package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

/**
 * RegistrationPage — Page Object for OP &gt; Registration (VisitScreen, #/VisitScreen).
 *
 * <p>Encapsulates the whole DevHIS registration flow: filling every section (Patient, Correspondence,
 * Other, Payor, Visit, Next of Kin), the Photo/Thumb/IC-Card attachments, Save + confirm, the in-app
 * "Consent Details" (PERSONAL DATA NOTICE &amp; CONSENT) modal, and capturing the auto-opened
 * artifacts (Registration Report label + Patient Registration Form consent).</p>
 *
 * <p>Notable quirks handled here (see {@code devhis-registration} memory): NationalityID must be set
 * before ICCardTypeID or the NRIC field disables; Queue No lives on the Save button's scope; several
 * fields intermittently fail to commit and silently block Save with a "Please Enter ..." toast, so
 * critical fields are re-asserted before Save and the Kin row is re-filled on retry.</p>
 */
public class RegistrationPage extends BasePage {

    public RegistrationPage(Page page) {
        super(page);
    }

    /**
     * Snapshot of every field value set during the fill (keyed by ng-model, e.g. "Registration.PrefixID" ->
     * numeric model value; "Registration.KinName" -> text). At Save these are re-applied as PLAIN properties
     * on the Registration/Visit objects (no DOM change events) so no watcher fires and no field-reset cascade
     * knocks another field empty — the fix for the interdependent "Please Enter X" chain on this screen.
     */
    private final java.util.Map<String, Object> snap = new java.util.LinkedHashMap<>();

    /** Record a field's model value in the snapshot (only when non-null/non-empty, so a good value is kept). */
    private void snap(String ngModel, Object value) {
        if (value != null && !value.toString().isEmpty()) snap.put(ngModel, value);
    }

    // ---- randomised patient data -----------------------------------------

    /** A randomised patient (a different patient each run) used to fill the whole form. */
    public static class PatientProfile {
        public String gender, prefix, fullName, familyName, marital, race, religion, blood, income;
        public String dob, nric, tin;
        public String address, postcode, houseNo, street, mobile, phone, email;
        public String kinTitle, kinName, kinFamily, kinRel, kinNric, kinMobile, kinEmail;
        /** Null/empty = Malaysian (unchanged default behaviour). Set by {@link #randomForeign(String)} to drive
         *  {@link RegistrationPage#fillPatientInformation} down the non-Malaysian path — see that method's
         *  comment on why the Passport No. box IS {@code Registration.FamilyName}, not a separate field. */
        public String nationality, icCardType, passportExpiry;

        /** Build a random, internally-consistent patient (NRIC prefix matches DOB; kin uses valid options). */
        public static PatientProfile random() {
            Random rnd = new Random();
            PatientProfile p = new PatientProfile();
            boolean male = rnd.nextBoolean();
            p.gender = male ? "Male" : "Female";
            String[] maleNames   = {"John Peter", "David Smith", "Michael Brown", "James Wilson", "Robert Test", "Peter Johnson"};
            String[] femaleNames = {"Mary Jane", "Sarah Test", "Emma Watson", "Linda Carter", "Anna Peters", "Test User"};
            p.fullName = (male ? maleNames : femaleNames)[rnd.nextInt(6)];
            p.familyName = p.fullName.substring(p.fullName.lastIndexOf(' ') + 1);
            p.marital = new String[]{"Single", "Married", "Divorced"}[rnd.nextInt(3)];
            p.prefix = male ? "Mr." : (p.marital.equals("Married") ? "Mrs." : "Ms.");
            p.race = new String[]{"Malay", "Chinese", "Indian", "Others"}[rnd.nextInt(4)];
            p.religion = new String[]{"Islam", "Buddhism", "Hinduism", "Christianity"}[rnd.nextInt(4)];
            p.blood = new String[]{"A", "B", "O", "AB"}[rnd.nextInt(4)];
            p.income = new String[]{"No Income", "< RM 500", "RM 500 - RM 1000"}[rnd.nextInt(3)];
            int byear = 1965 + rnd.nextInt(40), bmonth = 1 + rnd.nextInt(12), bday = 1 + rnd.nextInt(28);
            p.dob = String.format("%02d/%02d/%04d", bday, bmonth, byear);
            String[] placeCodes = {"10", "14", "08", "12", "01", "07"};
            // NRIC: YYMMDD (must match DOB) + valid place code + unique 4-digit serial.
            p.nric = String.format("%02d%02d%02d", byear % 100, bmonth, bday)
                    + placeCodes[rnd.nextInt(placeCodes.length)] + String.format("%04d", Math.abs(System.nanoTime() % 10000));
            p.tin = "IG" + (rnd.nextInt(90000000) + 10000000);
            String[][] addresses = {{"15 Jalan Damai", "50000"}, {"22 Jalan Ampang", "50450"}, {"8 Persiaran Surian", "50480"},
                    {"30 Jalan Bukit Bintang", "50000"}, {"5 Lorong Mawar", "50450"}, {"12 Jalan Tun Razak", "50400"}};
            String[] a = addresses[rnd.nextInt(addresses.length)];
            p.address = a[0]; p.postcode = a[1];
            p.houseNo = p.address.split(" ")[0];
            p.street = p.address.substring(p.address.indexOf(' ') + 1);
            // MUST be at least 10 digits — the app rejects Save with "Mobile number must be at least 10 digits!".
            // ("1" + 8 digits = 9, which is what used to block every Save.)
            p.mobile = "1" + String.format("%09d", rnd.nextInt(900000000) + 100000000);
            p.phone = "03" + (rnd.nextInt(9000000) + 1000000);
            p.email = p.familyName.toLowerCase() + (rnd.nextInt(9000) + 1000) + "@example.com";
            // Next of Kin — Relationship must be an EXACT option; kin gender is independent.
            p.kinRel = new String[]{"Father", "Grandfather", "Biological Child", "Adopted Child"}[rnd.nextInt(4)];
            boolean kinMale = rnd.nextBoolean();
            p.kinTitle = kinMale ? "Mr." : "Mrs.";
            String[] kinMaleNames   = {"Tom Baker", "Chris Evans", "Paul Test", "George King"};
            String[] kinFemaleNames = {"Jane Doe", "Lucy Test", "Grace Hall", "Rose Adams"};
            p.kinName = (kinMale ? kinMaleNames : kinFemaleNames)[rnd.nextInt(4)];
            p.kinFamily = p.kinName.substring(p.kinName.lastIndexOf(' ') + 1);
            // The LAST digit of a Malaysian NRIC encodes gender: odd = male, even = female. Leaving it random
            // contradicted the kin's own Title (a "Mrs." with an odd serial), and AddKinDetails rejected roughly
            // every other run with "Please enter valid NRIC for NOK" — which read as a flaky test rather than
            // inconsistent data.
            int kinSerial = (int) (Math.abs(System.nanoTime() % 9999) + 1);
            if ((kinSerial % 2 == 1) != kinMale) kinSerial = kinSerial % 2 == 0 ? kinSerial + 1 : kinSerial - 1;
            if (kinSerial < 1) kinSerial = kinMale ? 1 : 2;
            p.kinNric = String.format("%02d%02d%02d", (1960 + rnd.nextInt(30)) % 100, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28))
                    + placeCodes[rnd.nextInt(placeCodes.length)] + String.format("%04d", kinSerial);
            // 10 digits, same rule as the patient mobile ("1" + 8 digits = 9 is rejected).
            p.kinMobile = "1" + String.format("%09d", rnd.nextInt(900000000) + 100000000);
            p.kinEmail = p.kinFamily.toLowerCase() + (rnd.nextInt(9000) + 1000) + "@example.com";
            return p;
        }

        /**
         * A random patient of the given foreign {@code nationality} (e.g. "Indonesian") — everything else
         * randomised the same way as {@link #random()}, EXCEPT: {@code icCardType} is "Passport" instead of "New
         * IC", and {@code nric}/{@code familyName} are left as generated but UNUSED by
         * {@link RegistrationPage#fillPatientInformation} for a non-Malaysian — that method fills the Passport
         * No. box (really {@code Registration.FamilyName}) with a freshly minted passport number instead, and the
         * full name goes into First Name only (this screen has no separate family-name field once nationality
         * flips the Passport No. box on).
         */
        public static PatientProfile randomForeign(String nationality) {
            PatientProfile p = random();
            p.nationality = nationality;
            p.icCardType = "Passport";
            java.time.LocalDate expiry = java.time.LocalDate.now().plusYears(1 + new Random().nextInt(9));
            p.passportExpiry = expiry.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            return p;
        }

        public String patientLine() {
            String nat = (nationality == null || nationality.isEmpty()) ? "Malaysian" : nationality;
            String idLabel = (nationality == null || nationality.isEmpty()) ? "New IC " + nric : "Passport (assigned at fill time)";
            return prefix + " " + fullName + " | " + gender + " | " + dob + " | " + idLabel
                    + " | " + nat + " | " + religion + " | " + race + " | " + marital + " | Blood " + blood;
        }
        public String correspondenceLine() { return address + " | Postcode " + postcode + " | Mobile " + mobile + " | " + email; }
        public String kinLine() { return kinTitle + " " + kinName + " (" + kinRel + ") | Malaysian | NRIC " + kinNric + " | Mobile " + kinMobile; }
    }

    /** Outcome of the Save step. */
    public static class SaveOutcome {
        public boolean saved;
        public String toast;
    }

    // ---- navigation -------------------------------------------------------

    /**
     * Open OP &gt; Registration via the menu (falls back to the direct hash) and wait for the dropdown
     * master-data to finish loading. Returns true once the master-data has populated.
     */
    public boolean open(String baseUrl) {
        // Guard the menu/link click evaluates: clicking a nav link makes the SPA route-change, and under the
        // flaky server that can trigger a full navigation (e.g. a session re-auth) that destroys the JS context
        // mid-evaluate ("Execution context was destroyed"). Swallow that — the #txtFirstName wait below (or the
        // direct-navigate fallback) is the real readiness gate.
        try { page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP'); if(op) op.click(); }"); }
        catch (Exception ignore) { System.out.println("open: OP menu click raced a navigation - continuing"); }
        waitForAngular(1200);
        try { page.evaluate("() => { const a=[...document.querySelectorAll(\"a[href='#/VisitScreen']\")][0]; if(a) a.click(); }"); }
        catch (Exception ignore) { System.out.println("open: VisitScreen link click raced a navigation - continuing"); }
        waitForAngular(1500);
        try {
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(8000));
        } catch (Exception e) {
            // Use in-app hash routing instead of page.navigate() to avoid SPA reload hangs
            page.evaluate("() => { window.location.hash = '#/VisitScreen'; }");
            page.waitForSelector("#txtFirstName", new Page.WaitForSelectorOptions().setTimeout(40000));
        }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) {}
        waitForAngular(2500);
        boolean masterLoaded = false;
        for (int i = 0; i < 4 && !masterLoaded; i++) {
            try {
                page.waitForFunction(
                        "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.GenderID'); return e && e.options.length>1; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                masterLoaded = true;
            } catch (com.microsoft.playwright.PlaywrightException ex) {
                waitForAngular(1500);
            }
        }
        return masterLoaded;
    }

    /**
     * Open a screen that REUSES this exact registration form (identical {@code Registration.*}/{@code Visit.*}
     * ng-models, same {@code #txtFirstName}/attachments/{@code IUDRegistration()} save) at the given hash route —
     * e.g. {@code "#/EmergencyRegistrationConsious"} for Emergency Registration (Conscious). Hash-routes in-app
     * (no full reload), waits for the form (Save button + First Name), then the master-data. Returns true if the
     * master-data (Gender options) loaded.
     */
    public boolean openRoute(String hashRoute) {
        // Wait for the SPA to boot (Angular + the left menu) before routing — right after login the AngularJS
        // router may not be initialised yet, so an immediate hash change is ignored and the form never loads.
        try { page.waitForFunction("() => window.angular && [...document.querySelectorAll('li > a')].length > 3",
                null, new Page.WaitForFunctionOptions().setTimeout(30000)); }
        catch (Exception ignore) { }
        waitForAngular(1000);
        // Route with retries: bounce via #/PatientDashboard first so the hashchange to the target route always
        // fires (even if the hash was already set), then wait for the form (Save button + First Name).
        boolean formUp = false;
        for (int attempt = 0; attempt < 3 && !formUp; attempt++) {
            try { page.evaluate("() => { window.location.hash = '#/PatientDashboard'; }"); } catch (Exception ignore) { }
            waitForAngular(700);
            try { page.evaluate("(h) => { window.location.hash = h; }", hashRoute); } catch (Exception ignore) { }
            try {
                page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDRegistration();']\") && document.getElementById('txtFirstName')",
                        null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 20000 : 15000));
                formUp = true;
            } catch (Exception e) { System.out.println("openRoute: form not ready for " + hashRoute + " (attempt " + (attempt + 1) + ") — re-routing"); waitForAngular(1500); }
        }
        try { page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) { }
        waitForAngular(2500);
        boolean masterLoaded = false;
        for (int i = 0; i < 4 && !masterLoaded; i++) {
            try {
                page.waitForFunction(
                        "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.GenderID'); return e && e.options.length>1; }",
                        null, new Page.WaitForFunctionOptions().setTimeout(8000));
                masterLoaded = true;
            } catch (com.microsoft.playwright.PlaywrightException ex) {
                waitForAngular(1500);
            }
        }
        return masterLoaded;
    }

    // ---- section fills ----------------------------------------------------

    /** Patient Information — Nationality BEFORE ICCardType so the NRIC field ends up enabled. */
    public void fillPatientInformation(PatientProfile p) {
        String nationality = (p.nationality == null || p.nationality.isEmpty()) ? "Malaysian" : p.nationality;
        // setSel matches option text EXACTLY, but the dropdown spells the adjectival form as a noun on some
        // environments (DSH: "Australia", not "Australian" — same gap already fixed on the kin side via
        // setSelLike). An exact-match miss here silently leaves Registration.NationalityID on "--Select--": the
        // rest of the fill still worked because patientIsMalaysian() reads that empty state as "not Malaysian" and
        // happens to route correctly into the passport path, but the PATIENT's own Nationality stayed genuinely
        // empty all the way to Save, which then rejected with the nameless "Please fill in all the mandatory
        // fields!". Use the same fuzzy matcher the kin fields already use.
        String natSet = ensureNationality(nationality);
        System.out.println("fillPatientInformation: Nationality = " + (natSet.isEmpty() ? "(NOT set)" : natSet));
        if (!setPrefixRobust(p.prefix))
            System.out.println("fillPatientInformation: Patient Title not committed after retries — options may differ on this env");
        setSel("Registration.GenderID", p.gender);
        setSel("Registration.RaceID", p.race);
        setSel("Registration.ReligionID", p.religion);
        setSel("Registration.MaritalStatusID", p.marital);
        setSel("Registration.BloodGroupID", p.blood);
        setSel("Registration.IncomeCategoryID", p.income);
        // Identification Type (ICCardType): wait for the dropdown to load, select "New IC" (or, for a
        // non-Malaysian, "Passport" — see PatientProfile.randomForeign), and VERIFY it stuck. A slow-loading
        // dropdown makes setSel silently no-op — leaving Identification Type empty, which in turn keeps the
        // Identification No (NationalId) field disabled/empty. Retry until the model is actually set.
        String icCardType = (p.icCardType == null || p.icCardType.isEmpty()) ? "New IC" : p.icCardType;
        try {
            page.waitForFunction("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); return e && e.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillPatientInformation: ICCardType dropdown did not populate in time"); }
        for (int a = 0; a < 4; a++) {
            setSel("Registration.ICCardTypeID", icCardType);
            waitForAngular(300);
            boolean idTypeSet = Boolean.TRUE.equals(page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); const c=e?angular.element(e).controller('ngModel'):null; return c && c.$modelValue!=null && c.$modelValue!==''; }"));
            if (idTypeSet) break;
            System.out.println("fillPatientInformation: Identification Type not set yet — retrying (attempt " + (a + 1) + ")");
            setSelTxt("Registration.ICCardTypeID", icCardType);  // fallback: selectedIndex-based (picks the wanted text or first real)
            waitForAngular(300);
        }
        page.locator("#txtFirstName").fill(p.fullName);
        // Unlike NRIC/DOB below, this was never snap()'d — so when the Nationality re-assert further down (or
        // Save's own retry loop) fires the ng-change reset cascade, Registration.FirstName gets wiped with no
        // safety net to restore it from, and Save keeps failing on "Name*=''" no matter how many retries run.
        snap("Registration.FirstName", p.fullName);
        // NOTE: "Registration.FamilyName" is the PASSPORT NO. control on this form, not a surname — the sibling
        // screens say so outright (OutPatientQueueManagementPage: "Passport='+setTxt('Registration.FamilyName',…)",
        // AppointmentListPage: "setIf('Registration.FamilyName','A1234567'); // Passport No"), and the NOK grid's
        // "passport no." column duly showed the surname we had been writing here. Feeding it p.familyName therefore
        // (a) stored a surname as a passport number and (b) drew from a six-name pool, so Save kept failing with
        // "Passport No already Exist!!" once those names had been used. Use a unique passport-shaped value instead.
        // A MALAYSIAN is identified by the NRIC alone — fill the NRIC, and leave Passport No. EMPTY (cleared, not
        // just skipped, so nothing carried over from an earlier pass survives). Only a non-Malaysian gets a
        // passport, and even then only where the app actually allows one to be entered: for a local the control is
        // disabled (KLG reports fillable:false) and fillModel writes through $setViewValue, which ignores
        // `disabled`, so filling unconditionally stored a passport no real user could have typed.
        if (patientIsMalaysian()) {
            clearModel("Registration.FamilyName");
            System.out.println("fillPatientInformation: Nationality is Malaysian — NRIC only, Passport No. cleared");
        } else if (passportEditable()) {
            fillModel("Registration.FamilyName", uniquePassport("A"));
            // Passport Expiry Date (Registration.PassportExpirydate) sits alongside the Passport No. box and is
            // marked mandatory once nationality is non-Malaysian — a future date, same dd/MM/yyyy shape as DOB.
            String expiry = (p.passportExpiry == null || p.passportExpiry.isEmpty())
                    ? java.time.LocalDate.now().plusYears(5).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : p.passportExpiry;
            fillModel("Registration.PassportExpirydate", expiry);
        } else {
            System.out.println("fillPatientInformation: Passport No. is not editable for this nationality — left empty");
        }
        waitForAngular(400);
        // Identification No (NRIC) via the Angular model (the field can be transiently guarded/disabled). Verify it
        // committed and retry — if the Identification Type wasn't set, this field stays empty and blocks Save.
        for (int a = 0; a < 4; a++) {
            page.evaluate("(v)=>{const e=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12') || [...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")][0];"
                    + "if(e){const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}"
                    + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}}", p.nric);
            waitForAngular(300);
            boolean nricSet = Boolean.TRUE.equals(page.evaluate("(v) => { const e=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.value===v); return !!e; }", p.nric));
            if (nricSet) break;
            System.out.println("fillPatientInformation: Identification No not committed yet — retrying (attempt " + (a + 1) + ")");
        }
        snap("Registration.NationalId", p.nric);
        page.locator("#txtDateOfBirth").fill(p.dob);
        snap("Registration.DateOfBirth", p.dob);
        fillModel("Registration.TINNumber", p.tin);
        // Re-assert LAST. On a slow environment (KS) the 256-entry Nationality master arrives after the fill has
        // moved on, Angular re-renders the select, and the selection drops back to the app's default — "Myanmar"
        // there. Everything downstream then judged a Malaysian patient as a foreigner (the Passport No. check) and
        // the saved record carried the wrong nationality. Setting it once at the top is not enough.
        // CHECK first, and only re-select on a REAL drift. Re-selecting unconditionally fires the nationality
        // ng-change after the identity is in, and on this form that runs the patient lookup which clears the
        // section that was just filled — the exact failure this caused on IP Admission. Environments that do not
        // drift (devhis, DSH) therefore see no extra interaction at all.
        String natNow = selectedText("Registration.NationalityID");
        String wantN = nationality.toLowerCase().replaceAll("[^a-z]", "");
        String gotN = natNow.toLowerCase().replaceAll("[^a-z]", "");
        boolean natOk = !gotN.isEmpty()
                && (gotN.startsWith(wantN.substring(0, Math.min(5, wantN.length()))) || wantN.startsWith(gotN));
        if (!natOk) {
            System.out.println("fillPatientInformation: Nationality drifted to \"" + natNow + "\" (wanted "
                    + nationality + ") — re-asserting");
            ensureNationality(nationality);
        }
    }

    /**
     * Select a Nationality and PROVE it stuck, retrying while the master data settles.
     *
     * <p>Waits for the list to actually hold options (an empty select makes {@code setSelLike} a silent no-op),
     * selects, then reads the label back; a late-arriving master reverts the selection to the environment default,
     * so it retries rather than trusting the first write.</p>
     *
     * @return the label finally selected, "" if none stuck
     */
    private String ensureNationality(String wanted) {
        return ensureSelectVerified("Registration.NationalityID", wanted);
    }

    /**
     * Select a value on an async-loaded dropdown and PROVE it stuck.
     *
     * <p>Waits for the option list to stop CHANGING first — not merely to be non-empty. On a slow environment (KS)
     * the 256-entry Nationality master is delivered in stages: a write that lands mid-load is discarded when
     * Angular re-renders against the new option set, and the select silently falls back to the environment default
     * ("Myanmar") or to "--Select--". Then selects, reads the label back, and retries. Accepts either spelling the
     * environments use for the same value ("Malaysian" here, "Malaysia" there).</p>
     *
     * @return the label finally selected, "" if none stuck
     */
    private String ensureSelectVerified(String model, String wanted) {
        try {
            page.waitForFunction("(ng) => { const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                            + " if(!e || e.options.length<2) return false;"
                            + " const n=e.options.length; if(window.__optLen && window.__optLen[ng]===n) return true;"
                            + " window.__optLen=window.__optLen||{}; window.__optLen[ng]=n; return false; }",
                    model, new Page.WaitForFunctionOptions().setTimeout(25000).setPollingInterval(1500));
        } catch (Exception ignore) { System.out.println("ensureSelectVerified: " + model + " list never settled — proceeding"); }
        String got = "";
        for (int a = 0; a < 5; a++) {
            setSelLike(model, wanted);
            waitForAngular(600);
            got = selectedText(model);
            String w = wanted.toLowerCase().replaceAll("[^a-z]", "");
            String g = got.toLowerCase().replaceAll("[^a-z]", "");
            if (!g.isEmpty() && (g.startsWith(w) || w.startsWith(g))) return got;
            System.out.println("ensureSelectVerified: " + model + " wanted \"" + wanted + "\" but reads \""
                    + got + "\" — retrying (attempt " + (a + 1) + ")");
            page.waitForTimeout(1500);
        }
        return got;
    }

    /**
     * Validation check — with Nationality = <b>Malaysian</b>, the <b>Passport No.</b> field must NOT be enabled
     * (a Malaysian registers with an NRIC, not a passport). This inspects the current form state: the Nationality
     * display value, whether a Passport No. field is visible, and whether the screen is asking for it — by the
     * DOM {@code required} property (Angular sets it when {@code ng-required} evaluates true), the
     * {@code ng-invalid-required} class (the red border), or a {@code *} in its label. Returns a compact JSON-ish
     * string: {@code {"nat":"Malaysia","passportVisible":true,"labStar":true,"domRequired":true,"invalidReq":true,"enabled":true}}.
     */
    /**
     * NOK/Guarantor counterpart of {@link #checkPassportRequiredForLocal()} — with the kin's Nationality =
     * <b>Malaysian</b>, the kin is identified by the <b>NRIC</b> and the <b>Passport No.</b> must carry no value.
     *
     * <p>The kin Passport No. control binds to {@code Registration.KinFamilyName} (it is not a surname, despite
     * the name), so this reports that field's value, the NRIC beside it, and the kin nationality on screen.
     * Returns e.g. {@code {"kinNat":"Malaysian","passportPresent":true,"passportValue":"","nric":"760111015733"}}.</p>
     */
    /** The kin form's nationality / NRIC / Passport No. as they stand BEFORE Add — captured by {@link #addNextOfKin}. */
    public String lastKinPassportPreAdd = "";

    /** The kin FORM's own state (nationality, NRIC, Passport No.) — only meaningful before the Add clears it. */
    public String kinFormPassportState() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const box=ng=>[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + " const val=ng=>{ const e=box(ng); return e ? (e.value||'').trim() : ''; };"
                + " let kinNat=''; const ns=[...document.querySelectorAll('select')].find(s=>(s.getAttribute('ng-model')||'')==='Registration.NOKnationalid');"
                + " if(ns && ns.selectedIndex>=0){ const o=ns.options[ns.selectedIndex]; kinNat=norm(o?o.textContent:''); }"
                + " const p=box('Registration.KinFamilyName');"
                + " return JSON.stringify({source:'kin form (before Add)', kinNat, passportPresent:!!p,"
                + "   passportVisible:!!(p && p.offsetParent!==null), passportValue:val('Registration.KinFamilyName'),"
                + "   nric:val('Registration.KinNationalId')}); }");
        return r == null ? "{}" : r.toString();
    }

    public String checkKinPassportForLocal() {
        // Read the COMMITTED grid row, not the form: a successful Add clears the kin form, so a form-based probe
        // run afterwards reports "--Select--" with everything blank and says nothing about what was stored.
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const t=[...document.querySelectorAll('table')].find(x=>/relation|guarantor|nationality/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(t){"
                + "   const heads=[...t.querySelectorAll('thead th')].map(h=>norm(h.textContent));"
                + "   const rows=[...t.querySelectorAll('tbody tr')].filter(x=>{ const tx=norm(x.textContent); return tx && !/no records|no data/i.test(tx); });"
                + "   if(rows.length){"
                + "     const cells=[...rows[rows.length-1].querySelectorAll('td')].map(c=>norm(c.textContent));"
                + "     const at=re=>{ const i=heads.findIndex(h=>re.test(h)); return (i>=0 && i<cells.length) ? cells[i] : ''; };"
                + "     return JSON.stringify({source:'grid row', kinNat:at(/^nationality$/i),"
                + "       passportValue:at(/passport/i), nric:at(/nric/i)}); } }"
                // No committed row yet — fall back to the form (valid before Add).
                + " const val=ng=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + "   return e ? (e.value||'').trim() : ''; };"
                + " let kinNat=''; const ns=[...document.querySelectorAll('select')].find(s=>(s.getAttribute('ng-model')||'')==='Registration.NOKnationalid');"
                + " if(ns && ns.selectedIndex>=0){ const o=ns.options[ns.selectedIndex]; kinNat=norm(o?o.textContent:''); }"
                + " return JSON.stringify({source:'kin form', kinNat, passportValue:val('Registration.KinFamilyName'),"
                + "   nric:val('Registration.KinNationalId')}); }");
        return r == null ? "{}" : r.toString();
    }

    /**
     * True when the kin on file is Malaysian and carries NO passport value (NRIC only) — the wanted state.
     * The NRIC must be present too: an all-blank read (e.g. probing the form after Add cleared it) proves
     * nothing and must not be reported as a pass.
     */
    public boolean kinPassportEmptyForLocal(String checkJson) {
        String s = checkJson == null ? "" : checkJson;
        boolean local = s.toLowerCase().contains("malays");
        boolean passportEmpty = s.contains("\"passportValue\":\"\"");
        boolean hasNric = !s.contains("\"nric\":\"\"");
        return local && passportEmpty && hasNric;
    }

    /**
     * PROBE — EVERY passport-ish label and input on the screen, with the section it sits in, whether it is
     * visible, starred, editable, and which ng-model it binds to. Answers "which element is the check actually
     * looking at?" — the patient's Passport No., the NOK one, or a grid column header.
     */
    /**
     * <b>Visa Details</b> — mandatory for a passport holder. Save is otherwise rejected with
     * "Visa Details are mandatory for Passport holders. Click the Visa button next to Passport No. to add them!".
     *
     * <p>Opens the modal ({@code fnOpenVisaDetailModal()}), fills Visa Type / Entry Type / Duration of Stay /
     * validity dates / Remarks, attaches the passport+visa copy when {@code attach} is given, then commits the row
     * with <b>Add</b> ({@code fnAddVisaDetails()}) — without Add the details stay in the modal and Save still
     * rejects the patient. Returns what was entered.</p>
     */
    /**
     * Type a visa field with REAL keystrokes and report what the model kept — JS alone does not bind these on IP
     * Admission. The selector is scoped to the VISIBLE control: the same ng-models exist elsewhere in the page's
     * DOM, and typing into a hidden twin looks like success while the modal stays empty.
     */
    private String typeVisaField(String ngModel, String value) {
        String sel = "input[ng-model='" + ngModel + "']:visible";
        try {
            com.microsoft.playwright.Locator box = page.locator(sel).first();
            box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            box.fill("");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(60));
            box.press("Tab");
        } catch (Exception e) {
            System.out.println("typeVisaField(" + ngModel + "): " + e.getMessage().split("\n")[0]);
        }
        waitForAngular(400);
        // VERIFY the raw input actually holds what was typed. A date field driven by an input-mask directive can
        // silently corrupt a fully-typed "dd/MM/yyyy" string with its own "/" — real keystrokes into ValidityEndDate
        // once landed as "13/08/0227" instead of "13/08/2027" (the app then rejected the visa outright: "Start
        // Date cannot be greater than End Date"), and nothing had checked the box actually held what was sent.
        boolean looksLikeDate = value.matches("\\d{2}/\\d{2}/\\d{4}");
        if (looksLikeDate) {
            String raw = rawInputValue(ngModel);
            if (!value.equals(raw)) {
                System.out.println("typeVisaField(" + ngModel + "): typed \"" + value + "\" but box holds \"" + raw
                        + "\" — retrying digits-only (mask likely inserts its own '/')");
                try {
                    com.microsoft.playwright.Locator box = page.locator(sel).first();
                    box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
                    box.fill("");
                    box.type(value.replace("/", ""), new com.microsoft.playwright.Locator.TypeOptions().setDelay(60));
                    box.press("Tab");
                } catch (Exception e) {
                    System.out.println("typeVisaField(" + ngModel + ") digits-only retry: " + e.getMessage().split("\n")[0]);
                }
                waitForAngular(400);
                raw = rawInputValue(ngModel);
            }
            if (!value.equals(raw)) {
                // Last resort: force the exact string through the ngModel controller directly.
                System.out.println("typeVisaField(" + ngModel + "): still \"" + raw + "\" — forcing via ngModel controller");
                page.evaluate("([m,v]) => { const A=window.angular;"
                        + " const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                        + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v;"
                        + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                        + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                        + " e.dispatchEvent(new Event('blur',{bubbles:true})); }", Arrays.asList(ngModel, value));
                waitForAngular(400);
            }
        }
        Object v = page.evaluate("(m) => { const A=window.angular;"
                + " const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return '(missing)'; const c=A.element(e).controller('ngModel');"
                + " const model = c && c.$modelValue!=null ? String(c.$modelValue) : '';"
                + " return (e.value||'') + (model ? '' : ' [model NOT set]'); }", ngModel);
        return ngModel.replace("Registration.", "") + "=" + v;
    }

    /** Raw text currently in a visible input bound to {@code ngModel} (not the parsed ngModel value). */
    private String rawInputValue(String ngModel) {
        Object v = page.evaluate("(m) => { const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " return e ? (e.value||'') : ''; }", ngModel);
        return v == null ? "" : v.toString();
    }

    public String fillVisaDetails(java.nio.file.Path attach) {
        boolean tagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('button,a')]"
                + " .find(e=>/fnOpenVisaDetailModal/i.test(e.getAttribute('ng-click')||'')); if(!b) return false; b.id='__visaBtn'; return true; }"));
        if (!tagged) return "(no Visa button on this screen)";
        try { page.locator("#__visaBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
        catch (Exception e) {
            System.out.println("fillVisaDetails: real click intercepted — DOM click fallback");
            try { page.evaluate("() => { const b=document.getElementById('__visaBtn'); if(b) b.click(); }"); } catch (Exception ignore) { }
        }
        page.evaluate("() => { const b=document.getElementById('__visaBtn'); if(b) b.removeAttribute('id'); }");
        // WAIT for the modal's own fields, don't just pause. IP Admission renders this modal more slowly than OP
        // Registration: a fixed wait filled nothing ("(no Registration.VisaTypeID)") while the later upload — by
        // then the modal was up — worked, so the row was never created.
        try {
            // Wait for the MODAL ITSELF to be open, not merely for the fields to exist: these inputs live in the
            // DOM the whole time and only become interactive when the dialog opens, so a field-existence gate let
            // the fill run against a closed modal (values reported "(missing)", the real select threw).
            page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const m=[...document.querySelectorAll('.modal,.modal-content,[uib-modal-window]')]"
                    + "   .find(x=>x.offsetParent!==null && /visa details/i.test(norm(x.textContent)));"
                    + " if(!m) return false;"
                    + " const s=m.querySelector(\"select[ng-model='Registration.VisaTypeID']\");"
                    + " const d=m.querySelector(\"input[ng-model='Registration.DurationOfStay']\");"
                    + " return !!(s && s.offsetParent!==null) && !!(d && d.offsetParent!==null); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(25000));
            // Give the Visa Type master a moment once the modal is up.
            try {
                page.waitForFunction("() => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.VisaTypeID');"
                        + " return !!(s && s.options.length>1); }", null, new Page.WaitForFunctionOptions().setTimeout(8000));
            } catch (Exception ignore) { System.out.println("fillVisaDetails: Visa Type list did not populate — will report what it offers"); }
        } catch (Exception e) {
            // Say WHY nothing opened: is the button there, visible, covered by something, and did the screen
            // answer with a toast? A datepicker/overlay left open by the previous field swallows the click.
            Object why = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a')].find(x=>/fnOpenVisaDetailModal/i.test(x.getAttribute('ng-click')||''));"
                    + " if(!b) return 'no Visa button in the DOM';"
                    + " const r=b.getBoundingClientRect();"
                    + " const top=document.elementFromPoint(r.left+r.width/2, r.top+r.height/2);"
                    + " const toasts=[...document.querySelectorAll('.toast-message,.toast,[id^=toast]')].map(e=>norm(e.textContent)).filter(t=>t);"
                    + " return 'visible='+(b.offsetParent!==null)+' disabled='+b.disabled+' rect='+Math.round(r.width)+'x'+Math.round(r.height)"
                    + "   +' at('+Math.round(r.left)+','+Math.round(r.top)+') covered-by='+(top?top.tagName+'.'+String(top.className||'').split(' ')[0]:'?')"
                    + "   +' toasts='+JSON.stringify(toasts.slice(0,3)); }");
            System.out.println("fillVisaDetails: the Visa modal did not open — " + why);
            // Clear anything hanging over it (open datepicker / dropdown), then click again for real.
            try { page.keyboard().press("Escape"); } catch (Exception ignore) { }
            waitForAngular(500);
            try {
                page.locator("button[ng-click='fnOpenVisaDetailModal()']:visible").first()
                        .click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
            } catch (Exception ex) {
                System.out.println("fillVisaDetails: retry click failed - " + ex.getMessage().split("\n")[0]);
                page.evaluate("() => { const b=[...document.querySelectorAll('button,a')].find(x=>/fnOpenVisaDetailModal/i.test(x.getAttribute('ng-click')||'')); if(b) b.click(); }");
            }
            try {
                page.waitForFunction("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " return [...document.querySelectorAll('.modal,.modal-content,[uib-modal-window]')]"
                        + "   .some(x=>x.offsetParent!==null && /visa details/i.test(norm(x.textContent))); }",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) {
                System.out.println("fillVisaDetails: still no Visa modal — the fields below cannot be filled");
            }
        }
        waitForAngular(800);

        java.time.LocalDate start = java.time.LocalDate.now();
        String fmtStart = start.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String fmtEnd = start.plusYears(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        Object filled = page.evaluate("(a) => { const A=window.angular; const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const set=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>(x.getAttribute('ng-model')||'')===ng && x.offsetParent!==null);"
                + "   if(!e) return '(no '+ng+')'; const c=A.element(e).controller('ngModel'); e.value=v;"
                + "   if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); return v; };"
                + " const sel=(ng)=>{ const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng && x.offsetParent!==null);"
                + "   if(!s) return '(no '+ng+')';"
                + "   const i=[...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + "   if(i<0) return '(no option)'; s.selectedIndex=i; s.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{ A.element(s).triggerHandler('change'); }catch(x){} return norm(s.options[i].textContent); };"
                // Entry Type is a radio pair (Single / Multiple) — a real element click so its binding fires.
                + " const r=[...document.querySelectorAll('input[type=radio]')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.EntryType' && x.offsetParent!==null);"
                + " if(r && !r.checked) r.click();"
                + " const visaType=sel('Registration.VisaTypeID');"
                + " const dur=set('Registration.DurationOfStay', a.duration);"
                + " const from=set('Registration.ValidityStartDate', a.start);"
                + " const to=set('Registration.ValidityEndDate', a.end);"
                + " const rem=set('Registration.Remark', a.remark);"
                + " return 'Visa Type='+visaType+' | Duration='+dur+' | Valid '+from+' to '+to+' | Remarks='+rem; }",
                java.util.Map.of("duration", "30", "start", fmtStart, "end", fmtEnd,
                        "remark", "Visitor visa for medical treatment"));
        waitForAngular(600);

        // Visa Type with a REAL selectOption. Driving it from JS (selectedIndex + change) leaves the model unset
        // on IP Admission — the list had 10 entries and Add still answered "Please select Visa Type!".
        String visaType = "";
        for (int attempt = 0; attempt < 3 && visaType.isEmpty(); attempt++) {
            try {
                com.microsoft.playwright.Locator sel = page.locator("select[ng-model='Registration.VisaTypeID']:visible").first();
                Object idx = page.evaluate("() => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.VisaTypeID');"
                        + " if(!s) return -1; return [...s.options].findIndex(o=>o.value && !/^-*\\s*select/i.test((o.textContent||'').trim())); }");
                int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
                if (i >= 0) sel.selectOption(new com.microsoft.playwright.options.SelectOption().setIndex(i),
                        new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
            } catch (Exception e) { System.out.println("fillVisaDetails: real Visa Type select failed - " + e.getMessage()); }
            waitForAngular(700);
            Object committed = page.evaluate("() => { const A=window.angular;"
                    + " const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.VisaTypeID');"
                    + " if(!s) return ''; const c=A.element(s).controller('ngModel');"
                    + " const set = c && c.$modelValue!=null && c.$modelValue!=='';"
                    + " return set ? ((s.options[s.selectedIndex]||{}).textContent||'').trim() : ''; }");
            visaType = committed == null ? "" : committed.toString().trim();
            if (visaType.isEmpty()) System.out.println("fillVisaDetails: Visa Type not committed yet (attempt " + (attempt + 1) + ")");
        }
        System.out.println("fillVisaDetails: Visa Type = " + (visaType.isEmpty() ? "(NOT set)" : visaType));

        // Duration / validity dates typed for REAL. The JS fill above is enough on OP Registration but not on IP
        // Admission, where Add answered "Please enter Duration of Stay!" over a box that visibly held 30.
        String typed = typeVisaField("Registration.DurationOfStay", "30")
                + " | " + typeVisaField("Registration.ValidityStartDate", fmtStart)
                + " | " + typeVisaField("Registration.ValidityEndDate", fmtEnd);
        System.out.println("fillVisaDetails: typed -> " + typed);

        // The passport + visa copies are MANDATORY: without them Add answers "Please select Passport copy!" and
        // no visa row is created, so Save still rejects the patient. Attach BOTH and verify each input took a file.
        StringBuilder attached = new StringBuilder();
        if (attach != null && java.nio.file.Files.exists(attach)) {
            for (String[] pair : new String[][]{ { "PassportFileData", "PassportFile" }, { "VisaFileData", "VisaFile" } }) {
                try {
                    page.locator("input[type=file][ng-model='" + pair[0] + "']").first()
                            .setInputFiles(attach, new com.microsoft.playwright.Locator.SetInputFilesOptions().setTimeout(8000));
                    // Setting the file is not enough: the app's inline onchange calls PhotoChanged(files, flag),
                    // which validates the extension and UPLOADS the copy, and only then sets the scope's
                    // PassportFile / VisaFile. Those stayed null (so Add answered "Please select Passport copy!"),
                    // hence invoke the app's own handler the same way its onchange does.
                    page.evaluate("([ng,flag]) => { const A=window.angular;"
                            + " const e=[...document.querySelectorAll('input[type=file]')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                            + " if(!e) return; let s=A.element(e).scope(); while(s && !s.fnAddVisaDetails) s=s.$parent;"
                            + " if(s && typeof s.PhotoChanged==='function') s.PhotoChanged(e.files, flag); }",
                            java.util.Arrays.asList(pair[0], pair[1]));
                    attached.append(attached.length() == 0 ? "" : ", ").append(pair[0]).append("=set");
                } catch (Exception e) {
                    System.out.println("fillVisaDetails: could not attach to " + pair[0] + " - " + e.getMessage());
                    attached.append(attached.length() == 0 ? "" : ", ").append(pair[0]).append("=FAILED");
                }
            }
            // The copies UPLOAD asynchronously (Upload.upload -> api/patientregistration/GetPhotoData) and the
            // image only appears when that returns — Add before then is answered with "Please select Passport
            // copy!". Wait for BOTH to land, looking across every scope (the two values do not always live on the
            // same one) and accepting the modal's preview <img> as an equally good signal.
            final String COPIES_READY =
                    "() => { const A=window.angular; const seen=new Set(); let passport=false, visa=false;"
                    + " document.querySelectorAll('input[type=file],form,div[ng-controller],body').forEach(el=>{ try{ let x=A.element(el).scope();"
                    + "   for(let i=0;i<15&&x;i++){ if(!seen.has(x.$id)){ seen.add(x.$id);"
                    + "     if(x.PassportFile) passport=true; if(x.VisaFile) visa=true; } x=x.$parent; } }catch(e){} });"
                    + " const m=[...document.querySelectorAll('.modal,.modal-content')].find(x=>x.offsetParent!==null && /visa details/i.test(x.textContent||''));"
                    + " const imgs=m ? [...m.querySelectorAll('img')].filter(i=>i.offsetParent!==null && (i.src||'').length>30 && i.complete).length : 0;"
                    + " return (passport && visa) || imgs>=2; }";
            try {
                page.waitForFunction(COPIES_READY, null, new Page.WaitForFunctionOptions().setTimeout(60000));
                attached.append(" (both copies uploaded)");
            } catch (Exception e) {
                Object state = page.evaluate("() => { const A=window.angular; const seen=new Set(); const out=[];"
                        + " document.querySelectorAll('input[type=file],form,body').forEach(el=>{ try{ let x=A.element(el).scope();"
                        + "   for(let i=0;i<15&&x;i++){ if(!seen.has(x.$id)){ seen.add(x.$id);"
                        + "     if('PassportFile' in x) out.push('PassportFile='+(x.PassportFile?'set':'null'));"
                        + "     if('VisaFile' in x) out.push('VisaFile='+(x.VisaFile?'set':'null')); } x=x.$parent; } }catch(e){} });"
                        + " const m=[...document.querySelectorAll('.modal,.modal-content')].find(x=>x.offsetParent!==null && /visa details/i.test(x.textContent||''));"
                        + " out.push('preview imgs='+(m?[...m.querySelectorAll('img')].filter(i=>(i.src||'').length>30).length:'n/a'));"
                        + " return [...new Set(out)].join(', '); }");
                attached.append(" (copies did NOT finish uploading in 60s -> ").append(state).append(")");
            }
        } else {
            attached.append("(no file given)");
        }
        System.out.println("fillVisaDetails: attachments -> " + attached);

        // Click the uploaded copies once they are on screen — the screen expects the thumbnail to be acted on
        // after the upload, and clicking is harmless when there is nothing there.
        Object clicked = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,.modal-content')].find(x=>x.offsetParent!==null && /visa details/i.test(x.textContent||''));"
                // Only ever click INSIDE the Visa modal — falling back to the whole document clicked eight
                // unrelated images on the Admission screen.
                + " const scope=m; if(!scope) return '(no Visa modal on screen)';"
                + " const imgs=[...scope.querySelectorAll('img,a[ng-click],i[ng-click],span[ng-click]')]"
                + "   .filter(e=>e.offsetParent!==null && ((e.tagName==='IMG' && (e.src||'').length>30)"
                + "     || /view|preview|passport|visa|photo/i.test((e.getAttribute('ng-click')||'')+' '+(e.title||''))));"
                + " if(!imgs.length) return '(no uploaded image to click)';"
                + " const what=imgs.map(e=>e.tagName+(e.tagName==='IMG'?('['+(e.alt||'img')+']'):('{'+(e.getAttribute('ng-click')||'')+'}')));"
                + " imgs.forEach(e=>{ try{ e.click(); }catch(err){} });"
                + " return 'clicked '+imgs.length+': '+what.slice(0,4).join(', '); }");
        System.out.println("fillVisaDetails: uploaded copy click -> " + clicked);
        waitForAngular(1200);
        // Any viewer the click opened must be closed again, or it covers the Add button.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const viewers=[...document.querySelectorAll('.modal,.modal-content')].filter(x=>x.offsetParent!==null"
                + "   && !/visa details/i.test(x.textContent||'') && /\\.(png|jpe?g)|preview|image/i.test(x.innerHTML||''));"
                + " viewers.forEach(v=>{ const c=[...v.querySelectorAll('button,a,.close')].find(x=>/^\\s*(close|×|x|ok)\\s*$/i.test(norm(x.textContent))); if(c) c.click(); }); }");
        waitForAngular(600);

        // Watch what Add does: any toast it raises, and whether a visa row lands in the scope's list. Clicking Add
        // is not proof — the kin taught that lesson, and Save keeps asking for Visa Details when the row is not
        // committed.
        page.evaluate("() => { window.__visaToasts=[]; if(window.__visaObs) window.__visaObs.disconnect();"
                + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__visaToasts.includes(t)) window.__visaToasts.push(t); }); };"
                + " window.__visaObs=new MutationObserver(grab); window.__visaObs.observe(document.body,{childList:true,subtree:true}); }");
        boolean addTagged = Boolean.TRUE.equals(page.evaluate("() => { const b=[...document.querySelectorAll('button,a')]"
                + " .find(e=>/fnAddVisaDetails/i.test(e.getAttribute('ng-click')||'') && e.offsetParent!==null);"
                + " if(!b) return false; b.id='__visaAdd'; return true; }"));
        String added = "(no Add button)";
        if (addTagged) {
            try { page.locator("#__visaAdd").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); added = "Add clicked"; }
            catch (Exception e) {
                try { page.evaluate("() => { const b=document.getElementById('__visaAdd'); if(b) b.click(); }"); added = "Add clicked (DOM)"; }
                catch (Exception ignore) { added = "Add click failed"; }
            }
            page.evaluate("() => { const b=document.getElementById('__visaAdd'); if(b) b.removeAttribute('id'); }");
            waitForAngular(1500);
            Object diag = page.evaluate("() => { const A=window.angular; const out=[];"
                    + " out.push('toasts='+JSON.stringify(window.__visaToasts||[]));"
                    + " const seen=new Set(); const lists=[];"
                    + " document.querySelectorAll('form,div[ng-controller],body').forEach(el=>{ try{ let x=A.element(el).scope();"
                    + "   for(let i=0;i<15&&x;i++){ if(!seen.has(x.$id)){ seen.add(x.$id);"
                    + "     Object.keys(x).forEach(k=>{ if(/visa/i.test(k) && Array.isArray(x[k])) lists.push(k+'='+x[k].length); }); } x=x.$parent; } }catch(e){} });"
                    + " out.push('visa lists: '+(lists.length?[...new Set(lists)].join(', '):'(none found)'));"
                    + " const m=[...document.querySelectorAll('.modal,.modal-content')].find(x=>x.offsetParent!==null && /visa details/i.test((x.textContent||'')));"
                    + " out.push('modal rows='+(m ? [...m.querySelectorAll('tbody tr')].filter(r=>(r.textContent||'').trim() && !/no records/i.test(r.textContent)).length : 'n/a'));"
                    + " return out.join(' | '); }");
            System.out.println("fillVisaDetails: after Add => " + diag);
            added += " | attachments " + attached + " | image click: " + clicked + " | " + diag;
        }
        // Close the modal if it is still up, so the rest of the form is reachable.
        page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const m=[...document.querySelectorAll('.modal,.modal-content')].find(x=>x.offsetParent!==null && /visa details/i.test(norm(x.textContent)));"
                + " if(!m) return; const c=[...m.querySelectorAll('button,a')].find(x=>/^\\s*close\\s*$/i.test(norm(x.textContent)));"
                + " if(c) c.click(); }");
        waitForAngular(1200);
        return filled + " | " + added;
    }

    public String dumpPassportElements() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sectionOf=e=>{ let n=e, hop=0; while(n && hop++<12){ n=n.parentElement; if(!n) break;"
                + "   const h=n.querySelector && n.querySelector('.panel-title,.panel-heading,.box-title,legend,h3,h4');"
                + "   if(h){ const t=norm(h.textContent).slice(0,40); if(t) return t; } } return '?'; };"
                + " const out=[];"
                + " [...document.querySelectorAll('label,.control-label,th')].filter(l=>/passport/i.test(l.textContent||''))"
                + "   .forEach(l=>out.push('LABEL<'+l.tagName+'> \"'+norm(l.textContent).slice(0,28)+'\" star='+/\\*/.test(l.textContent||'')"
                + "     +' visible='+(l.offsetParent!==null)+' section=\"'+sectionOf(l)+'\"'));"
                + " [...document.querySelectorAll('input,textarea')].filter(e=>/passport/i.test((e.getAttribute('ng-model')||'')+' '+(e.name||'')+' '+(e.placeholder||'')))"
                + "   .forEach(e=>out.push('INPUT ng=\"'+(e.getAttribute('ng-model')||'')+'\" visible='+(e.offsetParent!==null)"
                + "     +' disabled='+e.disabled+' readonly='+e.readOnly+' value=\"'+(e.value||'')+'\" section=\"'+sectionOf(e)+'\"'));"
                + " return out.join('\\n  '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Enablement of the Passport No. box is gated by a SEPARATE control, {@code Registration.ICCardTypeID}
     * ("Identification Type") — Nationality alone does nothing to it. Verified live 2026-08-24: with Identification
     * Type left on "New IC", setting Nationality to a foreign country (even via a real {@code selectOption}) left
     * the Passport box disabled; only switching Identification Type to "Passport" enabled it. A run where
     * {@code fillPatientInformation()} had already set both earlier can still have Identification Type drift back
     * (e.g. a later Nationality re-assert resetting it) by the time this check runs — self-heal it here, the same
     * "re-assert before judging" pattern used by {@code ensureDoctorSelected}/{@code ensureDepartmentSelected} on
     * IP Admission, rather than reporting a false DEFECT for a field the fill routine already knows how to set.
     */
    private void ensureIdentificationTypeForNationality(boolean malaysian) {
        String want = malaysian ? "New IC" : "Passport";
        boolean wantEnabled = !malaysian;
        for (int attempt = 0; attempt < 3; attempt++) {
            Object cur = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID');"
                    + " const o=e?e.options[e.selectedIndex]:null; return o?o.textContent.trim():''; }");
            // Re-select even when the label already reads "want" — a select whose VALUE doesn't change fires no
            // 'change' event, so if the label is stale-but-correct from an earlier fill, the ng-disabled watcher on
            // Registration.FamilyName never re-ran. Force a real change by picking a DIFFERENT option first when
            // already on the wanted one, then re-selecting it.
            try {
                if (want.equalsIgnoreCase(String.valueOf(cur))) {
                    page.locator("select[ng-model='Registration.ICCardTypeID']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(malaysian ? "Passport" : "New IC"));
                    waitForAngular(300);
                }
                page.locator("select[ng-model='Registration.ICCardTypeID']").first().selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(want));
            } catch (Exception e) {
                System.out.println("ensureIdentificationTypeForNationality: selectOption(" + want + ") failed - " + e.getMessage());
            }
            // Poll for the Passport box's OWN disabled state to actually flip — the select's label can update a
            // beat before the ng-disabled watcher elsewhere on the form re-evaluates.
            boolean settled = false;
            for (int w = 0; w < 10; w++) {
                Object disabled = page.evaluate("() => { const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')==='Registration.FamilyName'); return e ? e.disabled : null; }");
                if (disabled instanceof Boolean && ((Boolean) disabled) != wantEnabled) { settled = true; break; }
                page.waitForTimeout(300);
            }
            if (settled) return;
            System.out.println("ensureIdentificationTypeForNationality: Passport box disabled state did not settle after attempt " + (attempt + 1));
        }
    }

    public String checkPassportRequiredForLocal() {
        ensureIdentificationTypeForNationality(patientIsMalaysian());
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " let nat=''; const ns=[...document.querySelectorAll('select')].find(s=>s.getAttribute('ng-model')==='Registration.NationalityID');"
                + " if(ns && ns.selectedIndex>=0){ const o=ns.options[ns.selectedIndex]; nat=norm(o?o.textContent:''); }"
                + " let idType=''; const is=[...document.querySelectorAll('select')].find(s=>s.getAttribute('ng-model')==='Registration.ICCardTypeID');"
                + " if(is && is.selectedIndex>=0){ const o=is.options[is.selectedIndex]; idType=norm(o?o.textContent:''); }"
                + " const labs=[...document.querySelectorAll('label,.control-label')].filter(l=>/passport\\s*no/i.test(l.textContent||'') && norm(l.textContent).length<30);"
                + " if(!labs.length) return JSON.stringify({nat, idType, passportVisible:false, enabled:false});"
                + " const lab=labs.find(l=>l.offsetParent!==null) || labs[0]; const labStar=/\\*/.test(lab.textContent||'');"
                // The Passport No. box IS Registration.FamilyName (not a surname) — take it directly when it is
                // there. Resolving it from the label found the wrong control on IP Admission and reported the
                // field as unusable on a run whose passport was accepted and saved.
                + " let inp=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.FamilyName') || null;"
                + " const fid=lab.getAttribute('for'); if(!inp && fid) inp=document.getElementById(fid);"
                + " if(!inp){ const g=lab.closest('.form-group,.row,.form-line,.col-sm-6,.col-md-6,.col-sm-4,div'); if(g) inp=g.querySelector('input,textarea'); }"
                + " if(!inp){ let sib=lab.nextElementSibling; for(let i=0;i<4&&sib&&!inp;i++){ inp=sib.tagName==='INPUT'?sib:(sib.querySelector?sib.querySelector('input'):null); sib=sib.nextElementSibling; } }"
                + " const visible = !!inp && inp.offsetParent!==null;"
                + " const domRequired = !!inp && inp.required===true;"
                + " const invalidReq = !!inp && /\\bng-invalid-required\\b/.test(inp.className||'');"
                // ENABLED = the user can actually put a passport in. A starred LABEL over a disabled/absent box is
                // cosmetic: on a Malaysian the screen leaves "Passport No.*" starred while the control itself is
                // disabled, and treating that asterisk as the verdict failed the app for something no user can do.
                // What counts is a usable control, or Angular genuinely demanding a value.
                + " const enabled = (!!inp && visible && !inp.disabled && !inp.readOnly) || domRequired || invalidReq;"
                // FILLABLE = the field will accept a passport number. A Malaysian registers on the NRIC, so the app
                // should not let one be typed at all — being merely "not starred" is not good enough.
                // Judge by EDITABILITY (visible + not disabled + not readonly), which is what "able to fill" means.
                // A write-and-read-back probe looks more rigorous but lies here: setting .value and reading it back
                // races Angular's $render, which restores the DOM from the model, so a field we demonstrably fill
                // through the ngModel reported back as unfillable. `writeHeld` keeps that observation as
                // information only — it is not what the verdict rests on.
                + " const fillable = !!inp && visible && !inp.disabled && !inp.readOnly;"
                + " let writeHeld=false, probeErr='';"
                + " if(fillable){ const before=inp.value;"
                + "   try{ inp.value='X9999999'; inp.dispatchEvent(new Event('input',{bubbles:true}));"
                + "        writeHeld = (inp.value==='X9999999');"
                + "        inp.value=before; inp.dispatchEvent(new Event('input',{bubbles:true}));"
                + "   }catch(e){ probeErr=String(e && e.message || e); } }"
                + " return JSON.stringify({nat, idType, passportVisible:visible, labStar, domRequired, invalidReq, enabled, fillable, writeHeld, probeErr}); }");
        return r == null ? "{}" : r.toString();
    }

    /**
     * True when the patient's Nationality is Malaysian AND the Passport No. field still accepts input.
     *
     * <p>A Malaysian registers with an NRIC, so the passport control should be closed to entry entirely — not merely
     * unstarred. Anything else lets a passport number be stored against a local patient, which is how this suite came
     * to be writing one for every Malaysian it registered.</p>
     */
    public boolean passportFillableForMalaysian(String checkJson) {
        String nat = group(checkJson, "\"nat\":\"([^\"]*)\"");
        boolean fillable = checkJson.contains("\"fillable\":true");
        return fillable && nat.toLowerCase().startsWith("malaysia");
    }

    /**
     * Validation check — after a Next of Kin is added, verify the <b>NOK/Guarantor grid</b> shows each value UNDER
     * ITS CORRECT COLUMN HEADER. DSH mis-maps the columns (e.g. the RELATIONSHIP column shows the passport/NRIC,
     * MOBILE NO shows the nationality, E-MAIL shows the name, ADDRESS shows the title). We read the header row +
     * first data row, map header→cell, and flag a defect when a column's value is semantically wrong:
     * RELATIONSHIP isn't the entered relationship (or a known relationship word), MOBILE NO contains letters, or
     * E-MAIL has no "@". Returns a JSON-ish string incl. a non-empty {@code "problems":[...]} when mis-mapped.
     */
    /** What the kin form ACTUALLY ended up holding — may differ from the profile when the option does not exist. */
    public String kinTitleActual = "", kinNationalityActual = "", kinRelActual = "", kinOccupationActual = "";

    public String checkNokGridColumnMapping(String expRel, String expMobile, String expName) {
        // Compare the grid against what was REALLY selected, not what was asked for. When an environment has no
        // "Biological Child", setSelLike falls back and the grid then shows "Adopted Daughter" — correctly. Checking
        // against the requested value made the report accuse the APP of mis-mapping its columns when the wrong value
        // came from this test. The substitution itself is reported separately, as a data gap, by setSelLike.
        String requested = expRel;
        if (kinRelActual != null && !kinRelActual.isEmpty()) expRel = kinRelActual;
        final String substituted = (kinRelActual != null && !kinRelActual.isEmpty()
                && !kinRelActual.equalsIgnoreCase(requested))
                ? " [NOTE: this environment has no \"" + requested + "\" — the test used \"" + kinRelActual + "\"]" : "";
        Object r = page.evaluate("(a) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const expRel=norm(a[0]).toLowerCase();"
                + " const t=[...document.querySelectorAll('table')].find(x=>{ const h=norm((x.querySelector('thead')||{}).innerText||'').toLowerCase(); return /relationship/.test(h) && /mobile/.test(h) && (/guarantor/.test(h) || /legal\\s*guardian/.test(h)); });"
                + " if(!t) return JSON.stringify({found:false});"
                + " const heads=[...t.querySelectorAll('thead th,thead td')].map(h=>norm(h.textContent).toLowerCase());"
                + " const row=[...t.querySelectorAll('tbody tr')].find(r=>{ const tx=norm(r.textContent); return tx && !/no records|no data/i.test(tx); });"
                + " if(!row) return JSON.stringify({found:true, row:false});"
                + " const cells=[...row.querySelectorAll('td')].map(td=>norm(td.textContent));"
                + " const val=name=>{ const key=name.replace(/\\s+/g,''); const i=heads.findIndex(h=>h.replace(/\\s+/g,'')===key); return i>=0?(cells[i]||''):''; };"
                + " const relCell=val('relationship'), mobCell=val('mobile no'), emailCell=val('e-mail')||val('email');"
                + " const knownRels=['father','mother','grandfather','grandmother','biological child','adopted child','adopted son','spouse','sibling','brother','sister'];"
                + " const problems=[];"
                + " if(relCell && (/^[0-9]+$/.test(relCell.replace(/\\s/g,'')) || (relCell.toLowerCase().indexOf(expRel)<0 && knownRels.indexOf(relCell.toLowerCase())<0))) problems.push('RELATIONSHIP shows \"'+relCell+'\"');"
                + " if(mobCell && /[a-z]/i.test(mobCell)) problems.push('MOBILE NO shows \"'+mobCell+'\"');"
                + " if(emailCell && !/@/.test(emailCell)) problems.push('E-MAIL shows \"'+emailCell+'\"');"
                + " const map={}; heads.forEach((h,i)=>{ if(h && h!=='delete') map[h]=cells[i]||''; });"
                // DIAGNOSTIC: what the kin FORM currently holds, and what each list's FIRST option is.
                // On KLG/DSH the TITLE / NATIONALITY / OCCUPATION cells come back as the same constants in every
                // run whatever was selected, while devhis shows the real values. If those constants equal the
                // first option of each list, the grid is falling back to list[0] because the saved id does not
                // resolve — a lookup/master-data problem, not a "wrong value was saved" one. This puts the proof
                // in the report instead of leaving it to inference.
                + " const selBy=ng=>[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + " const firstOpt=e=>{ if(!e) return ''; const o=[...e.options].find(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))); return o?norm(o.textContent):''; };"
                + " const curOpt=e=>{ if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const probe={}; [['title','Registration.KinTitleID'],['nationality','Registration.NOKnationalid'],"
                + "   ['relationship','Registration.KinRelationID'],['occupation','Registration.KinOccupationID']]"
                + "  .forEach(([k,ng])=>{ const e=selBy(ng); probe[k]={form:curOpt(e), first:firstOpt(e), cell:(map[k]||'')}; });"
                + " return JSON.stringify({found:true, row:true, problems, map, probe}); }",
                java.util.Arrays.asList(expRel, expMobile, expName));
        return (r == null ? "{}" : r.toString()) + substituted;
    }

    /** Attach an image to Photo (#PhotoData), Thumb (#ThumbData) and IC Card (#ICCard). Returns what stuck. */
    public String attachDocuments(Path file) {
        String attached = "";
        try { page.setInputFiles("#PhotoData", file); attached += "Photo "; } catch (Exception e) { System.out.println("Photo attach: " + e.getMessage()); }
        try { page.setInputFiles("#ThumbData", file); attached += "Thumb "; } catch (Exception e) { System.out.println("Thumb attach: " + e.getMessage()); }
        try { page.setInputFiles("#ICCard", file);    attached += "IC Card"; } catch (Exception e) { System.out.println("Card attach: " + e.getMessage()); }
        waitForAngular(800);
        return attached.trim();
    }

    /** Correspondence Details — Postcode auto-fills City/State/Country; Mobile set via the model. */
    public void fillCorrespondence(PatientProfile p) {
        fillModel("Registration.ResHouseNo", p.houseNo);
        fillModel("Registration.ResStreet", p.street);
        fillModel("Registration.ResAddress", p.address);
        fillModel("Registration.ResPinCode", p.postcode);
        page.evaluate("(v) => { const e=document.querySelector('#txtMobileNo') || [...document.querySelectorAll(\"input[ng-model='Registration.MobileNo']\")][0];"
                + " if(e){ const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('change',{bubbles:true})); } }", p.mobile);
        snap("Registration.MobileNo", p.mobile);
        fillModel("Registration.ResiNo", p.phone);
        fillModel("Registration.Email", p.email);
        // The postcode fires an ASYNC lookup that loads the City/State/Country dropdowns. Wait for the City select
        // to actually get its options before we read/commit them — else ResCityID is empty at Save and the app
        // rejects it with "Please Select City".
        try {
            page.waitForFunction(
                    "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ResCityID'); return e && e.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillCorrespondence: City dropdown did not populate from postcode in time"); }
        waitForAngular(400);
        // Ensure City/State/Country are actually SELECTED — the postcode auto-fill sometimes loads the options but
        // leaves the ng-model empty. For any that's still empty, pick the first real option (selectedIndex=1, the
        // ng-options-safe way) and capture the resulting model value.
        Object geo = page.evaluate("() => { const ensure=(ng)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null; const c=angular.element(e).controller('ngModel');"
                + " let v=(c && c.$modelValue!=null)?c.$modelValue:null; if((v===null||v==='') && e.options.length>1){ e.selectedIndex=1; e.dispatchEvent(new Event('change',{bubbles:true})); try{ if(window.jQuery) window.jQuery(e).trigger('change'); }catch(e2){} v=(c && c.$modelValue!=null)?c.$modelValue:null; } return v; };"
                + " return { ResCountryID:ensure('Registration.ResCountryID'), ResStateID:ensure('Registration.ResStateID'), ResCityID:ensure('Registration.ResCityID'), MobileCountryCode:ensure('Registration.MobileCountryCode') }; }");
        waitForAngular(300);
        // Snapshot the postcode-derived geo values first; the fallback below (if it runs) overrides State/City.
        if (geo instanceof Map) ((Map<?, ?>) geo).forEach((k, v) -> snap("Registration." + k, v));
        // FALLBACK: if the postcode gave no usable City (dropdown has no real option / model still empty), pick a
        // DIFFERENT State — that repopulates the City list — then select the first city. Try each state until one
        // yields at least one city option, and capture the resulting State+City model values.
        boolean cityOk = Boolean.TRUE.equals(page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ResCityID'); if(!e) return false; const c=angular.element(e).controller('ngModel'); return e.options.length>1 && c && c.$modelValue!=null && c.$modelValue!==''; }"));
        if (!cityOk) {
            System.out.println("fillCorrespondence: no City from postcode — selecting a different State to load cities.");
            Object fb = page.evaluate("async () => { const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                    + " const st=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ResStateID');"
                    + " const ct=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ResCityID');"
                    + " if(!st||!ct) return null; const stC=angular.element(st).controller('ngModel'), ctC=angular.element(ct).controller('ngModel');"
                    + " for(let i=1;i<st.options.length;i++){ st.selectedIndex=i; st.dispatchEvent(new Event('change',{bubbles:true})); try{ if(window.jQuery) window.jQuery(st).trigger('change'); }catch(e){}"
                    + "   await sleep(700);"  // let the State's ng-change load the City list
                    + "   if(ct.options.length>1){ ct.selectedIndex=1; ct.dispatchEvent(new Event('change',{bubbles:true})); try{ if(window.jQuery) window.jQuery(ct).trigger('change'); }catch(e){} await sleep(200);"
                    + "     return { ResStateID:(stC&&stC.$modelValue!=null)?stC.$modelValue:null, ResCityID:(ctC&&ctC.$modelValue!=null)?ctC.$modelValue:null }; } }"
                    + " return null; }");
            waitForAngular(300);
            if (fb instanceof Map) ((Map<?, ?>) fb).forEach((k, v) -> { if (v != null) snap("Registration." + k, v); });
        }
    }

    /** Other Details — Language / Occupation / Employer. */
    public void fillOtherDetails() {
        setSel("Registration.LanguagePreferedID", "English");
        setSel("Registration.OccupationId", "Education");
        fillModel("Registration.EmployerOccupation", "ABC Company Sdn Bhd");
    }

    /**
     * Fill the Payor Information section = Self. The "Payor Information" accordion header
     * ({@code FillSponserDropDown}) loads the default payor GRID ROW for the MRN; clicking that row
     * ({@code EditSponser($index)}) populates the payor FORM (Payor Mode=Self, Payor/Pricing Policy=
     * SELFPAY CASH CUSTOMER). Selecting only the Insurer radio leaves the form empty. Also ticks the
     * Insurer "Self" radio (value=1) and backfills any empty mandatory payor select. Returns true when
     * Payor Mode = Self (or the Insurer radio is checked as a fallback).
     */
    /** What the payor FORM held after {@link #selectPayorSelf()} — so the step can report WHY it failed. */
    public String lastPayorState = "";

    /** Fields the APP itself flagged on the last "mandatory fields" rejection (empty when the build doesn't tag). */
    public String lastMandatoryFields = "";

    /** What the Visit Information section actually holds after the fill — set by {@link #visitInformationComplete()}. */
    public String lastVisitState = "";

    /**
     * Did the Visit Information fill actually take?
     *
     * <p>The step used to report "Visit Information filled / PASS" unconditionally, so a run where Department,
     * Primary Doctor and Visit Type were all still empty passed here and only failed three steps later at Save,
     * with nothing pointing back to this section. Read the values instead: Department, Doctor and Visit Type must
     * hold something, and Sub Dept too when the screen marks it mandatory.</p>
     *
     * @return true when every mandatory visit field holds a value; {@link #lastVisitState} carries the detail
     */
    public boolean visitInformationComplete() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const sel=ng=>{ const all=[...document.querySelectorAll('select')].filter(x=>(x.getAttribute('ng-model')||'')===ng);"
                + "   return all.find(x=>x.offsetParent!==null) || all[0]; };"
                + " const val=e=>{ if(!e) return null; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const opts=e=>e?[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))).length:0;"
                // Sub Dept counts only where the screen stars it — some builds leave it optional.
                + " const starred=e=>{ if(!e) return false; if(e.hasAttribute('required')||e.hasAttribute('ng-required')) return true;"
                + "   let n=e.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label');"
                + "     if(l && /sub\\s*dep(t|art)/i.test(norm(l.textContent))) return /\\*/.test(l.textContent)||!!l.querySelector('.arstrik');"
                + "     n=n.parentElement; } return false; };"
                + " const dep=sel('Visit.DepartmentID'), sub=sel('Visit.SubDepartmentID'),"
                + "   doc=sel('Visit.DoctorID'), vt=sel('Visit.VisitTypeID');"
                + " const parts=[], missing=[], reasons=[];"
                // Single quotes on purpose: the value travels back inside a JSON string, and embedded double
                // quotes truncated the report text at the first one ("Department=\").
                + " const add=(name,e,required)=>{ const v=val(e); const n=opts(e);"
                + "   parts.push(name+\"='\"+(v===null?'(no field)':v)+\"'\"+(e&&!v?' [list has '+n+' options]':''));"
                + "   if(required && e && !v){ missing.push(name);"
                // Say WHY in plain words: an empty list is a data problem, an unselected one is a fill problem.
                + "     reasons.push(n===0 ? 'the '+name+' dropdown is EMPTY (no options loaded)'"
                + "                        : 'the '+name+' dropdown has '+n+' options but none is selected'); } };"
                + " add('Department',dep,true); add('Sub Dept',sub,starred(sub)); add('Primary Doctor',doc,true); add('Visit Type',vt,true);"
                + " return JSON.stringify({state:parts.join(' | '), missing:missing.join(', '), reasons:reasons.join('; ')}); }");
        String s = String.valueOf(r);
        String missing = group(s, "\"missing\":\"([^\"]*)\"");
        String reasons = group(s, "\"reasons\":\"([^\"]*)\"");
        lastVisitState = group(s, "\"state\":\"([^\"]*)\"").replace("\\\"", "\"");
        if (!missing.isEmpty())
            lastVisitState = "CAUSE: " + reasons + " — MISSING: " + missing + " — " + lastVisitState;
        return missing.isEmpty();
    }

    /** Why Sub Department could not be filled, when no department on the environment offers one. */
    public String lastSubDeptDiagnosis = "";

    /**
     * The fields the screen ITSELF marked as missing on the last blocked save.
     *
     * <p>Builds carrying the aggregated validator ({@code fnFATCollectMandatory} / {@code fnFATBlockOnMandatory})
     * add a {@code has-error} class to every offending form-group and focus the first one, so the app's own answer
     * can be read straight out of the DOM instead of re-deriving it from asterisks. Returns "" on older builds.</p>
     */
    public String appFlaggedMandatoryFields() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const out=[];"
                + " document.querySelectorAll('.has-error').forEach(g=>{"
                + "   const lab=g.querySelector('label'); const ctl=g.querySelector('select,input,textarea');"
                + "   const name=norm(lab?lab.textContent:'') || (ctl?(ctl.getAttribute('ng-model')||ctl.name||ctl.id):'') || norm(g.textContent).slice(0,30);"
                + "   const model=ctl?(ctl.getAttribute('ng-model')||''):'';"
                + "   const val=ctl? (ctl.tagName==='SELECT'? norm((ctl.options[ctl.selectedIndex]||{}).textContent) : norm(ctl.value)) : '';"
                + "   out.push(name+(model?'['+model+']':'')+'=\"'+val+'\"'); });"
                + " return [...new Set(out)].join(', '); }");
        return r == null ? "" : r.toString();
    }

    public boolean selectPayorSelf() {
        // Open the Payor Information accordion so its default row loads.
        page.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>x.getAttribute('ng-click')==='FillSponserDropDown();' && x.offsetParent!==null); if(a) a.click(); }");
        // KS serves the payor masters slowly: at 8s the grid was still empty, EditSponser therefore never ran, the
        // payor FORM stayed blank and Save died on the nameless "Please fill in all the mandatory fields!" with
        // Payor Mode / Payor Code / Priority / Pricing Policy empty. At 15s the same environment has both the
        // default SELFPAY row and a 28-option Payor Mode list, so wait properly — for the ROW and for the LIST.
        try {
            page.waitForFunction(
                    "() => [...document.querySelectorAll('table')].some(t=>/PAYOR MODE|PRICING POLICY/i.test((t.querySelector('thead')||{}).innerText||'') && /self|selfpay|cash/i.test((t.querySelector('tbody')||{}).innerText||''))",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("selectPayorSelf: payor default row did not load"); }
        try {
            page.waitForFunction(
                    "() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypeid'); return e && e.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("selectPayorSelf: Payor Mode list did not populate"); }
        waitForAngular(500);
        // Invoke the payor grid row's handler (EditSponser) — that fills the payor form with Self.
        page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/PAYOR MODE|PRICING POLICY/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return; const row=t.querySelector('tbody tr'); if(!row) return;"
                + " const cell=[...row.querySelectorAll('td')].find(td=>/self|selfpay|cash/i.test(td.textContent||'')) || row.querySelector('td') || row;"
                + " const sc=angular.element(cell).scope(); if(sc && typeof sc.EditSponser==='function'){ sc.$apply(function(){ sc.EditSponser(sc.$index); }); } else { cell.click(); } }");
        waitForAngular(600);
        // Ensure Insurer = Self (REAL click) and backfill any empty mandatory payor select.
        Object tagged = page.evaluate("()=>{const r=[...document.querySelectorAll('input[ng-model=\"Insurer\"]')].find(x=>x.value==='1'); if(!r) return false; r.id='__payorSelf'; return true;}");
        if (Boolean.TRUE.equals(tagged)) {
            try { page.locator("#__payorSelf").check(new com.microsoft.playwright.Locator.CheckOptions().setTimeout(5000)); }
            catch (Exception e) { try { page.locator("#__payorSelf").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {} }
            page.evaluate("()=>{const e=document.getElementById('__payorSelf'); if(e) e.removeAttribute('id');}");
        }
        page.evaluate("() => { const setSelIfEmpty=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const cur=((e.options[e.selectedIndex]||{}).text||'').trim(); if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return;"
                + " let i=[...e.options].findIndex(o=>(o.text||'').trim().toLowerCase()===txt.toLowerCase()); if(i<1) i=[...e.options].findIndex((o,ix)=>ix>0 && !/select/i.test(o.text||'')); if(i<1) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} };"
                + " setSelIfEmpty('Registration.receivabletypeid','Self'); setSelIfEmpty('Registration.PayerTypeId','Self'); }");
        waitForAngular(400);
        // Judge on the payor FORM, not on the Insurer radio. The radio alone used to be accepted as a fallback,
        // which reported "Payor = Self" as a PASS on KS while Payor Mode, Payor Code, Priority and Pricing Policy
        // were all still empty — the step passed and Save failed three steps later with no explanation.
        Object state = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const val=ng=>{ const e=[...document.querySelectorAll('select,input')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + "   if(!e) return ''; if(e.tagName!=='SELECT') return norm(e.value);"
                + "   const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " return JSON.stringify({mode:val('Registration.receivabletypeid'), code:val('Registration.receivabletypecode'),"
                + "   name:val('Registration.receivablename'), policy:val('Registration.tariffid'), priority:val('Registration.Priority')}); }");
        System.out.println("selectPayorSelf: payor form => " + state);
        lastPayorState = state == null ? "" : state.toString();
        return !group(lastPayorState, "\"mode\":\"([^\"]*)\"").isEmpty()
                && !group(lastPayorState, "\"policy\":\"([^\"]*)\"").isEmpty();
    }

    /**
     * Visit Information, for a screen that REQUIRES Sub Department.
     *
     * <p>Use this from the Appointment List → VisitScreen path: that screen rejects the save with
     * "Please Select SubDepartment !", but its label is not starred, so the mandatory-detection below cannot see
     * it and the fallback would clear the field. Saying so explicitly beats guessing from the URL or the markup.</p>
     */
    public void fillVisitInformationRequiringSubDept() {
        subDeptRequired = true;
        try { fillVisitInformation(); } finally { subDeptRequired = false; }
    }

    /** Set by {@link #fillVisitInformationRequiringSubDept()} — never clear Sub Department on this pass. */
    private boolean subDeptRequired = false;

    /** Visit Information — Source, Encounter, Department + Doctor, Queue No (on the Save button scope), Cash. */
    public void fillVisitInformation() {
        // Open the "Visit Information" accordion header FIRST — the exact control is //*[@id="headingFour"]/a
        // (same pattern as openNokAndClickGrid()'s "headingTwo" for Patient Information). Its fields sit in the DOM
        // while the panel is collapsed, so filling them without expanding it first can silently miss real clicks.
        try {
            page.locator("//*[@id='headingFour']/a").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
        } catch (Exception e) {
            // Fallback: click any element whose text is exactly "Visit Information".
            page.evaluate("() => { const h=[...document.querySelectorAll('a,button,div,h3,h4,span,.panel-heading,.panel-title,.box-header')].find(x=>/^\\s*visit information\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(h) h.click(); }");
        }
        waitForAngular(600);
        setSelTxt("Visit.PatientSourceID", "External");
        setSelTxt("Visit.EncounterTypeID", "Outpatient");
        setSelTxt("Visit.DepartmentID", "Cardiology");
        setSelTxt("Registration.GroupCategoryId", "Cardiology");
        waitForAngular(800); // doctor list can reload after the department is set
        // Sub Department is filtered BY Department, so it must be set AFTER the department cascade has reloaded it.
        // Emergency Registration (Conscious) makes it mandatory — Save is blocked with "Please Select SubDepartment !"
        // — while OP Registration does not show it, hence the if-present treatment.
        selectSubDepartmentAndDoctor();
        // Still no Doctor? Try a DIFFERENT DEPARTMENT — some departments have no doctors attached at all, and no
        // sub-department under them can produce one. Walking departments is the only way out.
        if (currentDoctor().isEmpty()) ensureDoctorByChangingDepartment();
        selectCaseCategoryIfPresent();
        // Run LAST: the sub-department / department cascades above can clear Visit Type on their way through
        // (Telemedicine Registration saved with "Please Select Visit Type!" once the fallback re-fired Department).
        // The doctor auto-picker always falls back to the FIRST real option ("Allen R" is rarely present) — which
        // is consistently the same doctor (e.g. "AJAY PAYABLE") across many departments, and confirmed live to have
        // NO Visit Types configured at all: Visit Type then never resolves no matter how long we wait. Retrying
        // with a DIFFERENT doctor is the actual fix — waiting longer for the same doctor never helps.
        if (!ensureVisitTypeIfPresent() && !tryOtherDoctorsForVisitType()) tryOtherCashCountersForVisitType();
        // ...and then re-check Sub Department, because everything above can leave it empty on a slow environment.
        reassertSubDepartmentIfRequired();
        // Department itself gets no such re-check anywhere above — confirmed live (Emergency Registration
        // Conscious, foreign patient): the initial setSelTxt() call ran before its list had loaded (0/placeholder
        // options at that moment, so even the "pick the first real option" fallback had nothing to pick), and by
        // the time Sub Department/Doctor/Visit Type had all resolved correctly, nothing went back to select
        // Department from its now-populated (if short) list. Save did not actually block on it, but the pre-Save
        // verification correctly flagged the gap — close it the same way Sub Department is re-checked above.
        if (selectedText("Visit.DepartmentID").isEmpty()) {
            setSelTxt("Visit.DepartmentID", "Cardiology");
        }
        page.evaluate("() => { const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDRegistration();' && x.offsetParent!==null);"
                + " let s = b ? angular.element(b).scope() : angular.element(document.querySelector('#txtFirstName')).scope();"
                + " s.$apply(function(){ let x=s; for(let i=0;i<10&&x;i++){ if(x.Visit){ x.Visit.TokenNo='1'; break; } x=x.$parent; } }); }");
        page.evaluate("()=>{const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='Visit.IsCashPayment');if(c&&!c.checked)c.click();}");
    }
    /**
     * Set <b>Sub Department</b> and <b>Doctor</b> together, because they are coupled.
     *
     * <p>Sub Department is present on BOTH OP Registration and Emergency Registration (Conscious), but only
     * Emergency makes it mandatory (Save answers <i>"Please Select SubDepartment !"</i>). Selecting one RELOADS the
     * doctor list, and some sub-departments have <b>no doctors at all</b> — on OP, "Sub Cardio Unit" empties the
     * list, so a naive "pick the first sub-department" broke Save with <i>"Please Select Doctor!"</i>.</p>
     *
     * <p>So: walk the sub-department options and keep the first one that still leaves a selectable doctor, then set
     * the doctor from the filtered list (preferring "Allen R", else whatever remains). If no sub-department yields a
     * doctor, leave Sub Department unset and just pick a doctor — that is the pre-existing OP behaviour. Both values
     * go through {@link #setSelTxt(String, String)} so they land in the snapshot the Save re-apply uses.</p>
     */
    /**
     * Move the <b>Department</b> until its <b>Sub Department</b> list is non-empty, and return that list.
     *
     * <p>Only needed where Sub Department is mandatory but the department in use has none — KS's Cardiology. The
     * walk is capped ({@code -Ddept.walk.max}, default 12) so a screen with dozens of departments cannot turn one
     * save into a several-minute crawl.</p>
     *
     * @param subDeptModel ng-model of the Sub Department select
     * @return the sub-departments found, empty when no department yielded any
     */
    private java.util.List<?> walkDepartmentsForSubDept(String subDeptModel) {
        int max = Integer.getInteger("dept.walk.max", 12);
        // The Department list itself hangs off ENCOUNTER TYPE. Under "Outpatient" KS offers only two departments
        // (MEDICAL OFFICER, NUCLEAR MEDICINE) and neither has a sub-department; under "ALLIED HEALTH" the list is
        // long and Sub Dept is populated. So walking departments alone can never find one — walk the encounter
        // types too, current selection first so a working combination is not disturbed.
        java.util.List<Object> encounters = new java.util.ArrayList<>();
        encounters.add(selectedText("Visit.EncounterTypeID"));
        // Read the list only once it has stopped GROWING. KS delivers these masters late — the same slowness that
        // reverts the Nationality selection — so an immediate read saw a single "Outpatient" entry while the
        // screen in a browser offers ALLIED HEALTH and the rest.
        awaitStableOptions("Visit.EncounterTypeID");
        Object encOpts = optionsOf("Visit.EncounterTypeID");
        if (encOpts instanceof java.util.List) for (Object e : (java.util.List<?>) encOpts) if (!encounters.contains(e)) encounters.add(e);

        int tried = 0;
        java.util.List<Object> deptsSeen = new java.util.ArrayList<>();
        for (Object enc : encounters) {
            String encounter = String.valueOf(enc);
            if (!encounter.isEmpty()) { setSelTxt("Visit.EncounterTypeID", encounter); waitForAngular(1800); }
            awaitStableOptions("Visit.DepartmentID");
            Object deptOpts = optionsOf("Visit.DepartmentID");
            java.util.List<?> depts = (deptOpts instanceof java.util.List) ? (java.util.List<?>) deptOpts : java.util.Collections.emptyList();
            for (Object d : depts) {
                if (++tried > max) break;
                String dept = String.valueOf(d);
                if (!deptsSeen.contains(dept)) deptsSeen.add(dept);
                setSelTxt("Visit.DepartmentID", dept);
                waitForAngular(1800);
                Object subs = optionsOf(subDeptModel);
                java.util.List<?> list = (subs instanceof java.util.List) ? (java.util.List<?>) subs : java.util.Collections.emptyList();
                if (!list.isEmpty()) {
                    System.out.println("walkDepartmentsForSubDept: Encounter=" + encounter + " Department=" + dept
                            + " has " + list.size() + " sub-department(s)");
                    return list;
                }
            }
            if (tried > max) break;
        }
        lastSubDeptDiagnosis = "Sub Dept has no value to offer: tried " + tried + " department(s) across "
                + encounters.size() + " encounter type(s) (" + deptsSeen + ") and none has a sub-department configured";
        System.out.println("walkDepartmentsForSubDept: " + lastSubDeptDiagnosis);
        return java.util.Collections.emptyList();
    }

    /**
     * Block until a select's option count stops changing (two equal reads ~1.5s apart), up to 25s.
     * A list read while it is still filling makes a walk conclude "there is nothing here" far too early.
     */
    private void awaitStableOptions(String ngModel) {
        try {
            page.evaluate("(ng) => { window.__stab=window.__stab||{}; delete window.__stab[ng]; }", ngModel);
            page.waitForFunction("(ng) => { const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                            + " if(!e) return true; const n=e.options.length;"
                            + " window.__stab=window.__stab||{}; const prev=window.__stab[ng]; window.__stab[ng]=n;"
                            + " return prev!==undefined && prev===n && n>1; }",
                    ngModel, new Page.WaitForFunctionOptions().setTimeout(25000).setPollingInterval(1500));
        } catch (Exception ignore) {
            System.out.println("awaitStableOptions: " + ngModel + " never settled with options — proceeding");
        }
    }

    /** The real (non-placeholder) option labels of a select, by ng-model. */
    private Object optionsOf(String ngModel) {
        return page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + " if(!e) return []; return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))"
                + "   .map(o=>norm(o.textContent)); }", ngModel);
    }

    private void selectSubDepartmentAndDoctor() {
        // Locate the Sub Department select and list its real options (polling — it loads off Department).
        Object info = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\s+/g,' ').trim(); const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const labelOf=s=>{ let t=''; if(s.id){ const l=document.querySelector('label[for=\"'+s.id+'\"]'); if(l) t+=' '+l.textContent; }"
                + "   const g=s.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t+=' '+l.textContent; } return norm(t).toLowerCase(); };"
                + " const real=e=>[...e.options].filter(o=>o.value && !/^-*\s*select\s*-*$/i.test(norm(o.textContent))).map(o=>norm(o.textContent));"
                + " let sel=null;"
                + " for(let k=0;k<20;k++){ sel=[...document.querySelectorAll('select')].find(s=>/sub.?depart/i.test(s.getAttribute('ng-model')||''))"
                + "     || [...document.querySelectorAll('select')].find(s=>/sub\s*depart/.test(labelOf(s)));"
                + "   if(sel && real(sel).length) break; await sleep(500); }"
                + " if(!sel) return resolve({found:false});"
                + " resolve({found:true, ng:(sel.getAttribute('ng-model')||''), options:real(sel)}); })");
        java.util.Map<?, ?> m = (info instanceof java.util.Map) ? (java.util.Map<?, ?>) info : java.util.Collections.emptyMap();

        if (!Boolean.TRUE.equals(m.get("found"))) {
            // No Sub Department on this screen — original behaviour.
            setSelTxt("Visit.DoctorID", "Allen R");
            System.out.println("selectSubDepartmentAndDoctor: no Sub Department field; doctor set directly");
            return;
        }
        String model = String.valueOf(m.get("ng"));
        java.util.List<?> opts = (m.get("options") instanceof java.util.List)
                ? (java.util.List<?>) m.get("options") : java.util.Collections.emptyList();

        // KS: the default department (Cardiology) has NO sub-departments at all, and this build makes Sub Dept
        // mandatory — so there was nothing to keep and nothing to clear, and every save was blocked on a field the
        // screen offered no value for. Walk the DEPARTMENTS until one actually has sub-departments.
        if (opts.isEmpty()) {
            java.util.List<?> found = walkDepartmentsForSubDept(model);
            if (!found.isEmpty()) opts = found;
        }

        for (Object o : opts) {
            String subDept = String.valueOf(o);
            setSelTxt(model, subDept);
            waitForAngular(1500);                     // the doctor list reloads off the sub-department
            setSelTxt("Visit.DoctorID", "Allen R");   // falls back to the first real doctor if Allen R is filtered out
            String doctor = currentDoctor();
            if (!doctor.isEmpty()) {
                System.out.println("selectSubDepartmentAndDoctor: " + model + "=" + subDept + " | Doctor=" + doctor);
                return;
            }
            System.out.println("selectSubDepartmentAndDoctor: '" + subDept + "' has no doctors — trying the next one");
        }

        // No sub-department leaves a doctor. What to do now DIFFERS by screen:
        //  • Emergency Registration (Conscious) REQUIRES Sub Department ("Please Select SubDepartment !") and saves
        //    happily without a doctor — so keep the first sub-department and stop. Clearing it here also re-fires the
        //    department cascade, which wipes other Visit fields and produced "Please Select Visit Type!".
        //  • OP Registration requires a Doctor and does not need Sub Department — so clear it and restore the doctor.
        //
        // Decide by whether Sub Department is MANDATORY on the screen in front of us, NOT by the URL. The URL test
        // (contains "emergencyregistration") silently took the OP branch on the VisitScreen reached from the
        // Appointment List — clearing a Sub Department that screen requires, so every save was rejected with
        // "Please Select SubDepartment !". A starred label / required attribute is the real signal.
        boolean needsSubDept = subDeptRequired || Boolean.TRUE.equals(page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return false;"
                + " if(e.hasAttribute('required')||e.hasAttribute('ng-required')) return true;"
                + " let n=e.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div');"
                + " for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label');"
                // "Sub Dept*" on KS, "Sub Department*" elsewhere — matching only the long spelling made a starred,
                // mandatory field look optional, so it was cleared and every save was blocked on it.
                + "   if(l && /sub\\s*dep(t|art)/i.test(norm(l.textContent))) return /\\*/.test(l.textContent) || !!l.querySelector('.arstrik');"
                + "   n=n.parentElement; } return false; }", model))
                || page.url().toLowerCase().contains("emergencyregistration");
        if (needsSubDept && !opts.isEmpty()) {
            String first = String.valueOf(opts.get(0));
            setSelTxt(model, first);
            waitForAngular(1500);
            setSelTxt("Visit.DoctorID", "Allen R");   // best effort; this screen saves without one
            System.out.println("selectSubDepartmentAndDoctor: Sub Department is mandatory here — kept " + first
                    + " | Doctor=" + currentDoctor());
            return;
        }

        // OP: clear Sub Department and fall back to a doctor-only selection (pre-existing behaviour).
        // Clearing alone is NOT enough — the doctor list stays empty until the DEPARTMENT cascade is re-triggered,
        // so re-select the department to repopulate the doctors before picking one.
        page.evaluate("(ng) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                + " e.selectedIndex=0; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} }", model);
        waitForAngular(800);
        setSelTxt("Visit.DepartmentID", "Cardiology");   // re-fires the cascade that fills the doctor list
        waitForAngular(1800);
        setSelTxt("Visit.DoctorID", "Allen R");
        if (currentDoctor().isEmpty()) { waitForAngular(1500); setSelTxt("Visit.DoctorID", ""); }
        System.out.println("selectSubDepartmentAndDoctor: no sub-department left a doctor — cleared it; Doctor=" + currentDoctor());
    }

    /**
     * Satisfy the dropdown that a blocking toast NAMES — <i>"Please Select Admission Source!"</i> → set Admission
     * Source, and so on.
     *
     * <p>DSH enforces mandatory fields that devhis/KLG do not, and it leaves their labels UNSTARRED, so neither
     * {@code describeEmptyMandatory()} nor a required-attribute check can find them. It also validates in order and
     * names only the FIRST offender, so each one costs a whole run to discover: Sub Department, then Admission
     * Source, then whatever is next. The message itself is the one reliable signal — so match the field by the name
     * in the toast (against its ng-model, else its label) and select the first real option.</p>
     *
     * <p>Only SELECTs are touched. A "Please Enter …" text field is left alone, because inventing a value for an
     * unknown field is how a test starts writing nonsense into the system under test.</p>
     *
     * @return what was set, for the log ("" when nothing matched)
     */
    private String satisfyFieldNamedInToast(String toast) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)please\\s+select\\s+(.+?)\\s*[!.]*\\s*$").matcher(toast.trim());
        if (!m.find()) return "";
        String wanted = m.group(1).trim();
        if (wanted.isEmpty()) return "";

        Object r = page.evaluate("(name) => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const want=norm(name); if(!want) return '';"
                + " const A=window.angular; const $=window.jQuery;"
                + " const txt=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=txt(l.textContent); }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p&&h++<4&&!t){ const l=p.querySelector('label,.control-label'); if(l) t=txt(l.textContent); p=p.parentElement; } }"
                + "   return t; };"
                + " const sels=[...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null);"
                // ng-model first (Visit.AdmissionSourceID -> admissionsourceid contains admissionsource), then label.
                + " let e=sels.find(s=>norm(s.getAttribute('ng-model')||'').indexOf(want)>=0)"
                + "      || sels.find(s=>norm(labelOf(s)).indexOf(want)>=0); if(!e) return '';"
                + " const real=[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(txt(o.textContent)));"
                + " if(!real.length) return JSON.stringify({ng:(e.getAttribute('ng-model')||''), value:'', text:'(no options)'});"
                + " const cur=e.options[e.selectedIndex]; const curT=cur?txt(cur.textContent):'';"
                + " if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(curT))"
                + "   return JSON.stringify({ng:(e.getAttribute('ng-model')||''), value:cur.value, text:curT+'(already)'});"
                + " e.value=real[0].value; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " return JSON.stringify({ng:(e.getAttribute('ng-model')||''), value:real[0].value, text:txt(real[0].textContent)}); }",
                wanted);
        String s = r == null ? "" : r.toString();
        if (s.isEmpty()) return "";
        // Snapshot it so the Save re-apply on the next attempt keeps the value.
        java.util.regex.Matcher ng = java.util.regex.Pattern.compile("\"ng\":\"([^\"]*)\"").matcher(s);
        java.util.regex.Matcher vl = java.util.regex.Pattern.compile("\"value\":\"([^\"]*)\"").matcher(s);
        if (ng.find() && vl.find() && !ng.group(1).isEmpty() && !vl.group(1).isEmpty()) snap(ng.group(1), vl.group(1));
        waitForAngular(600);
        return wanted + " -> " + s;
    }

    /**
     * List every STARRED (mandatory) or {@code required}/{@code ng-required} field on the WHOLE visible page that
     * is still empty. Only useful when Save rejects with the nameless <i>"Please fill in all the mandatory
     * fields!"</i> — a different message from the "Please Select X!" family that {@link #satisfyFieldNamedInToast}
     * handles, and one that names nothing, so without this the actual offending field is invisible in the log.
     */
    private String emptyStarredFieldsWholePage() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let t='';"
                + "   if(e.id){ const l=document.querySelector('label[for=\"'+e.id+'\"]'); if(l) t=norm(l.textContent); }"
                + "   if(!t){ let p=e.parentElement,h=0; while(p&&h++<5&&!t){ const l=p.querySelector('label,.control-label'); if(l) t=norm(l.textContent); p=p.parentElement; } }"
                + "   return t; };"
                + " const out=[];"
                + " document.querySelectorAll('input,select,textarea').forEach(e=>{ if(e.offsetParent===null) return;"
                + "   const lb=labelOf(e); if(!/\\*/.test(lb) && !e.hasAttribute('required') && !e.hasAttribute('ng-required')) return;"
                + "   let empty;"
                + "   if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; empty=!o||!o.value||/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)); }"
                + "   else if(e.type==='checkbox'||e.type==='radio'){ return; }"
                + "   else { empty=!(e.value||'').trim(); }"
                + "   if(empty) out.push((lb||'(no label)').replace(/\\s*\\*+\\s*$/,'')+'['+(e.getAttribute('ng-model')||e.id||'?')+']'); });"
                + " return out.length? out.slice(0,20).join(', ') : '(none found empty)'; }");
        return r == null ? "" : r.toString();
    }

    /**
     * Last line of defence for <b>Sub Department</b> — re-select it if the screen requires it and it is empty.
     *
     * <p>{@link #selectSubDepartmentAndDoctor()} runs early, and two things can still leave the field blank by the
     * time Save is clicked, both seen on DSH as <i>"Please Select SubDepartment !"</i> while the fill step reported
     * success:</p>
     * <ul>
     *   <li>the option list had not loaded inside that method's poll (DSH answers slower than devhis/KLG), so the
     *       walk saw an empty list and the fallback <b>cleared</b> the field; or</li>
     *   <li>a later cascade — {@code ensureDoctorByChangingDepartment()} / {@code ensureVisitTypeIfPresent()} —
     *       re-fired Department, which reloads Sub Department and drops the selection.</li>
     * </ul>
     *
     * <p>So this runs at the very END of {@link #fillVisitInformation()}: if the field is already set it does
     * nothing; otherwise it waits for the list (re-firing the Department cascade once to force a reload), picks the
     * first real option through {@link #setSelTxt(String, String)} so the value lands in the snapshot the Save
     * re-apply uses, and reads the bound model back — Angular silently discards a value that is not in the list.</p>
     */
    private void reassertSubDepartmentIfRequired() {
        Object info = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const sub=()=>[...document.querySelectorAll('select')].find(s=>/sub.?depart/i.test(s.getAttribute('ng-model')||''));"
                + " const real=e=>e?[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))):[];"
                + " const chosen=e=>{ const o=e&&e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " let e=sub(); if(!e) return resolve({found:false});"
                + " const ng=e.getAttribute('ng-model')||'';"
                + " if(chosen(e)) return resolve({found:true, ng, current:chosen(e)});"
                // Empty. Wait for the list; halfway through, re-fire Department to force the cascade to reload it.
                + " for(let k=0;k<12;k++){ e=sub(); if(e && real(e).length) break;"
                + "   if(k===5){ const d=[...document.querySelectorAll('select')].find(s=>s.getAttribute('ng-model')==='Visit.DepartmentID');"
                + "     if(d){ d.dispatchEvent(new Event('change',{bubbles:true}));"
                + "       try{A.element(d).triggerHandler('change');}catch(x){} if($){try{$(d).trigger('change');}catch(x){}} } }"
                + "   await sleep(700); }"
                + " e=sub(); const rs=real(e);"
                + " resolve({found:true, ng, current:'', first: rs.length?norm(rs[0].textContent):''}); })");
        java.util.Map<?, ?> m = (info instanceof java.util.Map) ? (java.util.Map<?, ?>) info : java.util.Collections.emptyMap();
        if (!Boolean.TRUE.equals(m.get("found"))) return;

        String model = String.valueOf(m.get("ng"));
        String current = m.get("current") == null ? "" : String.valueOf(m.get("current"));
        if (!current.isEmpty()) return;                                  // already set — leave it alone

        // Only force it where the screen actually demands it; on OP an unset Sub Department is legitimate and
        // setting one here would re-filter the doctor list that selectSubDepartmentAndDoctor() just settled.
        boolean needs = subDeptRequired || Boolean.TRUE.equals(page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return false;"
                + " if(e.hasAttribute('required')||e.hasAttribute('ng-required')) return true;"
                + " let n=e.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div');"
                + " for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label');"
                // "Sub Dept*" on KS, "Sub Department*" elsewhere — matching only the long spelling made a starred,
                // mandatory field look optional, so it was cleared and every save was blocked on it.
                + "   if(l && /sub\\s*dep(t|art)/i.test(norm(l.textContent))) return /\\*/.test(l.textContent) || !!l.querySelector('.arstrik');"
                + "   n=n.parentElement; } return false; }", model))
                || page.url().toLowerCase().contains("emergencyregistration");
        if (!needs) return;

        String first = m.get("first") == null ? "" : String.valueOf(m.get("first"));
        if (first.isEmpty()) {
            System.out.println("reassertSubDepartmentIfRequired: " + model + " is EMPTY and no options loaded — Save will be blocked");
            return;
        }
        setSelTxt(model, first);
        waitForAngular(1200);
        Object back = page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return '';"
                + " const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + " return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; }", model);
        System.out.println("reassertSubDepartmentIfRequired: was empty — re-selected " + model + "="
                + (back == null ? "" : back) + " (wanted " + first + ")");
    }

    /** The label currently selected on a {@code <select>} by ng-model — "" for a "--Select--" placeholder. */
    protected String selectedText(String ngModel) {
        Object t = page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + " if(!e) return ''; const o=e.options[e.selectedIndex]; const s=o?norm(o.textContent):'';"
                + " return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(s)) ? s : ''; }", ngModel);
        return t == null ? "" : t.toString();
    }

    /**
     * Make sure the kin's <b>address Country</b> holds a value — WITHOUT touching its nationality.
     *
     * <p>{@code Registration.KinCountryID} is where the kin LIVES; {@code Registration.NOKnationalid} is what the
     * kin IS. Selecting the nationality already derives this field (its ng-change is
     * {@code fnsetCountrywithNationalitywiseNOK}), so this only steps in when the derivation left it empty, and
     * then prefers Malaysia — the country of the patient address that "Same As Patient Address" just copied, and
     * the only one with State/City master data on these environments.</p>
     */
    private void ensureKinAddressCountry() {
        Object res = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const e=[...document.querySelectorAll('select')].find(s=>(s.getAttribute('ng-model')||'')==='Registration.KinCountryID');"
                + " if(!e) return '(no kin Country field)';"
                + " const cur=e.options[e.selectedIndex]; const curT=cur?norm(cur.textContent):'';"
                + " if(cur && cur.value && !/^-*\\s*select\\s*-*$/i.test(curT)) return 'already '+curT;"
                + " let i=[...e.options].findIndex(o=>o.value && /^malaysia$/i.test(norm(o.textContent)));"
                + " if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " if(i<0) return '(no real option)';"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " return 'set '+norm(e.options[i].textContent); }");
        waitForAngular(800);
        System.out.println("ensureKinAddressCountry: " + res);
    }

    /**
     * Make sure the next-of-kin <b>State</b> and <b>City</b> hold values before <b>Add</b>.
     *
     * <p>The kin address is normally filled by ticking "Same As Patient Address", but that does not reliably carry
     * the State or the City across, and since HIN-3720 (24/02/2026) {@code AddKinDetails} validates the kin address
     * as Country → State → City, refusing with <i>"Please Select State"</i> / <i>"Please Select City!"</i>. Both
     * lists load asynchronously from the level above, so wait for options rather than reading them once. Walks the
     * states only if the first one yields no city. No-op for a field already set or absent.</p>
     */
    private void ensureKinStateAndCity() {
        Object res = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const find=m=>[...document.querySelectorAll('select')].find(s=>(s.getAttribute('ng-model')||'')===m);"
                + " const real=e=>e?[...e.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent))):[];"
                + " const chosen=e=>{ if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t))?t:''; };"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                // The list is repopulated by the level above — poll instead of reading it once.
                + " const waitOpts=async(m,ms)=>{ const t0=Date.now(); while(Date.now()-t0<ms){ const e=find(m); if(real(e).length) return e; await sleep(300);} return find(m); };"
                + " const stM='Registration.KinStateID', ciM='Registration.KinCityID';"
                + " let st=find(stM), ci=find(ciM);"
                + " if(!st && !ci) return resolve({found:false});"
                + " if(chosen(st) && chosen(ci)) return resolve({found:true, already:true, state:chosen(st), city:chosen(ci)});"
                + " if(!chosen(st)){ st=await waitOpts(stM,10000); }"
                + " const states=real(st);"
                + " if(!states.length) return resolve({found:true, state:chosen(st), city:chosen(ci), noStates:true});"
                // Try each state until one yields a real city (most have one; a few are empty).
                + " for(const s of (chosen(st)? [{o:st.options[st.selectedIndex], i:st.selectedIndex}].concat(states) : states)){"
                + "   if(st.selectedIndex!==s.i){ st.selectedIndex=s.i; fire(st); }"
                + "   ci=await waitOpts(ciM,6000); const cities=real(ci);"
                + "   if(cities.length){ if(!chosen(ci)){ ci.selectedIndex=cities[0].i; fire(ci); await sleep(400); }"
                + "     return resolve({found:true, state:chosen(st), city:chosen(ci)}); } }"
                + " resolve({found:true, state:chosen(st), city:chosen(ci), noCities:true}); })");
        java.util.Map<?, ?> m = (res instanceof java.util.Map) ? (java.util.Map<?, ?>) res : java.util.Collections.emptyMap();
        if (!Boolean.TRUE.equals(m.get("found"))) { System.out.println("ensureKinStateAndCity: no kin State/City fields (skipped)"); return; }
        waitForAngular(600);
        String why = Boolean.TRUE.equals(m.get("noStates")) ? " — NO states for this country"
                : Boolean.TRUE.equals(m.get("noCities")) ? " — NO cities under any state" : "";
        System.out.println("ensureKinStateAndCity: State=" + m.get("state") + " City=" + m.get("city")
                + (Boolean.TRUE.equals(m.get("already")) ? " (already set)" : "") + why);
    }

    /** @deprecated superseded by {@link #ensureKinStateAndCity()}; kept for the subclasses that still call it. */
    @Deprecated
    private void ensureKinCity() {
        Object res = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " const find=re=>[...document.querySelectorAll('select')].find(s=>re.test(s.getAttribute('ng-model')||''));"
                + " const realIdx=e=>[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " const chosen=e=>{ if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):''; return (t && !/^-*\\s*select\\s*-*$/i.test(t))?t:''; };"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                + " const city=find(/kin.*city/i); if(!city) return resolve({found:false});"
                + " if(chosen(city)) return resolve({found:true, already:true, city:chosen(city)});"
                + " const st=find(/kin.*state/i);"
                + " if(st){ const states=[...st.options].map((o,i)=>({o,i})).filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)));"
                + "   for(const s of states){ st.selectedIndex=s.i; fire(st); await sleep(1200);"
                + "     const c=find(/kin.*city/i); if(c){ const ci=realIdx(c); if(ci>=0){ c.selectedIndex=ci; fire(c); await sleep(600);"
                + "       if(chosen(c)) return resolve({found:true, state:norm(s.o.textContent), city:chosen(c)}); } } } }"
                + " const ci=realIdx(city); if(ci>=0){ city.selectedIndex=ci; fire(city); await sleep(400); return resolve({found:true, city:chosen(city)}); }"
                + " resolve({found:true, city:''}); })");
        java.util.Map<?, ?> m = (res instanceof java.util.Map) ? (java.util.Map<?, ?>) res : java.util.Collections.emptyMap();
        if (!Boolean.TRUE.equals(m.get("found"))) { System.out.println("ensureKinCity: no kin City field (skipped)"); return; }
        if (Boolean.TRUE.equals(m.get("already"))) { System.out.println("ensureKinCity: already set -> " + m.get("city")); return; }
        System.out.println("ensureKinCity: State=" + m.get("state") + " City=" + m.get("city"));
    }

    /**
     * LAST-DITCH <b>Visit Type</b> guard, to run immediately BEFORE any {@code IUDRegistration()} save.
     *
     * <p>Every screen that saves the VisitScreen hits the same failure: {@link #ensureVisitTypeIfPresent()} can
     * fail to resolve a value (Angular's {@code ngModel} controller reports null even with a real option
     * selected), the value is therefore never snapshotted, and Save answers <i>"Please Select Visit Type!"</i>.
     * This picks the first real option and pushes it straight onto every {@code Visit} scope, so the validator
     * sees a value regardless of what the ngModel pipeline did.</p>
     *
     * <p><b>Shared on purpose.</b> The VisitScreen is reused by OP Registration, Telemedicine Registration,
     * Emergency Registration (conscious + unconscious) and the Appointment List's Registration action. The thin
     * subclasses inherit this automatically; the screens with their OWN save must splice this snippet in (or call
     * {@link #ensureVisitTypeSelected()}) so a fix here reaches all of them. Expects a JS variable {@code seenV}
     * (a Set of Visit scopes) to be in scope; {@link #ensureVisitTypeSelected()} supplies its own.</p>
     */
    /**
     * Visit Type values that must never be treated as a real selection. DSH rejects the first option in its list
     * with <i>"Cross Consultation Visit Type Not Allowed For this Patient!"</i>, so picking "the first real option"
     * blindly either gets the save rejected or leaves Visit Type unset on the retry — skipped when CHOOSING a
     * value. "No Information" is a different trap: it is DSH's pre-selected DEFAULT, reads as a normal option (not
     * a "--Select--" placeholder), and so was treated as "already set" — but Save still rejects it as empty
     * ("Please fill in all the mandatory fields!", pointing at a red-outlined Visit Type still reading "No
     * Information"). Also excluded when judging whether the field is already satisfied.
     */
    private static final String VISIT_TYPE_SKIP_JS = "/cross\\s*consult|^no\\s*information$/i";

    public static final String VISIT_TYPE_GUARD_JS =
            " try{ const vt=[...document.querySelectorAll('select')].find(s=>/visit.?type/i.test(s.getAttribute('ng-model')||''));"
            + "   if(vt){ const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const cur=vt.options[vt.selectedIndex];"
            + "     const skipRe=" + VISIT_TYPE_SKIP_JS + ";"
            + "     const empty=!cur || !cur.value || /^-*\\s*select\\s*-*$/i.test(norm(cur.textContent)) || skipRe.test(norm(cur.textContent));"
            // Prefer "New" (a plain visit — what a fresh registration actually is) over just the first real option.
            + "     if(empty){ let i=[...vt.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()==='new');"
            + "       if(i<0) i=[...vt.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && !skipRe.test(norm(o.textContent)));"
            + "       if(i<0) i=[...vt.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
            + "       if(i>=0){ vt.selectedIndex=i; vt.dispatchEvent(new Event('change',{bubbles:true}));"
            + "         try{ angular.element(vt).triggerHandler('change'); }catch(e){}"
            + "         const raw=(vt.options[i].value||'').replace(/^(number|string):/,'');"
            + "         const val=/^\\d+$/.test(raw)?Number(raw):raw;"
            + "         seenV.forEach(v2=>{ v2.VisitTypeID=val; }); } } } }catch(e){}";

    /**
     * Standalone form of {@link #VISIT_TYPE_GUARD_JS} for screens that own their save: call this right before
     * invoking {@code IUDRegistration()}. Collects the {@code Visit} scopes itself. Never throws.
     *
     * @return what it did, for logging
     */
    /**
     * Move Visit Type to a DIFFERENT option — for when the save refuses the current one
     * (<i>"Cross Consultation Visit Type Not Allowed For this Patient!"</i> on DSH). Skips the current selection
     * and anything matching the skip list; returns the newly chosen label, or "" when there is nothing else.
     */
    public String selectDifferentVisitType() {
        try {
            Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                    + " const vt=[...document.querySelectorAll('select')].find(s=>/visit.?type/i.test(s.getAttribute('ng-model')||''));"
                    + " if(!vt) return ''; const skipRe=" + VISIT_TYPE_SKIP_JS + "; const cur=vt.selectedIndex;"
                    + " const cand=[...vt.options].map((o,i)=>({o,i})).filter(x=>x.i!==cur && x.o.value"
                    + "   && !/^-*\\s*select\\s*-*$/i.test(norm(x.o.textContent)) && !skipRe.test(norm(x.o.textContent)));"
                    + " if(!cand.length) return '';"
                    + " const t=cand[0]; vt.selectedIndex=t.i; vt.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{A.element(vt).triggerHandler('change');}catch(e){} if($){try{$(vt).trigger('change');}catch(e){}}"
                    + " const raw=(t.o.value||'').replace(/^(number|string):/,''); const val=/^\\d+$/.test(raw)?Number(raw):raw;"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ let x=A.element(el).scope();"
                    + "   for(let k=0;k<15&&x;k++){ if(x.Visit && typeof x.Visit==='object') x.Visit.VisitTypeID=val; x=x.$parent; } }catch(e){} });"
                    + " return norm(t.o.textContent); }");
            String s = r == null ? "" : r.toString().trim();
            System.out.println("selectDifferentVisitType: " + (s.isEmpty() ? "(no other Visit Type available)" : s));
            waitForAngular(500);
            return s;
        } catch (Exception e) {
            System.out.println("selectDifferentVisitType: " + e.getMessage());
            return "";
        }
    }

    public String ensureVisitTypeSelected() {
        try {
            Object r = page.evaluate("() => { const seenV=new Set();"
                    + " document.querySelectorAll('*').forEach(el=>{ try{ let x=angular.element(el).scope();"
                    + "   for(let i=0;i<15&&x;i++){ if(x.Visit && typeof x.Visit==='object') seenV.add(x.Visit); x=x.$parent; } }catch(e){} });"
                    + VISIT_TYPE_GUARD_JS
                    + " const vt=[...document.querySelectorAll('select')].find(s=>/visit.?type/i.test(s.getAttribute('ng-model')||''));"
                    + " if(!vt) return '(no Visit Type field)';"
                    + " const o=vt.options[vt.selectedIndex]; return 'VisitType=' + (o?(o.textContent||'').trim():'(none)'); }");
            String s = r == null ? "" : r.toString();
            System.out.println("ensureVisitTypeSelected: " + s);
            return s;
        } catch (Exception e) {
            System.out.println("ensureVisitTypeSelected: " + e.getMessage());
            return "";
        }
    }

    /**
     * Ensure <b>Visit Type</b> still holds a value, re-selecting it if a cascade cleared it.
     *
     * <p>Called at the very end of {@link #fillVisitInformation()}: selecting a Sub Department — and, in the OP
     * fallback, re-selecting the Department to repopulate the doctors — can reset Visit Type, and Save then answers
     * <i>"Please Select Visit Type!"</i>. Snapshotted through {@link #setSelTxt(String, String)}, and a no-op where
     * the field is absent or already set.</p>
     */
    /** @return true if Visit Type is already set, or was just resolved; false if it could not be resolved. */
    private boolean ensureVisitTypeIfPresent() {
        Object res = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=s=>{ let t=''; if(s.id){ const l=document.querySelector('label[for=\"'+s.id+'\"]'); if(l) t+=' '+l.textContent; }"
                + "   const g=s.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t+=' '+l.textContent; } return norm(t).toLowerCase(); };"
                + " const sel=[...document.querySelectorAll('select')].find(s=>/visit.?type/i.test(s.getAttribute('ng-model')||''))"
                + "   || [...document.querySelectorAll('select')].find(s=>/visit\\s*type/.test(labelOf(s)));"
                + " if(!sel) return {found:false};"
                + " const skipRe=" + VISIT_TYPE_SKIP_JS + ";"
                + " const o=sel.options[sel.selectedIndex]; const cur=o?norm(o.textContent):'';"
                + " const set=(cur && !/^-*\\s*select\\s*-*$/i.test(cur) && !skipRe.test(cur));"
                + " return {found:true, ng:(sel.getAttribute('ng-model')||''), set:set, value:cur}; }");
        java.util.Map<?, ?> m = (res instanceof java.util.Map) ? (java.util.Map<?, ?>) res : java.util.Collections.emptyMap();
        if (!Boolean.TRUE.equals(m.get("found"))) { System.out.println("ensureVisitType: field not present (skipped)"); return true; }
        if (Boolean.TRUE.equals(m.get("set"))) { System.out.println("ensureVisitType: already set -> " + m.get("value")); return true; }
        String model = String.valueOf(m.get("ng"));
        if (model.isEmpty()) { System.out.println("ensureVisitType: no ng-model; left as-is"); return true; }
        // Select and then READ THE MODEL BACK after it settles. setSelTxt only waits ~150ms and returns null if the
        // model has not updated yet; snap() drops nulls, so the value never reached the Save re-apply snapshot and
        // Save kept answering "Please Select Visit Type!".
        Object v = page.evaluate("(ng) => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(r=>setTimeout(r,ms));"
                + " let e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return resolve(null);"
                // The Visit Type list loads ASYNCHRONOUSLY (after Department/Sub Dept settle). Selecting before it
                // arrives finds no real option and bails with null — which is exactly why Save kept answering
                // "Please Select Visit Type!". WAIT for a real option first.
                + " const skipRe=" + VISIT_TYPE_SKIP_JS + ";"
                + " let i=-1;"
                + " for(let k=0;k<20;k++){ e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) break;"
                // Prefer "New" (a plain visit) first; else any Visit Type the save will accept; else any real one.
                + "   i=[...e.options].findIndex(o=>o.value && norm(o.textContent).toLowerCase()==='new');"
                + "   if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)) && !skipRe.test(norm(o.textContent)));"
                + "   if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "   if(i>=0) break; await sleep(400); }"
                + " if(!e || i<0) return resolve(null);"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " for(let k=0;k<10;k++){ await sleep(300); const c=A.element(e).controller('ngModel'); if(c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='') return resolve(c.$modelValue); }"
                // FALL BACK TO THE DOM. The controller's $modelValue can still be null here even though a real
                // option IS selected (the ngModel pipeline lags the change on this cascade). Returning null made
                // snap() drop the key, so the Save re-apply had nothing to restore and Save answered
                // "Please Select Visit Type!" — with the log reading "re-selected Visit.VisitTypeID = null".
                + " const c=A.element(e).controller('ngModel');"
                + " if(c && c.$modelValue!=null && (''+c.$modelValue).trim()!=='') return resolve(c.$modelValue);"
                + " const o=e.options[e.selectedIndex];"
                + " resolve(o && o.value ? o.value.replace(/^(number|string):/,'') : null); })", model);
        boolean resolved = v != null && !String.valueOf(v).trim().isEmpty();
        if (!resolved) {
            System.out.println("ensureVisitType: could NOT resolve a value for " + model + " — Save will re-select it");
        } else {
            snap(model, v);
            System.out.println("ensureVisitType: was empty — re-selected " + model + " = " + v);
        }
        waitForAngular(400);
        return resolved;
    }

    /**
     * Retry after {@link #ensureVisitTypeIfPresent()} failed for the current Doctor — walk the OTHER real Doctor
     * options in whichever Sub Department / Department is currently set, re-checking Visit Type after each. The
     * auto-picker always lands on the SAME doctor (the first real option, e.g. "AJAY PAYABLE"), and confirmed live
     * that doctor can have NO Visit Types configured at all — retrying with a different doctor is the fix, not
     * waiting longer for the same one. Bounded (a handful of candidates) since each costs a real cascade reload.
     */
    private boolean tryOtherDoctorsForVisitType() {
        Object opts = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Visit.DoctorID');"
                + " if(!e) return []; const cur=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))"
                + "   .map(o=>norm(o.textContent)).filter(t=>t && t!==cur); }");
        java.util.List<?> list = (opts instanceof java.util.List) ? (java.util.List<?>) opts : java.util.Collections.emptyList();
        if (list.isEmpty()) { System.out.println("tryOtherDoctorsForVisitType: no other doctor to try"); return false; }
        final int maxDoctors = Integer.getInteger("doctor.walk.max", 5);
        int tried = 0;
        for (Object o : list) {
            if (tried++ >= maxDoctors) break;
            String doctor = String.valueOf(o);
            setSelTxt("Visit.DoctorID", doctor);
            waitForAngular(1200);   // Visit Type list reloads off the doctor
            if (ensureVisitTypeIfPresent()) {
                System.out.println("tryOtherDoctorsForVisitType: Doctor=" + doctor + " resolved Visit Type");
                return true;
            }
            System.out.println("tryOtherDoctorsForVisitType: Doctor=" + doctor + " also has no Visit Type — trying the next one");
        }
        System.out.println("tryOtherDoctorsForVisitType: no doctor (tried " + tried + ") resolved Visit Type");
        return false;
    }

    /**
     * Still no Visit Type after walking Doctors? Walk <b>Cash Counter</b> ({@code Visit.CashCounterID}) instead —
     * on the Appointment List → VisitScreen path the Visit Type list can be filtered by Cash Counter rather than
     * (or in addition to) Doctor, so a Cash Counter with none configured leaves Visit Type empty no matter which
     * doctor is picked. Same walk-and-recheck shape as {@link #tryOtherDoctorsForVisitType()}.
     */
    private boolean tryOtherCashCountersForVisitType() {
        Object opts = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Visit.CashCounterID');"
                + " if(!e) return []; const cur=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))"
                + "   .map(o=>norm(o.textContent)).filter(t=>t && t!==cur); }");
        java.util.List<?> list = (opts instanceof java.util.List) ? (java.util.List<?>) opts : java.util.Collections.emptyList();
        if (list.isEmpty()) { System.out.println("tryOtherCashCountersForVisitType: no other Cash Counter to try (field absent or only one option)"); return false; }
        final int maxCounters = Integer.getInteger("cashcounter.walk.max", 5);
        int tried = 0;
        for (Object o : list) {
            if (tried++ >= maxCounters) break;
            String counter = String.valueOf(o);
            setSelTxt("Visit.CashCounterID", counter);
            waitForAngular(1200);   // Visit Type list can reload off the Cash Counter
            if (ensureVisitTypeIfPresent()) {
                System.out.println("tryOtherCashCountersForVisitType: Cash Counter=" + counter + " resolved Visit Type");
                return true;
            }
            System.out.println("tryOtherCashCountersForVisitType: Cash Counter=" + counter + " also has no Visit Type — trying the next one");
        }
        System.out.println("tryOtherCashCountersForVisitType: no Cash Counter (tried " + tried + ") resolved Visit Type");
        return false;
    }

    /**
     * No doctor under the current department? Walk the DEPARTMENT list until one yields a selectable Doctor.
     *
     * <p>{@link #selectSubDepartmentAndDoctor()} only tries sub-departments beneath whichever department is set.
     * Some departments have no doctors at all, so no sub-department under them can help — the way out is to change
     * the department. Each candidate department is set, its cascade allowed to reload, and its sub-departments /
     * doctors tried; the first department that produces a doctor wins and the search stops.</p>
     */
    private void ensureDoctorByChangingDepartment() {
        Object opts = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Visit.DepartmentID');"
                + " if(!e) return []; const cur=norm((e.options[e.selectedIndex]||{}).textContent||'');"
                + " return [...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)))"
                + "   .map(o=>norm(o.textContent)).filter(t=>t!==cur); }");
        java.util.List<?> list = (opts instanceof java.util.List) ? (java.util.List<?>) opts : java.util.Collections.emptyList();
        if (list.isEmpty()) { System.out.println("ensureDoctorByChangingDepartment: no other department to try"); return; }

        // The list is ALPHABETICAL and the first ~10 entries are specialist departments with no doctors at all
        // (Anaesthesiology, Anatomical Pathology, Breast & Endocrine Surgery, Cardiothoracic, Chemical Pathology,
        // Clinical Haematology …). A cap of 8 therefore ALWAYS gave up before reaching a department that has one,
        // and the save was then rejected with "Please Select Doctor!" / "Please Select Visit Type!" (Visit Type is
        // populated by the doctor cascade, so an empty doctor list leaves Visit Type empty too).
        // Each candidate costs ~1.8s, so walking the whole list is bounded by -Ddept.walk.max (default 40).
        final int maxDepartments = Integer.getInteger("dept.walk.max", 40);
        int tried = 0;
        for (Object o : list) {
            if (tried++ >= maxDepartments) break;
            String dept = String.valueOf(o);
            setSelTxt("Visit.DepartmentID", dept);
            waitForAngular(1800);                          // department reloads sub-department + doctor
            setSelTxt("Visit.DoctorID", "Allen R");        // falls back to the first real doctor
            if (!currentDoctor().isEmpty()) {
                // Finding a doctor directly (no sub-department fallback needed) used to return immediately —
                // leaving Sub Department (and everything the app cascades off it: Encounter Type, Cash Counter,
                // Location, Registration Department, Admission Source) still holding whatever it had for the
                // OLD department this walk just abandoned. On a screen where Sub Department is mandatory
                // (subDeptRequired), select one for THIS department before returning — confirmed live this is
                // exactly what left "Sub Dept[Visit.SubDepartmentID]" (a real, correct ng-model) rejected by
                // Save even though a valid doctor had been found.
                if (subDeptRequired) selectSubDepartmentAndDoctor();
                System.out.println("ensureDoctorByChangingDepartment: Department=" + dept + " | Doctor=" + currentDoctor());
                return;
            }
            // The department alone gave no doctor — try its sub-departments before moving on.
            selectSubDepartmentAndDoctor();
            if (!currentDoctor().isEmpty()) {
                System.out.println("ensureDoctorByChangingDepartment: Department=" + dept
                        + " (via sub-department) | Doctor=" + currentDoctor());
                return;
            }
            System.out.println("ensureDoctorByChangingDepartment: '" + dept + "' has no doctors — trying the next department");
        }
        noDoctorAvailable = true;
        System.out.println("ensureDoctorByChangingDepartment: no department yielded a doctor (tried " + tried + ")");
    }

    /** True when NO department or sub-department offered a selectable doctor on the last visit fill. */
    public boolean noDoctorAvailable = false;

    /** The Doctor currently selected on the Visit form ("" when unset). */
    private String currentDoctor() {
        Object cur = page.evaluate("() => { const norm=s=>(s||'').replace(/\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Visit.DoctorID' && x.offsetParent!==null);"
                + " if(!e) return ''; const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + " return (t && !/^-*\s*select\s*-*$/i.test(t)) ? t : ''; }");
        return cur == null ? "" : cur.toString();
    }


    /**
     * DSH's Visit Information has a mandatory <b>Case Category</b> select that devhis lacks (Save is blocked by
     * "Please Select Case Category!"). Discover it by its ng-model (…casecateg…) or its "Case Category" label and
     * pick the first real option, snapshotting the value for the Save re-apply. A harmless no-op where the field is
     * absent (devhis), so it's safe to call unconditionally.
     */
    private void selectCaseCategoryIfPresent() {
        Object res = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase();"
                + " const labelOf=s=>{ let t=''; if(s.id){ const l=document.querySelector('label[for=\"'+s.id+'\"]'); if(l) t+=' '+l.textContent; }"
                + "   const g=s.closest('.form-group,.form-line,.row,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,td,div'); if(g){ const l=g.querySelector('label,.control-label'); if(l) t+=' '+l.textContent; } return norm(t); };"
                // 1) by ng-model (case category / case type variants)
                + " let sel=[...document.querySelectorAll('select')].find(s=>/case.?categ|case.?type/i.test(s.getAttribute('ng-model')||''));"
                // 2) by a 'Case Category' label near the select
                + " if(!sel){ sel=[...document.querySelectorAll('select')].find(s=>/case\\s*categ/.test(labelOf(s))); }"
                // 3) by finding the label element then its group's select (covers select2-offscreen / hidden selects)
                + " if(!sel){ const labs=[...document.querySelectorAll('label,.control-label,span,td,th')].filter(l=>/case\\s*categ/.test(norm(l.textContent)) && norm(l.textContent).length<40);"
                + "   for(const lab of labs){ const fid=lab.getAttribute('for'); if(fid){ const e=document.getElementById(fid); if(e&&e.tagName==='SELECT'){ sel=e; break; } }"
                + "     const g=lab.closest('.form-group,.row,.form-line,td,.col-sm-6,.col-md-6,.col-sm-4,.col-sm-3,div'); if(g){ const e=g.querySelector('select'); if(e){ sel=e; break; } }"
                + "     let sib=lab.nextElementSibling; for(let i=0;i<4&&sib;i++){ const e=sib.tagName==='SELECT'?sib:(sib.querySelector?sib.querySelector('select'):null); if(e){ sel=e; break; } sib=sib.nextElementSibling; } if(sel) break; } }"
                + " if(sel){ if(!sel.id) sel.id='__caseCatSel'; return {found:true, ng:(sel.getAttribute('ng-model')||''), id:sel.id}; }"
                // not found -> diagnostic dump
                + " const dump=[...document.querySelectorAll('select')].map(s=>(s.getAttribute('ng-model')||'?')+' :: '+labelOf(s).slice(0,28)).filter((v,i,a)=>a.indexOf(v)===i);"
                + " const anyText=[...document.querySelectorAll('label,.control-label,span,td,th')].filter(e=>/case\\s*categ/i.test(e.textContent||'')).map(e=>norm(e.textContent).slice(0,40)).slice(0,5);"
                + " return {found:false, selects:dump, caseCategoryTextSeen:anyText}; }");
        java.util.Map<?, ?> m = (res instanceof java.util.Map) ? (java.util.Map<?, ?>) res : java.util.Collections.emptyMap();
        if (Boolean.TRUE.equals(m.get("found"))) {
            String model = String.valueOf(m.get("ng"));
            if (model.isEmpty()) {
                // No ng-model: select the first real option by DOM id and dispatch change (validation reads value).
                page.evaluate("(id)=>{ const e=document.getElementById(id); if(!e) return; let i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} }", m.get("id"));
                System.out.println("selectCaseCategory: set Case Category (no ng-model, by id) to first real option");
            } else {
                setSelTxt(model, "");   // no text match -> first real option, snapped for the Save re-apply
                System.out.println("selectCaseCategory: set " + model + " to first real option");
            }
            return;
        }
        System.out.println("selectCaseCategory: field NOT found. caseCategoryTextSeen=" + m.get("caseCategoryTextSeen"));
        System.out.println("selectCaseCategory: VISIT SELECTS =\n  " + String.valueOf(m.get("selects")).replace(",", "\n  "));
    }

    /**
     * Next of Kin — at least one is required. The Add no-ops if a field hasn't committed, so re-fill all
     * kin fields and re-click Add on each attempt, verifying the row lands. Returns true if it landed.
     */
    /**
     * PROBE — what the app itself thinks about the kin: how many entries {@code KinDetailsList} holds, and which
     * kin FORM fields are still filled. "Please click Add to include the NOK/Guarantor details!" is raised by a
     * partially-filled kin FORM, not by an empty list, so both halves are needed to tell the two apart.
     */
    public String kinState() {
        Object r = page.evaluate("() => { const A=window.angular; let len=-1;"
                + " document.querySelectorAll('form,div[ng-controller],body').forEach(el=>{ try{ let x=A.element(el).scope();"
                + "   for(let i=0;i<15&&x;i++){ if(Array.isArray(x.KinDetailsList)){ len=Math.max(len,x.KinDetailsList.length); break; } x=x.$parent; } }catch(e){} });"
                + " const models=['Registration.KinTitleID','Registration.KinName','Registration.KinFamilyName','Registration.NOKnationalid',"
                + "   'Registration.KinNationalId','Registration.KinRelationID','Registration.KinMobileNo','Registration.KinEmail',"
                + "   'Registration.KinOccupationID','Registration.KinMobileCountryCode','Registration.KinCountryID'];"
                + " const filled=models.filter(m=>{ const e=[...document.querySelectorAll('input,select,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + "   return e && e.value && !/^\\s*$|^\\?$/.test(e.value) && e.selectedIndex!==0; }).map(m=>m.replace('Registration.',''));"
                + " return 'KinDetailsList='+len+' | kin form still filled: '+(filled.length?filled.join(','):'(empty)'); }");
        return r == null ? "" : r.toString();
    }

    /**
     * Empty the kin FORM once the kin is committed to {@code KinDetailsList}.
     *
     * <p>After Add, leftovers in the kin form make the screen treat it as a second, uncommitted kin and Save is
     * rejected with "Please click Add to include the NOK/Guarantor details!" — even though the grid shows the row.
     * Only the form is cleared; the committed list is untouched.</p>
     */
    public String clearKinFormIfCommitted() {
        Object r = page.evaluate("() => { const A=window.angular; let len=-1;"
                + " document.querySelectorAll('form,div[ng-controller],body').forEach(el=>{ try{ let x=A.element(el).scope();"
                + "   for(let i=0;i<15&&x;i++){ if(Array.isArray(x.KinDetailsList)){ len=Math.max(len,x.KinDetailsList.length); break; } x=x.$parent; } }catch(e){} });"
                + " if(len<=0) return 'not cleared (KinDetailsList='+len+')';"
                // Untick "Same As Patient Address" FIRST. While it is ticked the kin address mirrors the patient's,
                // so clearing the kin City blanked the PATIENT's too and Save answered "Please Select City!".
                + " const same=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='chkSameasPatAddr');"
                + " if(same && same.checked){ same.click(); }"
                + " const models=['Registration.KinAddress1','Registration.KinAddress2','Registration.KinCityID','Registration.KinPostalCode',"
                + "   'Registration.KinTitleID','Registration.KinName','Registration.KinFamilyName','Registration.NOKnationalid',"
                + "   'Registration.KinNationalId','Registration.KinRelationID','Registration.KinMobileNo','Registration.KinEmail',"
                + "   'Registration.KinOccupationID','Registration.KinMobileCountryCode','Registration.KinCountryID'];"
                + " const cleared=[];"
                // Only what is actually still filled — an empty field re-set fires ng-change for nothing.
                + " models.forEach(m=>{ const e=[...document.querySelectorAll('input,select,textarea')].find(x=>(x.getAttribute('ng-model')||'')===m);"
                + "   if(!e) return; const filled = e.tagName==='SELECT' ? (e.selectedIndex>0) : !!(e.value||'').trim();"
                + "   if(!filled) return; const c=A.element(e).controller('ngModel');"
                + "   if(e.tagName==='SELECT'){ e.selectedIndex=0; } else { e.value=''; }"
                + "   if(c){ c.$setViewValue(''); c.$render(); }"
                + "   e.dispatchEvent(new Event('change',{bubbles:true})); cleared.push(m.replace('Registration.','')); });"
                + " return (cleared.length? 'cleared '+cleared.join(',') : 'nothing left in the kin form')+' (KinDetailsList='+len+')'; }");
        waitForAngular(600);
        return r == null ? "" : r.toString();
    }

    /** How many rows the NOK/Guarantor grid holds (its "no records" placeholder excluded). */
    public int kinGridRowCount() {
        Object n = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/relation|guarantor|nationality/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return 0;"
                + " return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=(r.textContent||'').trim();"
                + "   return tx && !/no\\s*records|no\\s*data/i.test(tx); }).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /**
     * Wait for {@code $scope.CivilIdLength} (the length the screen demands of an NRIC) and return it, or -1.
     *
     * <p>It is loaded asynchronously. Filling the kin before it lands means the screen compares the NRIC's length
     * to {@code undefined} and rejects every value with "Please enter valid NRIC for NOK".</p>
     */
    /** Why the kin could not be added, in the app's own terms — surfaced in the step so the report explains it. */
    public String lastKinFailReason = "";

    public int awaitCivilIdLength(int timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            Object v = page.evaluate("() => { const A=window.angular; const seen=new Set(); let found=null;"
                    + " document.querySelectorAll('form,div[ng-controller],body').forEach(el=>{ try{ let x=A.element(el).scope();"
                    + "   for(let i=0;i<15&&x;i++){ if(!seen.has(x.$id)){ seen.add(x.$id);"
                    + "     if(found===null && x.CivilIdLength!==undefined && x.CivilIdLength!==null) found=x.CivilIdLength; } x=x.$parent; } }catch(e){} });"
                    + " return found; }");
            if (v instanceof Number) return ((Number) v).intValue();
            if (v != null && v.toString().matches("\\d+")) return Integer.parseInt(v.toString());
            page.waitForTimeout(500);
        }
        System.out.println("awaitCivilIdLength: CivilIdLength never appeared — the kin NRIC length cannot be checked");
        return -1;
    }

    public boolean addNextOfKin(PatientProfile p) {
        // If the NOK grid ALREADY has a kin row (an existing patient can come with prior NOK details), SELECT that
        // existing kin — click its grid row to load it into the form — instead of adding a new/duplicate kin.
        boolean existingKin = Boolean.TRUE.equals(page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor/i.test((x.querySelector('thead')||{}).innerText||'')); if(!t) return false;"
                + " return [...t.querySelectorAll('tbody tr')].some(r=>{ const tx=(r.textContent||'').trim(); return tx && !/no\\s*records|no\\s*data/i.test(tx); }); }"));
        if (existingKin) {
            // Leave it alone: the row is already in KinDetailsList and that is what Save needs. Clicking it fires
            // EditKinDetails, which takes the kin back out of the list into the form and blocks Save with
            // "Please click Add to include the NOK/Guarantor details!".
            System.out.println("addNextOfKin: NOK grid already has a kin — keeping that row as is (no Edit click).");
            return true;
        }
        // The screen validates the kin NRIC by LENGTH ONLY, against $scope.CivilIdLength:
        //     if (!KinNationalId || KinNationalId.toString().length !== CivilIdLength) -> "Please enter valid NRIC for NOK"
        // CivilIdLength arrives asynchronously, and until it does the comparison is against undefined, so EVERY
        // value is rejected — which is why the kin Add failed on some runs and not others. Wait for it, then make
        // the NRIC exactly that long instead of assuming 12.
        int civilIdLen = awaitCivilIdLength(8000);
        if (civilIdLen < 0) {
            // Not a slow load: Configapp.civiliddigit1 is not configured on this environment, so CivilIdLength is
            // undefined and the strict !== can never be satisfied. Record it so the step says WHY rather than
            // just "kin not in the grid".
            lastKinFailReason = "the screen's NOK NRIC check compares the length to $scope.CivilIdLength"
                    + " (= Configapp.civiliddigit1), which is NOT configured here — so every NRIC is rejected"
                    + " with \"Please enter valid NRIC for NOK\" once the kin Nationality is set to Malaysian";
        }
        if (civilIdLen > 0 && p.kinNric != null && p.kinNric.length() != civilIdLen) {
            String fixed = p.kinNric.length() > civilIdLen ? p.kinNric.substring(0, civilIdLen)
                    : p.kinNric + "0".repeat(civilIdLen - p.kinNric.length());
            System.out.println("addNextOfKin: CivilIdLength=" + civilIdLen + " — kin NRIC " + p.kinNric + " -> " + fixed);
            p.kinNric = fixed;
        }
        // Wait for the kin dropdowns to populate first — setSel() silently no-ops when an option isn't loaded
        // yet, so under a slow server the kin fields stay empty and AddKinDetails is rejected on every retry.
        try {
            page.waitForFunction(
                    "() => ['Registration.KinTitleID','Registration.KinRelationID','Registration.KinOccupationID','Registration.KinCountryID','Registration.KinMobileCountryCode']"
                            + ".every(ng => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e && e.options.length>1; })",
                    null, new Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("addNextOfKin: kin dropdowns not fully populated in time — proceeding"); }
        // Capture any JAlert/toast the Add raises so a failure reports WHY (e.g. "Please Enter Kin ...").
        page.evaluate("() => { window.__kinAlerts=[]; ['JAlert','jAlert'].forEach(n=>{ if(window[n] && !window['__ok_'+n]){ window['__ok_'+n]=window[n]; window[n]=function(m){ try{window.__kinAlerts.push(String(m));}catch(e){} return window['__ok_'+n].apply(this,arguments); }; } });"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__kinAlerts.includes(t)) window.__kinAlerts.push(t); }); }; new MutationObserver(grab).observe(document.body,{childList:true,subtree:true}); }");
        boolean kinAdded = false;
        final int kinRowsBefore = kinGridRowCount();
        for (int attempt = 0; attempt < 4 && !kinAdded; attempt++) {
            // Title options vary by environment (DSH's list doesn't include "Mr."/"Mrs." at all), so a single
            // hardcoded string fell back to the alphabetically-first option — a kin titled "Baby Of". Try several
            // real, gender-matching alternates before giving up to that fallback.
            boolean kinTitleMale = "Mr.".equals(p.kinTitle);
            String[] kinTitleCandidates = kinTitleMale
                    ? new String[]{p.kinTitle, "Mr.", "Mr", "Mister", "Encik", "Tuan"}
                    : new String[]{p.kinTitle, "Mrs.", "Mrs", "Ms.", "Ms", "Miss", "Puan", "Cik"};
            kinTitleActual = setSelLike("Registration.KinTitleID", kinTitleCandidates);
            fillModel("Registration.KinName", p.kinName);
            // Nationality FIRST — it decides whether a passport belongs on this kin at all. The kin follows the
            // PATIENT's nationality: a foreign patient registered with a Malaysian next of kin (hardcoded here)
            // was inconsistent test data, and it hid the foreign kin path entirely.
            String kinNat = (p.nationality == null || p.nationality.isEmpty()) ? "Malaysian" : p.nationality;
            // Verified, not fire-and-forget: the kin list drifts back to the environment default (or to
            // "--Select--") on a slow load exactly as the patient's does, and the Add then answers
            // "Please Select Nationality for NOK!" on every attempt.
            kinNationalityActual = ensureSelectVerified("Registration.NOKnationalid", kinNat);
            // A MALAYSIAN kin is identified by the NRIC alone; a foreign kin by Passport alone — exactly the same
            // rule as the patient's own identification. NRIC used to be filled unconditionally BEFORE this check
            // even ran, so a foreign kin (nationality follows the patient's, set above) ended up with both an
            // NRIC and a passport, which is what showed up as e.g. "Tom Baker / Afghanistan / NRIC 620222103385"
            // in the NOK grid — a nationality that should only ever carry a passport.
            // KinFamilyName is the kin PASSPORT NO. control (not a surname), so filling it for a local stored a
            // passport number against someone who should not have one.
            if (kinIsMalaysian()) {
                fillModel("Registration.KinNationalId", p.kinNric);              // NRIC
                // CLEAR it, don't merely skip it: this fill runs up to 4 times, and "Same As Patient Address" can
                // copy values across, so a passport from an earlier pass would otherwise survive into the save.
                clearModel("Registration.KinFamilyName");
                System.out.println("fillNextOfKin: kin nationality is Malaysian — NRIC only, Passport No. cleared");
            } else {
                clearModel("Registration.KinNationalId");                        // leave NRIC empty for a foreign kin
                fillModel("Registration.KinFamilyName", uniquePassport("B"));   // kin Passport No
                System.out.println("fillNextOfKin: kin nationality is \"" + kinNat + "\" — Passport only, NRIC cleared");
            }
            // Same environment gap again: DSH has no plain "Adopted Child"/"Biological Child" option. Its
            // KinRelationID list (confirmed by dumping the real options via setSelLike's fallback warning) has
            // gender-split "Adopted Son"/"Adopted Daughter" for an adopted child, but for a BIOLOGICAL child DSH
            // has no "Biological" anything at all — only plain "Son"/"Daughter" (the un-adopted default). Offer
            // the alternate that matches the kin's title first, the other gender second, before falling back.
            String[] kinRelCandidates;
            if ("Adopted Child".equals(p.kinRel)) {
                kinRelCandidates = kinTitleMale
                        ? new String[]{"Adopted Child", "Adopted Son", "Adopted Daughter"}
                        : new String[]{"Adopted Child", "Adopted Daughter", "Adopted Son"};
            } else if ("Biological Child".equals(p.kinRel)) {
                kinRelCandidates = kinTitleMale
                        ? new String[]{"Biological Child", "Biological Son", "Biological Daughter", "Son", "Daughter"}
                        : new String[]{"Biological Child", "Biological Daughter", "Biological Son", "Daughter", "Son"};
            } else {
                kinRelCandidates = new String[]{p.kinRel};
            }
            kinRelActual = setSelLike("Registration.KinRelationID", kinRelCandidates);
            fillModel("Registration.KinMobileNo", p.kinMobile);
            fillModel("Registration.KinEmail", p.kinEmail);
            // Same environment-specific gap as Title: DSH's Occupation list has no "Education", so this used to
            // fall back to whatever was first ("ACCOUNT"). Try a handful of common, plausible occupations first.
            kinOccupationActual = setSelLike("Registration.KinOccupationID",
                    new String[]{"Education", "Others", "Employed", "Self Employed", "Government Sector",
                            "Private Sector", "Business", "Unemployed"});
            setSelTxt("Registration.KinMobileCountryCode", "60");  // mandatory: else "Please Enter Kin Mobile Country Code!"
            // KinCountryID is the kin's ADDRESS country, NOT its nationality (that is NOKnationalid, set above,
            // whose ng-change fnsetCountrywithNationalitywiseNOK derives this field on its own). Forcing the
            // address country to the nationality is what broke the foreign kin on DSH: "Australia" has no States
            // in the master data, and since HIN-3720 the kin Add validates Country -> State -> City, so every
            // attempt died on "Please Select State". A foreign kin living at the patient's Malaysian address is
            // both realistic and accepted — the app lets the two disagree. Only fill this if it came out empty.
            ensureKinAddressCountry();
            // REAL click the "Same As Patient Address" checkbox (synthetic click doesn't fire Angular).
            Object chkTagged = page.evaluate("()=>{const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='chkSameasPatAddr'); if(!c || c.checked) return false; c.id='__kinSameAddr'; return true;}");
            if (Boolean.TRUE.equals(chkTagged)) {
                try { page.locator("#__kinSameAddr").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
                page.evaluate("()=>{const e=document.getElementById('__kinSameAddr'); if(e) e.removeAttribute('id');}");
            }
            ensureKinStateAndCity();   // Same-As-Address carries neither reliably -> "Please Select State/City!"
            // Re-select the NOK Nationality RIGHT BEFORE Add so AddKinDetails captures it into the grid entry
            // (else the grid kin lacks a nationality and Save says 'Please Select Nationality for NOK'). Its
            // ng-change (fnsetCountrywithNationalitywiseNOK) needs a beat to settle — and it REWRITES the kin
            // address Country, wiping the State/City just chosen, so only re-assert when it actually came out
            // empty, and repair the address afterwards when it did.
            if (selectedText("Registration.NOKnationalid").isEmpty()) {
                ensureSelectVerified("Registration.NOKnationalid", kinNat);
                ensureKinAddressCountry();
                ensureKinStateAndCity();
            }
            waitForAngular(600);
            // Snapshot the kin form HERE — this is the only moment it can be judged. Add commits the kin and
            // clears the form, so a probe run afterwards sees "--Select--" and blanks and proves nothing.
            lastKinPassportPreAdd = kinFormPassportState();
            System.out.println("addNextOfKin: kin passport state before Add => " + lastKinPassportPreAdd);
            // REAL click the Add-kin button so the row is actually added.
            Object addTagged = page.evaluate("()=>{const b=[...document.querySelectorAll('button,a')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__addKinBtn'; return true;}");
            if (Boolean.TRUE.equals(addTagged)) {
                try { page.locator("#__addKinBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
                page.evaluate("()=>{const e=document.getElementById('__addKinBtn'); if(e) e.removeAttribute('id');}");
            } else {
                System.out.println("addNextOfKin: AddKinDetails button NOT found (attempt " + (attempt + 1) + ")");
            }
            // Scope-call fallback — fire AddKinDetails() directly on the Angular scope (a synthetic/real click
            // occasionally doesn't fire the handler under load). Pass the kin model the same way the ng-click does.
            page.evaluate("()=>{ let done=false; document.querySelectorAll('*').forEach(el=>{ if(done)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.AddKinDetails==='function'){ x.$apply(()=>{ try{ x.AddKinDetails(x.Registration); }catch(e){ try{ x.AddKinDetails(); }catch(e2){} } }); done=true; break; } x=x.$parent; } }catch(e){} }); }");
            waitForAngular(1000);
            // Match on the FAMILY name (single word) and NRIC (digits) — NOT the full "First Family" name:
            // the grid splits the name across cells, so textContent concatenates to "FirstFamily" (no space)
            // and a spaced search string never matches, making a successful Add look like a failure.
            // Check the KIN grid specifically, and demand a row was actually GAINED. Searching every table on the
            // page for the family name / NRIC matched other grids (and the patient's own details), so a kin that
            // never made it into KinDetailsList reported as added — and Save then answered
            // "Please click Add to include the NOK/Guarantor details!".
            boolean inKinGrid = Boolean.TRUE.equals(page.evaluate("(a) => { const fam=(a[0]||'').toLowerCase(), nric=(a[1]||'');"
                    + " const t=[...document.querySelectorAll('table')].find(x=>/relation|guarantor|nationality/i.test((x.querySelector('thead')||{}).innerText||''));"
                    + " if(!t) return false;"
                    + " return [...t.querySelectorAll('tbody tr')].some(r=>{ const tx=(r.textContent||''); const tl=tx.toLowerCase();"
                    + "   if(!tx.trim() || /no\\s*records|no\\s*data/i.test(tx)) return false;"
                    + "   return (fam && tl.includes(fam)) || (nric && tx.replace(/\\s+/g,'').includes(nric)); }); }",
                    Arrays.asList(p.kinFamily, p.kinNric)));
            int kinRowsNow = kinGridRowCount();
            kinAdded = inKinGrid && kinRowsNow > kinRowsBefore;
            if (!kinAdded) System.out.println("addNextOfKin: attempt " + (attempt + 1) + " — kin rows "
                    + kinRowsBefore + " -> " + kinRowsNow + ", row matched=" + inKinGrid);
        }
        // NOTE: do NOT click the kin grid row after Add — clicking it fires EditKinDetails which MOVES the kin
        // OUT of the grid (KinDetailsList) back into the form for editing, emptying the list and causing
        // 'Please Fill Kin Details' at Save. Verified live: an empty kin form + a grid row saves cleanly.
        if (!kinAdded) {
            Object alerts = page.evaluate("() => (window.__kinAlerts||[]).slice(-5).join(' | ')");
            Object emptyKin = page.evaluate("() => ['Registration.KinTitleID','Registration.KinRelationID','Registration.KinOccupationID','Registration.KinCountryID','Registration.KinMobileCountryCode']"
                    + ".filter(ng=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return !e || !e.value || e.selectedIndex<=0; }).join(',')");
            Object diag = page.evaluate("() => { const btn=[...document.querySelectorAll('button,a')].some(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||''));"
                    + " const kt=[...document.querySelectorAll('table')].find(x=>/relation/i.test((x.querySelector('thead')||{}).innerText||''));"
                    + " const rows=kt?[...kt.querySelectorAll('tbody tr')].filter(r=>(r.textContent||'').trim() && !/no records/i.test(r.textContent||'')).length:'(no kin table)';"
                    + " const name=(document.querySelector(\"input[ng-model='Registration.KinName']\")||{}).value;"
                    + " return 'addBtn='+btn+' kinRows='+rows+' kinNameField=\"'+(name||'')+'\"'; }");
            System.out.println("addNextOfKin: FAILED — alerts=[" + alerts + "] emptyKinSelects=[" + emptyKin + "] " + diag);
        }
        return kinAdded;
    }

    // ---- save -------------------------------------------------------------

    /**
     * Re-assert critical fields (they occasionally fail to commit and block Save with a
     * "Please Enter ..." toast), then click Save and wait for the "Do you want to Save" confirm dialog.
     * Returns null if the dialog appeared, or the blocking toast text if the save was rejected.
     */
    /**
     * Open the NOK section (its grid sits under the "Patient Information" panel, which can be collapsed) and click
     * the kin's grid row. Clicking the row fires {@code EditKinDetails($index)}, which LOADS the kin's values
     * (KinName, NOK Nationality, etc.) back into the form WITHOUT removing it from {@code KinDetailsList} — so the
     * data no longer disappears before Save. Call this right after adding the kin (before Payor) and again at Save.
     */
    /**
     * Select a kin row from the NOK grid without needing a {@link PatientProfile} — for callers (e.g. the
     * Appointment List → VisitScreen path) that fill the kin fields themselves and then need the SAME
     * "click the row so the kin is committed" step OP Registration performs.
     */
    public void openNokAndClickGrid(String kinFamily, String kinNric) {
        PatientProfile p = new PatientProfile();
        p.kinFamily = kinFamily;
        p.kinNric = kinNric;
        openNokAndClickGrid(p);
    }

    public void openNokAndClickGrid(PatientProfile p) {
        boolean kinGridVisible = Boolean.TRUE.equals(page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor/i.test((x.querySelector('thead')||{}).innerText||'')); return t && t.offsetParent!==null && [...t.querySelectorAll('tbody tr')].some(r=>(r.textContent||'').trim()); }"));
        if (!kinGridVisible) {
            // Open the "Patient Information" accordion header — the exact control is //*[@id="headingTwo"]/a.
            try {
                page.locator("//*[@id='headingTwo']/a").first().click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(4000));
            } catch (Exception e) {
                // Fallback: click any element whose text is exactly "Patient Information".
                page.evaluate("() => { const h=[...document.querySelectorAll('a,button,div,h3,h4,span,.panel-heading,.panel-title,.box-header')].find(x=>/^\\s*patient information\\s*$/i.test((x.textContent||'').replace(/\\s+/g,' ').trim())); if(h) h.click(); }");
            }
            waitForAngular(600);
        }
        Object rowClicked = page.evaluate("(a) => { const fam=(a[0]||'').toLowerCase(), nric=(a[1]||'');"
                + " const kt=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!kt) return false; const row=[...kt.querySelectorAll('tbody tr')].find(r=>{ const tx=(r.textContent||''); const tl=tx.toLowerCase(); return (fam && tl.includes(fam)) || (nric && tx.replace(/\\s+/g,'').includes(nric)); }) || kt.querySelector('tbody tr'); if(!row) return false;"
                // scope-call EditKinDetails($index) directly (the row's own ng-click) — the reliable trigger.
                + " try{ const sc=angular.element(row).scope(); if(sc && typeof sc.EditKinDetails==='function'){ sc.$apply(()=>sc.EditKinDetails(sc.$index)); } }catch(e){}"
                + " const cell=[...row.querySelectorAll('td')].find(td=>td.offsetParent!==null && (td.textContent||'').trim() && !td.querySelector('button,a')); if(cell){ cell.id='__kinRowSave'; return true; } return 'called'; }",
                Arrays.asList(p.kinFamily, p.kinNric));
        if (Boolean.TRUE.equals(rowClicked)) {
            try { page.locator("#__kinRowSave").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
            page.evaluate("()=>{const e=document.getElementById('__kinRowSave'); if(e) e.removeAttribute('id');}");
        }
        waitForAngular(700);
    }

    /**
     * Dismiss the "Confirm! — Patient has previous unclosed episode." popup (and similar info confirms) by clicking
     * its OK/Yes/Continue button. The app raises this during Save when the patient already has an open episode; if
     * left up it blocks the "Do you want to Save" dialog and the save. Returns true if it clicked something.
     */
    private boolean dismissBlockingConfirm() {
        return Boolean.TRUE.equals(page.evaluate("() => { const boxes=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.jconfirm-box,.modal,.sweet-alert')].filter(m=>m.offsetParent!==null);"
                + " let clicked=false;"
                // "The visit for the selected doctor is already exists, still do you want to continue?" (YES/NO)
                // is a SECOND blocking confirm this matcher did not know about, so the save sat on it until the
                // step timed out. It is an informational duplicate-visit warning — answering YES is correct.
                + " boxes.forEach(box=>{ const txt=(box.textContent||'');"
                + "  if(/unclosed episode|previous unclosed|already has|open episode"
                + "     |visit for the selected doctor|still do you want to continue/i.test(txt)){"
                + "   const ok=[...box.querySelectorAll('button,a')].find(b=>/^(ok|yes|continue|proceed)$/i.test((b.textContent||'').trim()) && b.offsetParent!==null);"
                + "   if(ok){ ok.click(); clicked=true; } } });"
                + " return clicked; }"));
    }

    public String clickSaveAwaitConfirm(PatientProfile p) {
        // The NOK row can disappear before Save — re-ensure it's present, re-adding the kin if it's gone.
        for (int attempt = 0; attempt < 5; attempt++) {
            boolean kinPresent = Boolean.TRUE.equals(page.evaluate("(a)=>{ const fam=(a[0]||'').toLowerCase(), nric=(a[1]||'');"
                    // Our kin matched by family/NRIC, OR ANY real row already in the kin/relation grid (existing patient).
                    + " const kt=[...document.querySelectorAll('table')].find(x=>/relation|nationality|guarantor/i.test((x.querySelector('thead')||{}).innerText||''));"
                    + " const anyRow = kt && [...kt.querySelectorAll('tbody tr')].some(r=>{ const tx=(r.textContent||'').trim(); return tx && !/no\\s*records|no\\s*data/i.test(tx); });"
                    + " const mine = [...document.querySelectorAll('table tbody tr')].some(r=>{ const t=(r.textContent||''); const tl=t.toLowerCase(); return (fam && tl.includes(fam)) || (nric && t.replace(/\\s+/g,'').includes(nric)); });"
                    + " return anyRow || mine; }",
                    Arrays.asList(p.kinFamily, p.kinNric)));
            if (kinPresent) break;
            System.out.println("clickSaveAwaitConfirm: NOK row missing before Save — re-adding (attempt " + (attempt + 1) + ")");
            addNextOfKin(p);
        }
        System.out.println("clickSaveAwaitConfirm: kin state before Save => " + kinState());
        // DO NOT click the kin grid row before Save. That row's click fires EditKinDetails, which pulls the kin
        // back OUT of KinDetailsList into the form — so the app then rejects Save with "Please click Add to
        // include the NOK/Guarantor details!". The kin is added to the grid once, and Save follows directly.
        // Apply the captured field SNAPSHOT as PLAIN properties on every Registration/Visit object in the SAME
        // tick as the Save click — no DOM change events, so no ng-change watcher fires and no field-reset cascade
        // empties another field (the fix for the interdependent 'Please Enter/Select X' chain on this screen).
        // Keys are "Registration.PrefixID" -> numeric model value / "Registration.KinName" -> text / "Visit.X".
        final String saveJs = "(a) => { const snap=a.snap;"
                + " window.__regToast=''; if(window.__rtObs) window.__rtObs.disconnect();"
                + " window.__rtObs=new MutationObserver(()=>{ const el=document.querySelector('.toast-message'); const m=el?(el.textContent||'').trim():''; if(m && !window.__regToast) window.__regToast=m; });"
                + " window.__rtObs.observe(document.body,{childList:true,subtree:true});"
                + " const btns=[...document.querySelectorAll(\"button[ng-click='IUDRegistration();']\")]; const b=btns.find(x=>x.offsetParent!==null)||btns[0]; if(!b) return 'no-save-btn'; b.scrollIntoView({block:'center'});"
                + " const seenR=new Set(), seenV=new Set();"
                + " document.querySelectorAll('*').forEach(el=>{ try{ const sc=angular.element(el).scope(); let x=sc; for(let i=0;i<15&&x;i++){"
                // Apply PATIENT fields only — NOT the kin form fields. The kin is already committed to the grid
                // (KinDetailsList); re-filling the kin FORM makes it "partial" and the Save then re-validates it
                // ('Please Enter Kin Name / Select Nationality for NOK'). An empty kin form + a grid row saves fine.
                + "   if(x.Registration && typeof x.Registration==='object' && !seenR.has(x.Registration)){ seenR.add(x.Registration); const reg=x.Registration; reg.PassportExpirydate='31/12/2045'; for(const k in snap){ if(k.indexOf('Registration.')===0 && k.indexOf('Kin')<0 && k.indexOf('NOK')<0) reg[k.slice(13)]=snap[k]; } }"
                + "   if(x.Visit && typeof x.Visit==='object' && !seenV.has(x.Visit)){ seenV.add(x.Visit); const vis=x.Visit; vis.TokenNo='1'; vis.SaveBtnEnable=1; for(const k in snap){ if(k.indexOf('Visit.')===0) vis[k.slice(6)]=snap[k]; } }"
                + "   x=x.$parent; } }catch(e){} });"
                + VISIT_TYPE_GUARD_JS
                + " ['mousedown','mouseup','click'].forEach(t=>b.dispatchEvent(new MouseEvent(t,{bubbles:true,cancelable:true,view:window}))); return 'clicked'; }";
        // Retry the Save on a RECOVERABLE field-reset validation ("Please Select/Enter …" — a digest race that can
        // clear e.g. the Patient Title). On such a toast, re-assert the mandatory selects (real change so select2 +
        // the validator see them), re-apply the snapshot and re-click Save. Give up after 3 attempts.
        String lastToast = "";
        for (int attempt = 0; attempt < 3; attempt++) {
            page.evaluate(saveJs, java.util.Map.of("snap", snap));
            lastToast = "";
            for (int i = 0; i < 40; i++) {
                // The "Patient has previous unclosed episode" confirm can pop up first — click OK and keep waiting.
                if (dismissBlockingConfirm()) { System.out.println("clickSaveAwaitConfirm: clicked OK on 'previous unclosed episode' popup."); waitForAngular(500); continue; }
                boolean dialogShown = Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].some(m=>m.offsetParent!==null && /do you want to save/i.test(m.textContent||''))"));
                if (dialogShown) return null;
                Object bt = page.evaluate("() => window.__regToast || ''");
                if (bt != null && !bt.toString().isEmpty()) { lastToast = bt.toString(); break; }
                waitForAngular(500);
            }
            if (lastToast.isEmpty()) break; // no dialog and no toast — fall through to the message below
            // "… Visit Type Not Allowed For this Patient!" is recoverable too — the CHOSEN Visit Type is refused
            // for this patient (DSH refuses Cross Consultation), so move to a different one and save again rather
            // than failing the run.
            boolean visitTypeRefused = lastToast.toLowerCase().matches(".*visit\\s*type.*(not\\s*allow|no[t]?\\s*permitted).*");
            // "Please Select SubDepartment !" — the SERVER says the field is mandatory, which is the only reliable
            // signal we get. DSH enforces it on OP Registration while leaving the label unstarred, so every attempt
            // to detect it from the markup (starred label / required attribute / URL) misses it, and the fill's
            // fallback had legitimately cleared the field because no sub-department under that department leaves a
            // selectable doctor. React to the message instead of guessing from the DOM.
            boolean subDeptMissing = lastToast.toLowerCase().matches(".*sub\\s*depart.*");
            boolean recoverable = lastToast.toLowerCase().matches(".*please\\s+(select|enter).*")
                    || lastToast.toLowerCase().contains("mandatory") || visitTypeRefused;
            if (!recoverable || attempt == 2) return lastToast;
            System.out.println("clickSaveAwaitConfirm: recoverable validation '" + lastToast + "' — re-asserting fields & retrying (attempt " + (attempt + 1) + ")");
            // "Please fill in all the mandatory fields!" (unlike "Please Select X!") names NOTHING, so
            // satisfyFieldNamedInToast has nothing to match and the run just retries blind until it gives up.
            // Dump every starred/required field on the WHOLE page that is still empty — same trick that found the
            // System Config User Payable gap — so the actual offender shows up in the log instead of a guessing game.
            if (lastToast.toLowerCase().contains("fill in all the mandatory")) {
                // Newer builds (KS: hotfix/ValidationIssues, 09-08-2026) aggregate the check in
                // fnFATCollectMandatory() and tag every offender with `has-error` — that is the APP's own verdict,
                // far better than our guess at which starred field looks empty. Read it when it is there.
                String flagged = appFlaggedMandatoryFields();
                System.out.println("clickSaveAwaitConfirm: unnamed mandatory-field rejection — app-flagged: "
                        + (flagged.isEmpty() ? "(none — this build does not tag them)" : flagged));
                lastMandatoryFields = flagged;
                System.out.println("clickSaveAwaitConfirm: ... our own scan of empty starred/required fields: "
                        + emptyStarredFieldsWholePage());
                // This build reports the aggregate message instead of "Please Select SubDepartment !", so the
                // existing sub-department recovery never fired and the save retried blind. Drive it off the name
                // the APP flagged.
                if (flagged.toLowerCase().matches(".*sub\\s*dep.*")) subDeptMissing = true;
            }
            page.evaluate("() => { window.__regToast=''; document.querySelectorAll('.toast-message,.toast').forEach(e=>{ try{ e.remove(); }catch(x){} }); }");
            if (visitTypeRefused) {
                String vt = selectDifferentVisitType();
                System.out.println("clickSaveAwaitConfirm: Visit Type refused — switched to \""
                        + (vt.isEmpty() ? "(none available)" : vt) + "\"");
            }
            // Generic: satisfy whatever dropdown the toast names ("Please Select Admission Source!" …). Runs before
            // the specific handlers so they can still override with their extra cascade logic.
            if (!visitTypeRefused && !subDeptMissing) {
                String fixed = satisfyFieldNamedInToast(lastToast);
                if (!fixed.isEmpty()) System.out.println("clickSaveAwaitConfirm: satisfied field named in toast — " + fixed);
            }
            if (subDeptMissing) {
                // Re-run the walk with subDeptRequired set: it prefers a sub-department that still leaves a doctor
                // and, when none does, KEEPS the first one instead of clearing it. Values go through setSelTxt, so
                // they land in `snap` and survive the re-apply this loop performs on the next attempt.
                subDeptRequired = true;
                try { selectSubDepartmentAndDoctor(); } finally { subDeptRequired = false; }
                waitForAngular(900);
                System.out.println("clickSaveAwaitConfirm: server demanded Sub Department — re-selected it; Doctor="
                        + currentDoctor());
            }
            // Re-select the Patient Title (the observed flake) — punctuation-tolerant + verified so a cross-env
            // text mismatch (e.g. "Mrs." vs "Mrs") still binds and the validator sees it.
            setPrefixRobust(p.prefix);
            // Gender is the OTHER field this retry loop can knock out — confirmed live: a patient whose Gender
            // was correctly set survived every fill step, then came back empty specifically after THIS retry ran
            // selectSubDepartmentAndDoctor() above (a Department/Sub-Department cascade unrelated to Gender, but
            // the same class of AngularJS digest/scope disruption already known to clear Visit Type). Re-select it
            // — the same "first real option" fallback used throughout this suite — rather than let the run give up
            // on this patient and move to another one. Only acts if it is genuinely empty right now; never
            // overwrites a value that survived.
            page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.GenderID'); if(!e) return;"
                    + " const cur=((e.options[e.selectedIndex]||{}).text||'').trim(); if(cur && !/^-*\\s*select\\s*-*$/i.test(cur)) return;"
                    + " const i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.text||'').trim())); if(i<0) return;"
                    + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery||window.$; if($){try{$(e).trigger('change'); $(e).select2('val', e.value);}catch(err){}} }");
            waitForAngular(900);
        }
        return lastToast.isEmpty() ? "no dialog and no toast appeared" : lastToast;
    }

    /**
     * Click Save on the confirm dialog and wait for success — confirmed by the "Saved Successfully"
     * toast, the Registration Report tab, or the in-app "Consent Details" modal (any of which only
     * appear after a successful save; the toast can fade on a slow site).
     */
    public SaveOutcome confirmSaveAndAwaitSuccess() {
        page.evaluate("() => { const box=[...document.querySelectorAll('.ng-confirm-box,.jconfirm,.modal')].find(m=>m.offsetParent!==null && /do you want to save/i.test(m.textContent||''));"
                + " const sv=[...(box||document).querySelectorAll('button')].find(b=>/^save$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(sv) sv.click(); }");
        SaveOutcome out = new SaveOutcome();
        out.toast = "(toast faded)";
        // Poll generously — the save can be slow. Success = the toast, OR any artifact that only appears
        // after a successful save (Registration Report tab, either consent-form tab, or the modal).
        for (int i = 0; i < 60 && !out.saved; i++) {
            // The "Patient has previous unclosed episode" confirm can appear here too — click OK to let the save go.
            dismissBlockingConfirm();
            Object rt = page.evaluate("() => window.__regToast || ''");
            String t = rt == null ? "" : rt.toString();
            if (t.toLowerCase().contains("saved successfully")) { out.toast = t; out.saved = true; break; }
            boolean artifact = page.context().pages().stream().anyMatch(p ->
                    p.url().contains("RegistrationReport") || p.url().contains("nhisformstest"));
            if (artifact) { out.saved = true; out.toast = t.isEmpty() ? "Registration saved (report/consent tab opened)" : t; break; }
            boolean modalUp = Boolean.TRUE.equals(page.evaluate("() => /Consent Details/i.test((document.body&&document.body.innerText)||'') && [...document.querySelectorAll(\"button[ng-click='IUDconsentdetail()']\")].some(b=>b.offsetParent!==null)"));
            if (modalUp) { out.saved = true; out.toast = t.isEmpty() ? "Registration saved (Consent Details modal shown)" : t; break; }
            if (!t.isEmpty()) { out.toast = t; break; } // a non-success toast = a real error
            waitForAngular(500);
        }
        return out;
    }

    // ---- post-save artifacts ---------------------------------------------

    /** Wait for the in-app "Consent Details" (PERSONAL DATA NOTICE &amp; CONSENT) modal. */
    public boolean waitConsentDetailsModal() {
        try {
            page.waitForFunction("() => /Consent Details/i.test(document.body.innerText) && [...document.querySelectorAll(\"button[ng-click='IUDconsentdetail()']\")].some(b=>b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(10000));
            return true;
        } catch (Exception e) {
            System.out.println("Consent Details modal did not appear");
            return false;
        }
    }

    /** Close the Consent Details modal (its Save only opens an empty PDPA document list). */
    public void closeConsentDetailsModal() {
        page.evaluate("() => { const c=[...document.querySelectorAll(\"button[ng-click='fnclear()']\")].find(x=>x.offsetParent!==null); if(c) c.click(); }");
        waitForAngular(500);
    }

    /** Poll for an auto-opened tab whose URL contains {@code urlPart}. */
    public Page findTab(String urlPart) {
        for (int i = 0; i < 20; i++) {
            Page found = page.context().pages().stream().filter(p -> p.url().contains(urlPart)).reduce((a, b) -> b).orElse(null);
            if (found != null) return found;
            waitForAngular(500);
        }
        return null;
    }

    /**
     * Find the auto-opened <b>consent form</b> tab (Patient Registration Form) generically — its template id differs
     * by environment (devhis {@code TH_EF_293}), so instead of a hardcoded id, match the document host / form
     * signature: any tab that is NOT the main app page and NOT the PDF {@code RegistrationReport}, whose URL looks
     * like the nhisforms document builder ({@code nhisform*}, {@code TH_EF*}, a {@code TemplateId} query, or a
     * {@code /Create}//{@code /Edit/} document path). Returns the newest such tab, or null.
     */
    public Page findConsentFormTab() {
        for (int i = 0; i < 20; i++) {
            Page found = page.context().pages().stream()
                    .filter(p -> p != page && !p.isClosed())
                    .filter(p -> {
                        String u = p.url().toLowerCase();
                        if (u.contains("registrationreport") || u.isEmpty() || u.equals("about:blank")) return false;
                        return u.contains("nhisform") || u.contains("th_ef") || u.contains("templateid")
                                || u.contains("/create") || u.contains("/edit/") || u.contains("visitid=");
                    })
                    .reduce((a, b) -> b).orElse(null);
            if (found != null) return found;
            waitForAngular(500);
        }
        return null;
    }

    /**
     * Capture the (PDF) Registration Report tab as PNG bytes — bring it to the front (a backgrounded
     * PDF tab screenshots blank), capture, then restore the primary page. Returns null on failure.
     */
    /**
     * "" if {@code p} shows real content; otherwise a short description of the browser-level load failure
     * (chrome-error page, DNS failure, connection error) — e.g. a consent-form/report tab whose host never
     * resolved. A tab existing with the right URL is NOT proof it rendered: {@link #findTab} matches by URL
     * substring alone, and Playwright still returns a real {@code Page} for a Chrome error page.
     */
    public String describeIfBrokenPage(Page p) {
        if (p == null) return "(no page)";
        try {
            if (p.url().startsWith("chrome-error://")) return "chrome-error page (" + p.url() + ")";
            Object r = p.evaluate("() => { const t=(document.title||'')+' '+((document.body&&document.body.innerText)||'');"
                    + " const m=t.match(/this site can.?t be reached|dns_probe[a-z_]*|err_connection[a-z_]*|err_name_not_resolved|err_internet_disconnected/i);"
                    + " return m ? m[0] : ''; }");
            String s = r == null ? "" : r.toString().trim();
            return s.isEmpty() ? "" : s + " (" + p.url() + ")";
        } catch (Exception e) {
            return "";  // evaluate itself failing isn't proof of a broken page — don't false-positive
        }
    }

    public byte[] captureReportTab(Page reportTab) {
        byte[] png = null;
        try {
            reportTab.bringToFront();
            try { reportTab.waitForLoadState(); } catch (Exception ignore) {}
            reportTab.waitForTimeout(2500);
            png = reportTab.screenshot(new Page.ScreenshotOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("Registration Report screenshot: " + e.getMessage());
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) {}
        }
        return png;
    }

    /** Set by {@link #submitPatientForm} when the consent tab never actually loaded — the caller's step should
     *  name this explicitly rather than a generic "Consent submit not confirmed". */
    public String lastPatientFormLoadError = "";

    /** Submit the (pre-filled) Patient Registration Form consent tab: Submit -&gt; Yes -&gt; success. */
    public boolean submitPatientForm(Page form) {
        lastPatientFormLoadError = "";
        try { form.waitForLoadState(); } catch (Exception ignore) {}
        form.waitForTimeout(1500);
        // Fail fast and NAME it: a tab matched by URL substring is not proof it rendered — this host can come
        // back as a browser-level DNS/connection error (confirmed live: dsh-nhisforms.kpjhealth.com.my
        // DNS_PROBE_FINISHED_NXDOMAIN) and would otherwise burn the full 8s+15s of waits below for nothing.
        String broken = describeIfBrokenPage(form);
        if (!broken.isEmpty()) {
            lastPatientFormLoadError = broken;
            System.out.println("submitPatientForm: consent form tab did not load — " + broken);
            return false;
        }
        form.evaluate("() => { const b=[...document.querySelectorAll('button,input[type=submit]')].find(x=>/^submit$/i.test((x.textContent||x.value||'').trim()) && x.offsetParent!==null); if(b){ b.scrollIntoView({block:'center'}); b.click(); } }");
        boolean submitted = false;
        try {
            form.waitForFunction("() => [...document.querySelectorAll('button,a')].some(b=>/^yes$/i.test((b.textContent||'').trim()) && b.offsetParent!==null)",
                    null, new Page.WaitForFunctionOptions().setTimeout(8000));
            form.evaluate("() => { const y=[...document.querySelectorAll('button,a')].find(b=>/^yes$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(y) y.click(); }");
            form.waitForFunction("() => /form submitted successfully/i.test((document.body && document.body.innerText) || '') || /\\/Edit\\//.test(location.href)",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
            submitted = true;
        } catch (Exception e) {
            System.out.println("Consent submit: " + e.getMessage());
        }
        // Dismiss the 'Form Submitted Successfully' dialog.
        form.waitForTimeout(1000);
        form.evaluate("() => { const ok=[...document.querySelectorAll('button,a')].find(b=>/^ok$/i.test((b.textContent||'').trim()) && b.offsetParent!==null); if(ok) ok.click(); }");
        form.waitForTimeout(1000);
        return submitted;
    }

    /** Full-page (long) screenshot of the Patient Registration Form. Returns PNG bytes, or null. */
    public byte[] captureFullForm(Page form) {
        byte[] png = null;
        try {
            form.bringToFront();
            png = form.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000));
        } catch (Exception e) {
            System.out.println("Patient Registration Form full-page screenshot: " + e.getMessage());
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) {}
        }
        return png;
    }

    // ---- low-level helpers ------------------------------------------------

    /** Select a select2/ng-options dropdown (by ng-model) to the option with the given visible text.
     *  Also snapshots the resulting model value so Save can re-apply it as a plain property. */
    private void setSel(String ngModel, String optionText) {
        Object v = page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "if(!e)return null;const o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);if(!o)return null;e.value=o.value;"
                + "const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}"
                + "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return (c && c.$modelValue!=null)?c.$modelValue:null;}",
                Arrays.asList(ngModel, optionText));
        snap(ngModel, v);
    }

    /**
     * Set the Patient Title (<b>Registration.PrefixID</b>) robustly across environments. {@link #setSel} matches an
     * option by EXACT visible text, but the Title options differ by env (e.g. devhis "Mrs." vs DSH "Mrs"), so an
     * exact-text miss leaves PrefixID empty → Save is blocked by "Please Select Patient Title!". This waits for the
     * dropdown to populate, then picks the option by punctuation-insensitive match ("mrs." ≈ "Mrs"), else a
     * startsWith match, else the first real option — verifying the ng-model actually committed and snapshotting the
     * bound value for the Save re-apply. Returns true once PrefixID holds a real value.
     */
    private boolean setPrefixRobust(String want) {
        try {
            page.waitForFunction("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.PrefixID'); return e && e.options.length>1; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("setPrefixRobust: Patient Title dropdown did not populate in time"); }
        Object v = null;
        for (int a = 0; a < 4 && v == null; a++) {
            v = page.evaluate("(want)=>{ const $=window.jQuery; const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                    + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.PrefixID'); if(!e) return null;"
                    + " const opts=[...e.options]; const w=norm(want);"
                    + " let o=opts.find(x=>x.value && norm(x.textContent)===w)"
                    + "   || opts.find(x=>x.value && norm(x.textContent) && (norm(x.textContent).startsWith(w) || w.startsWith(norm(x.textContent))))"
                    + "   || opts.find(x=>x.value && !/^select$/.test(norm(x.textContent)));"
                    + " if(!o) return null; e.value=o.value; const c=angular.element(e).controller('ngModel');"
                    + " if(c){ c.$setViewValue(o.value); c.$render(); } if($){ try{ $(e).trigger('change'); $(e).select2('val',o.value); }catch(err){} }"
                    + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " return (c && c.$modelValue!=null && c.$modelValue!=='')?c.$modelValue:null; }", want);
            if (v == null) waitForAngular(400);
        }
        snap("Registration.PrefixID", v);
        return v != null;
    }

    /** Select a dropdown (by ng-model) to the option matching text (or first real option) via selectedIndex+change
     *  — the ng-options-safe binding (proven in the IP Admission flow). Setting {@code $setViewValue(option.value)}
     *  corrupts an ng-options select (its DOM value is "number:N"), so the model never actually binds. */
    private void setSelTxt(String ngModel, String optionText) {
        Object v = page.evaluate("([ng,txt])=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null;"
                + " let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return null;"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} const c=angular.element(e).controller('ngModel'); return (c && c.$modelValue!=null)?c.$modelValue:null; }",
                Arrays.asList(ngModel, optionText));
        snap(ngModel, v);
        waitForAngular(150);
    }

    /**
     * Select an option by text the way a human would — and SAY SO when the wanted text is not there.
     *
     * <p>{@link #setSelTxt(String, String)} matches the option text EXACTLY and, on no match, silently takes the
     * FIRST real option. That is deliberate for Doctor ("Allen R", else anybody), but for a value that carries
     * meaning it quietly writes the wrong data and nothing reports it: asking for Nationality "Malaysian" on an
     * environment whose option reads "Malaysia" selected <b>Afghanistan</b> — the first entry of an alphabetical
     * list — and the run still passed. Same trap for kin Title ("Mr." vs "Mr") and Occupation.</p>
     *
     * <p>So: match on a normalised comparison (case- and punctuation-insensitive), then prefix, then substring, and
     * only then fall back to the first option — printing a loud warning when it does, so a wrong value shows up in
     * the log instead of hiding in the grid.</p>
     *
     * @return the option text actually selected ("" when the field is absent or empty)
     */
    private String setSelLike(String ngModel, String wanted) {
        return setSelLike(ngModel, new String[]{wanted});
    }

    /**
     * Same as {@link #setSelLike(String, String)}, but tries several acceptable values in order before giving up
     * to the first-option fallback. Different environments populate the same dropdown with different option sets
     * (DSH's Title/Occupation lists don't contain "Mr."/"Education" at all), so a single hardcoded "wanted" string
     * fell back to whatever was alphabetically first — "Baby Of" as a kin's title, "ACCOUNT" as an occupation.
     * Real, plausible alternates are tried first; only when NONE of them exist does it fall back and warn.
     */
    private String setSelLike(String ngModel, String[] wantedCandidates) {
        String candidatesJson = "[" + Arrays.stream(wantedCandidates)
                .map(w -> "\"" + w.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .reduce((a, b) -> a + "," + b).orElse("") + "]";
        Object r = page.evaluate("([ng,candJson]) => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const candidates=JSON.parse(candJson);"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null;"
                + " const opts=[...e.options].map((o,i)=>({o,i,t:(o.textContent||'').trim(),n:norm(o.textContent)}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " if(!opts.length) return null;"
                // Fuzzy matching is only safe on LONG values. "Mr." normalises to "mr", and "mrs" starts with "mr",
                // so prefix matching cheerfully titled a male next of kin "Mrs". Below 4 characters, exact only —
                // punctuation is already normalised away, which is all the tolerance a title ever needed.
                // Matching order matters. Asking for "Malaysia" once matched "Malay" - a RACE, not a nationality -
                // because "malaysia".startsWith("malay"). So: exact first; then an option that EXTENDS the wanted
                // text (Malaysian for Malaysia), shortest such option first; and only then an option the wanted
                // text extends, and ONLY when it is at least 80% as long, which keeps "Malaysia"/"Malaysian" and
                // rejects "Malay" (5 of 9 characters). Every fuzzy step still needs 4+ characters, so "Mr." can
                // never reach "Mrs". Each candidate is tried in turn, so a value missing on THIS environment does
                // not fall through to the alphabetically-first option while a plausible alternate still exists.
                + " const MIN=4, NEAR=0.8; let m=null, how='', usedWanted='';"
                + " for(const txt of candidates){ const w=norm(txt); if(!w) continue;"
                + "   let cm=null, chow='';"
                + "   cm=opts.find(x=>x.n===w); if(cm) chow='exact';"
                + "   if(!cm && w.length>=MIN){ const ext=opts.filter(x=>x.n.startsWith(w)).sort((a,b)=>a.n.length-b.n.length);"
                + "     if(ext.length){ cm=ext[0]; chow='extends'; } }"
                + "   if(!cm && w.length>=MIN){ const sub=opts.filter(x=>x.n.length>=MIN && w.startsWith(x.n) && x.n.length>=w.length*NEAR)"
                + "       .sort((a,b)=>b.n.length-a.n.length); if(sub.length){ cm=sub[0]; chow='near-prefix'; } }"
                + "   if(!cm && w.length>=MIN){ const c=opts.filter(x=>x.n.length>=MIN && (x.n.indexOf(w)>=0||w.indexOf(x.n)>=0)"
                + "       && x.n.length>=w.length*NEAR).sort((a,b)=>b.n.length-a.n.length); if(c.length){ cm=c[0]; chow='contains'; } }"
                + "   if(cm){ m=cm; how=chow; usedWanted=txt; break; } }"
                + " if(!m){ m=opts[0]; how='FIRST-OPTION-FALLBACK'; usedWanted=candidates[0]||''; }"
                + " e.selectedIndex=m.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const A=window.angular,$=window.jQuery;"
                + " try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " let v=''; try{ const c=A.element(e).controller('ngModel'); if(c && c.$modelValue!=null) v=String(c.$modelValue); }catch(x){}"
                + " return JSON.stringify({value:v, text:m.t, how, wanted:usedWanted, allOpts:opts.map(x=>x.t)}); }",
                Arrays.asList(ngModel, candidatesJson));
        if (r == null) return "";
        String s = r.toString();
        String text = group(s, "\"text\":\"([^\"]*)\"");
        String how = group(s, "\"how\":\"([^\"]*)\"");
        String value = group(s, "\"value\":\"([^\"]*)\"");
        String usedWanted = group(s, "\"wanted\":\"([^\"]*)\"");
        if (!value.isEmpty()) snap(ngModel, value);
        if ("FIRST-OPTION-FALLBACK".equals(how)) {
            // Dump every real option this environment actually offers — guessing plausible alternates blind
            // (e.g. "Biological Son"/"Biological Daughter") missed again on DSH, so the next fix needs the truth
            // instead of another guess.
            String allOptsJson = group(s, "\"allOpts\":(\\[[^\\]]*\\])");
            System.out.println("setSelLike: WARNING " + ngModel + " has no option like any of "
                    + Arrays.toString(wantedCandidates) + " — fell back to the first option \"" + text
                    + "\" (WRONG DATA will be saved). Actual options on this environment: " + allOptsJson);
        } else if (!"exact".equals(how)) {
            System.out.println("setSelLike: " + ngModel + " \"" + usedWanted + "\" matched \"" + text + "\" (" + how + ")");
        }
        waitForAngular(150);
        return text;
    }

    /** True when the PATIENT nationality currently on the form is Malaysian (read from the control, as below). */
    private boolean patientIsMalaysian() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.NationalityID');"
                + " if(!e) return false; const o=e.options[e.selectedIndex]; if(!o||!o.value) return false;"
                + " return norm(o.textContent).indexOf('malaysia')===0; }");
        return Boolean.TRUE.equals(r);
    }

    /** Blank a field through its Angular model, so a value from an earlier pass cannot survive into the save. */
    private void clearModel(String ngModel) {
        page.evaluate("(ng)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng);"
                + " if(!e) return; const c=angular.element(e).controller('ngModel'); e.value='';"
                + " if(c){ c.$setViewValue(''); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }", ngModel);
        // REMOVE the key from the snapshot — snap() deliberately ignores empty values, so snap(ng,"") would leave
        // any value written on an earlier pass in place, and the Save re-apply would put the passport straight back.
        snap.remove(ngModel);
        waitForAngular(120);
    }

    /**
     * True when the NOK nationality currently on the form is Malaysian.
     *
     * <p>Read from the CONTROL rather than from what was requested: the option text differs by environment
     * ("Malaysia" vs "Malaysian"), and on a site without a matching option {@code setSelLike} falls back to the
     * first entry — so the form can hold something else entirely. What is on screen is what gets saved.</p>
     */
    private boolean kinIsMalaysian() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.NOKnationalid');"
                + " if(!e) return false; const o=e.options[e.selectedIndex]; if(!o||!o.value) return false;"
                + " return norm(o.textContent).indexOf('malaysia')===0; }");
        return Boolean.TRUE.equals(r);
    }

    /** True when the Passport No. control ({@code Registration.FamilyName}) accepts input right now. */
    private boolean passportEditable() {
        return Boolean.TRUE.equals(page.evaluate("() => { const e=[...document.querySelectorAll('input')]"
                + " .find(x=>x.getAttribute('ng-model')==='Registration.FamilyName');"
                + " return !!e && e.offsetParent!==null && !e.disabled && !e.readOnly; }"));
    }

    /** Counter so two passports minted in the same nanosecond-truncated window still differ. */
    private static final java.util.concurrent.atomic.AtomicInteger PASSPORT_SEQ = new java.util.concurrent.atomic.AtomicInteger();

    /** A unique passport-shaped value, e.g. {@code A4831207}. Prefix keeps patient and kin apart. */
    private static String uniquePassport(String prefix) {
        long n = Math.abs(System.nanoTime() / 1000 + PASSPORT_SEQ.incrementAndGet() * 7919L);
        return prefix + String.format("%07d", n % 10_000_000L);
    }

    /** First capture group of {@code re} in {@code s}, or "". */
    private static String group(String s, String re) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(re).matcher(s);
        return m.find() ? m.group(1) : "";
    }

    /** Fill an input/textarea (by ng-model) via the Angular model. Also snapshots the value for Save re-apply. */
    private void fillModel(String ngModel, String value) {
        page.evaluate("([ng,v])=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng&&(x.offsetParent!==null||!x.getAttribute('maxlength')));"
                + "if(!e)return;const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}",
                Arrays.asList(ngModel, value));
        snap(ngModel, value);
    }
}
