package com.kpj.pages.Ip;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * IP &gt; <b>Admission</b> (route {@code #/Admission}) — a full IPD admission.
 *
 * <p>Reached by CLICKING the menu tab (IP → Admission), not a direct URL. The form is the SAME IPD
 * admission form as Emergency Admission (verified live 2026-07-14: {@code Registration.*} patient fields +
 * {@code Admission.*} cascade + Save {@code IUDAdmission();} → "Patient Admitted Successfully."), so the
 * fill logic mirrors {@code com.kpj.pages.Emegency_Page.Emergency_Admission}. Save runs the data-driven
 * validation {@code ValidationListAdmission} requiring, in order: Patient Name/Gender, Admission Location,
 * Department, Doctor, Admission Type, Patient Source, Bed Class, Ward, Billing Class, Bed (Bed is SKIPPED
 * when {@code Admission.NonPresenceAdmission} is truthy), plus {@code Admission.AdmissionPurposeID}.</p>
 */
public class Admission extends BasePage {

    public Admission(Page page) { super(page); }

    // Critical patient fields captured in fillPatientSection and re-asserted right before Save (setting NRIC
    // fires an async patient-lookup that can wipe Mobile/Gender, so they must be re-set at the last moment).
    private String pMobile = "", pNric = "", pEmail = "", pDob = "", pPostCode = "";
    private String pGender = "", pFullName = "", pFamilyName = "";

    // Log of "<field> was cleared after entry, then restored" events (the app's own async patient-lookup wipe —
    // see restoreWipedContactFields / satisfyIdentityToast). Recorded here so the TEST's report can say WHY a
    // save needed retries — or, if it still failed, that repeated field-clearing was the cause — instead of the
    // wipe happening silently inside the retry loop and leaving only the final toast to explain the outcome.
    private final java.util.List<String> clearedFieldLog = new java.util.ArrayList<>();

    /** The cleared-field log for the report, or "" when no field was ever wiped after being entered. */
    public String clearedFieldsSummary() { return clearedFieldLog.isEmpty() ? "" : String.join(" | ", clearedFieldLog); }

    // ---- navigation via the menu tab (not a direct URL) -------------------

    /** Expand the <b>IP</b> left-menu treeview and click the <b>Admission</b> link ({@code #/Admission}). */
    public boolean navigateViaMenu() {
        // Wait for the left menu to render (the SPA boots the nav a beat after login); reload-retry if the
        // menu doesn't boot on the first try (a real AngularJS SPA-boot flake under server load).
        String menuJs = "() => window.angular && [...document.querySelectorAll('li > a')].some(a=>/^\\s*IP\\s*$/i.test((a.textContent||'').trim()))";
        boolean menuUp = false;
        for (int attempt = 0; attempt < 3 && !menuUp; attempt++) {
            try {
                page.waitForFunction(menuJs, null, new Page.WaitForFunctionOptions().setTimeout(attempt == 0 ? 30000 : 20000));
                menuUp = true;
            } catch (Exception ignore) {
                System.out.println("navigateViaMenu: IP menu did not render (attempt " + (attempt + 1) + ") — reloading");
                try { page.reload(new Page.ReloadOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)); }
                catch (Exception e) { System.out.println("navigateViaMenu: reload failed - " + e.getMessage()); }
                waitForAngular(1500);
            }
        }
        waitForAngular(800);
        // Expand the IP menu (real click — its ng handler toggles the submenu).
        Object ipTagged = page.evaluate("() => { const lis=[...document.querySelectorAll('li')];"
                + " const ipLi=lis.find(li=>{ const a=li.querySelector(':scope > a'); return a && /^\\s*IP\\s*$/i.test((a.textContent||'').trim()); });"
                + " const a=ipLi&&ipLi.querySelector(':scope > a'); if(!a) return false; a.id='__ipMenuTab'; return true; }");
        if (Boolean.TRUE.equals(ipTagged)) {
            try { page.locator("#__ipMenuTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: IP menu click failed - " + e.getMessage()); }
        } else { System.out.println("navigateViaMenu: IP menu not found"); }
        waitForAngular(800);
        // Click the Admission submenu link (href → #/Admission; NOT #/AdmissionList / EmergencyAdmission).
        Object adTagged = page.evaluate("() => { const a=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('href')||'')==='#/Admission' && x.offsetParent!==null)"
                + " || [...document.querySelectorAll('a')].find(x=>/^\\s*admission\\s*$/i.test((x.textContent||'').trim()) && (x.getAttribute('href')||'')==='#/Admission'); if(!a) return false; a.id='__ipAdmitTab'; return true; }");
        if (Boolean.TRUE.equals(adTagged)) {
            try { page.locator("#__ipAdmitTab").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
            catch (Exception e) { System.out.println("navigateViaMenu: Admission click failed - " + e.getMessage()); }
        } else { System.out.println("navigateViaMenu: Admission link not found"); }
        // Wait for the admission form (Save button present + on the #/Admission route).
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDAdmission();']\") && /#\\/Admission\\b/i.test(location.hash)",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("navigateViaMenu: admission form not ready in time"); }
        waitForAngular(1500);
        return page.url().toLowerCase().contains("#/admission");
    }

    // ---- fill the admission details --------------------------------------

    /**
     * Section 1 — <b>Patient Information</b> (same as OP Registration): Nationality BEFORE ICCardType so the
     * NRIC field stays enabled; the NRIC last digit's parity matches Gender (odd=male/even=female), and
     * Gender is re-asserted after NRIC (the NRIC/ICCardType cross-check can clear it). Returns a summary.
     */
    /**
     * A fresh {DOB, NRIC} pair for a male New-IC patient: {@code dd/MM/yyyy} and the matching
     * {@code YYMMDD + place(2) + serial(4)} with an odd last digit. The birth date is part of what varies, so the
     * space is years × days × serials rather than serials alone.
     */
    private static String[] newIdentity() {
        java.util.Random r = new java.util.Random();
        int year = 1960 + r.nextInt(45);                 // 1960..2004
        int month = 1 + r.nextInt(12);
        int day = 1 + r.nextInt(28);                     // 28 keeps every month valid
        int serial = r.nextInt(10000);
        if (serial % 2 == 0) serial = (serial + 1) % 10000;          // odd => male
        String dob = String.format("%02d/%02d/%04d", day, month, year);
        String nric = String.format("%02d%02d%02d", year % 100, month, day) + "10" + String.format("%04d", serial);
        return new String[]{ dob, nric };
    }

    /**
     * The patient's nationality: {@code -Ddevhis.nationality=<adjectival name, e.g. Australian>} admits a FOREIGN
     * patient (Identification Type = Passport + Visa Details), otherwise a Malaysian on a New IC. Same switch as
     * OP Registration, so one flag drives both flows.
     */
    public static String nationality() {
        String n = System.getProperty("devhis.nationality", "").trim();
        return n.isEmpty() ? "Malaysian" : n;
    }

    /** True when this run admits a non-Malaysian — the passport/visa path. */
    public static boolean isForeign() { return !nationality().equalsIgnoreCase("Malaysian"); }

    /** Identification Type that goes with {@link #nationality()}. */
    public static String idType() { return isForeign() ? "Passport" : "New IC"; }

    public String fillPatientSection() {
        java.util.Random rnd = new java.util.Random();
        // Male "New IC" patient. NRIC = DDMMYY + place(2) + serial(4), last digit ODD (male). A fresh serial
        // each run keeps it a NEW patient (so the lookup doesn't repopulate) while staying a valid New-IC format.
        boolean male = true;
        String gender = "Male";
        String prefix = "Mr.";
        String fullName = "John Peter";
        // "Registration.FamilyName" is the PASSPORT NO. box on this form, not a surname. A foreign patient
        // therefore needs a passport-shaped value here — and it must go in with the rest of the identity, because
        // filling it on its own later fires the passport lookup and clears the form.
        String familyName = isForeign()
                ? "P" + String.format("%07d", Math.abs(System.nanoTime() % 10000000))
                : "Peter";
        // The birth date VARIES too. With a fixed 01/01/1985 the NRIC was "850101 10 ####" — only 10,000 possible
        // values, so runs kept colliding with a patient already on file ("This NRIC is already registered.").
        String[] identity = newIdentity();
        String dob = identity[0];
        String nric = identity[1];
        String mobile = "12" + String.format("%08d", rnd.nextInt(90000000) + 10000000); // 10 digits
        String email = "johnpeter" + (rnd.nextInt(9000) + 1000) + "@example.com";
        String postCode = "50000";

        // ---- ATOMIC fill (exactly like the manual browser run that saved AdmissionID 10450): do the masters,
        // then identity+contact+address, in TWO big ASYNC evaluates with the internal waits — filling field-by-
        // field in many small evaluates let the patient-lookup fire between them and reset values.
        page.evaluate("async (a) => {"
                + " const setSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " setSel('Registration.NationalityID',a.nationality); setSel('Registration.PrefixID',a.prefix); setSel('Registration.GenderID',a.gender);"
                + " await new Promise(r=>setTimeout(r,1600));"  // race/religion load after nationality
                + " setSel('Registration.RaceID','Malay'); setSel('Registration.ReligionID','Islam'); setSel('Registration.MaritalStatusID','Single'); setSel('Registration.BloodGroupID','O'); setSel('Registration.ICCardTypeID',a.idType); }",
                java.util.Map.of("prefix", prefix, "gender", gender,
                        "nationality", nationality(), "idType", idType()));
        waitForAngular(300);
        // ASSERT the nationality with a real selectOption. The JS setSel above does not bind it on this screen —
        // the form was left on "--Select--", so the app could not tell whether to demand an NRIC or a passport and
        // Save asked for BOTH. Nationality goes first: it decides the Identification Type below.
        // WAIT for the list to load first. The NOK select took "Australian" in the same run, so the value exists —
        // the patient's select simply had nothing but the placeholder when it was set, and the form stayed on
        // "--Select--".
        try {
            page.waitForFunction("() => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.NationalityID');"
                    + " return !!(s && s.options.length>1); }",
                    null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(20000));
        } catch (Exception ignore) { System.out.println("fillPatientSection: the Nationality list did not populate in time"); }
        String natSet = selectNationalityVerified("Registration.NationalityID", nationality());
        System.out.println("fillPatientSection: Nationality = " + natSet);
        waitForAngular(800);
        // Ensure Identification Type (ICCardType) actually stuck as "New IC" — a slow-loading dropdown makes the
        // setSel above silently no-op, which removes the 12-digit NRIC field and blocks Save with
        // "Please Enter NRIC / Passport No". Wait for its options, then select "New IC" with verify-and-retry.
        try {
            page.waitForFunction("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); return e && e.options.length>1; }",
                    null, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillPatientSection: ICCardType dropdown did not populate in time"); }
        for (int a = 0; a < 4; a++) {
            boolean set = Boolean.TRUE.equals(page.evaluate("(want) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); if(!e) return false; const c=angular.element(e).controller('ngModel'); const v=c?c.$modelValue:null; return v!=null && v!=='' && new RegExp(want.replace(/\\s+/g,'\\\\s*'),'i').test(((e.options[e.selectedIndex]||{}).textContent)||''); }", idType()));
            if (set) break;
            System.out.println("fillPatientSection: Identification Type not '" + idType() + "' yet — retrying (attempt " + (a + 1) + ")");
            page.evaluate("(want) => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.ICCardTypeID'); if(!e) return; let i=[...e.options].findIndex(o=>new RegExp(want.replace(/\\s+/g,'\\\\s*'),'i').test((o.textContent||'').trim())); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} }", idType());
            waitForAngular(400);
        }
        page.evaluate("async (a) => {"
                + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && (x.offsetParent!==null || !x.getAttribute('maxlength'))); if(!e) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                + " const setSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " setInp('Registration.FirstName',a.first); setInp('Registration.FamilyName',a.family);"
                + " if(a.expiry) setInp('Registration.PassportExpirydate',a.expiry);"
                + " const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12'); if(nr){ const c=angular.element(nr).controller('ngModel'); nr.value=a.nric; if(c){c.$setViewValue(a.nric);c.$render();} nr.dispatchEvent(new Event('input',{bubbles:true})); nr.dispatchEvent(new Event('change',{bubbles:true})); }"
                + " setInp('Registration.DateOfBirth',a.dob); setSel('Registration.MobileCountryCode','60'); setInp('Registration.MobileNo',a.mobile); setInp('Registration.Email',a.email); setInp('Registration.ResPinCode',a.pc); setInp('Registration.ResHouseNo','12'); setInp('Registration.ResStreet','Jalan Test'); setInp('Registration.ResAddress','No 12, Jalan Test');"
                + " await new Promise(r=>setTimeout(r,3000)); }",  // post code auto-fills city/state/country
                java.util.Map.of("first", fullName, "family", familyName, "nric", nric, "dob", dob, "mobile", mobile,
                        "email", email, "pc", postCode,
                        // Passport Expiry travels with the same fill for a foreigner; "" leaves it untouched.
                        "expiry", isForeign() ? java.time.LocalDate.now().plusYears(5)
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")) : ""));
        waitForAngular(400);
        // CHECK last, and only re-select when it actually drifted. On a slow environment the Nationality master can
        // arrive after this fill and revert the selection to the environment default. Re-selecting unconditionally
        // is NOT safe here: the ng-change fires the patient lookup, which wipes the form that was just filled
        // (name, DOB, gender and address all back to "--Select--"), so read first and only act on a real drift.
        String natNow = currentNationalityText();
        String w = nationality().toLowerCase().replaceAll("[^a-z]", "");
        String g = natNow.toLowerCase().replaceAll("[^a-z]", "");
        boolean readable = !natNow.isEmpty();
        boolean matches = readable
                && (g.startsWith(w.substring(0, Math.min(5, w.length()))) || w.startsWith(g));
        if (!readable) {
            // Could not READ it — the section is mid-render or collapsed. That is NOT evidence of drift, and
            // re-selecting on it fires the patient lookup which wipes the form that was just filled (the whole
            // patient section came back "--Select--" and the run aborted). Leave it; the pre-save bind sweep
            // re-binds any mandatory select that really is empty.
            System.out.println("fillPatientSection: Nationality not readable right now — leaving it alone");
        } else if (!matches) {
            System.out.println("fillPatientSection: Nationality drifted to \"" + natNow + "\" — re-asserting");
            selectNationalityVerified("Registration.NationalityID", nationality());
        }
        // A FOREIGN patient is identified by the PASSPORT, not the NRIC: put a unique passport number and an
        // expiry into the passport controls (Registration.FamilyName IS the Passport No. box on this form) and
        // skip the NRIC verification below, which only applies to a New IC patient.
        if (isForeign()) {
            // The passport went in with the rest of the identity, in the SAME atomic fill. Typing it separately
            // afterwards fired the passport LOOKUP, which cleared the whole form — name, DOB, nationality, gender
            // and address all reverted to "--Select--" with only the passport left behind.
            pFamilyName = familyName;
            System.out.println("fillPatientSection: foreign patient (" + nationality() + ") — Passport " + familyName);
            pGender = gender; pFullName = fullName; pNric = ""; pDob = dob;
            pMobile = mobile; pEmail = email; pPostCode = postCode;
            return "Patient: " + prefix + " " + fullName + " | " + gender + " | " + dob + " | " + nationality()
                    + " | Passport " + familyName + " | Mobile " + mobile + " | Email " + email;
        }
        // Verify the NRIC (Identification No) actually committed to the 12-digit field; retry if not (it gets
        // dropped when ICCardType wasn't "New IC" at fill time). This is what blocks Save with "Please Enter NRIC".
        for (int a = 0; a < 4; a++) {
            boolean nricSet = Boolean.TRUE.equals(page.evaluate("(v) => [...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].some(x=>x.value===v)", nric));
            if (nricSet) break;
            System.out.println("fillPatientSection: NRIC not committed yet — retrying (attempt " + (a + 1) + ")");
            page.evaluate("(v) => { const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12') || [...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")][0]; if(nr){ const c=angular.element(nr).controller('ngModel'); nr.value=v; if(c){c.$setViewValue(v);c.$render();} nr.dispatchEvent(new Event('input',{bubbles:true})); nr.dispatchEvent(new Event('change',{bubbles:true})); } }", nric);
            waitForAngular(400);
        }
        pGender = gender; pFullName = fullName; pFamilyName = familyName; pNric = nric; pDob = dob;
        pMobile = mobile; pEmail = email; pPostCode = postCode;
        return "Patient: " + prefix + " " + fullName + " | " + gender + " | " + dob + " | NRIC " + nric + " | Mobile " + mobile + " | Email " + email;
    }

    /**
     * Read the identity/contact fields the Admission form ALREADY carries in — from an existing
     * Reservation/Pre-Admission, not a blank walk-in — into the {@code p*} fields
     * {@link #restoreWipedContactFields} / {@link #satisfyIdentityToast} use for later recovery, WITHOUT writing
     * anything. Returns a one-line summary, or {@code ""} if the form looks unbound (no First Name/NRIC yet) —
     * callers should fall back to {@link #fillPatientSection()} in that case.
     *
     * <p>Confirmed live (Chrome DevTools on the real app): opening {@code #/Admission} from an existing
     * Reservation pre-fills NRIC, Nationality and Gender from the REAL patient record. {@link #fillPatientSection()}
     * unconditionally overwrites that with a brand-new synthetic identity ("John Peter", a fresh NRIC,
     * "Malaysian") — correct for a genuine walk-in, but here it starts a tug-of-war with the app's own async
     * "patient lookup by NRIC/Mobile", which keeps re-fetching and re-asserting the REAL record over the fake
     * one (confirmed live: a run logged "asked for 'Malaysian' but Registration.NationalityID reads 'Myanmar'" —
     * the real patient's actual nationality winning the race). Reproduced the specific mechanism live in
     * isolation too: changing {@code Registration.MobileNo} alone fires that lookup and blanks First Name
     * ~900ms later — the mechanism behind a same-toast "Please Enter First Name!" loop that never resolved
     * across 5 retries. Leaving the real data untouched avoids ever triggering that lookup.</p>
     */
    public String captureExistingIdentity() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const g=(ng)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng); return e?e.value:''; };"
                + " const gs=(ng)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e && e.selectedIndex>=0 ? norm(e.options[e.selectedIndex].textContent) : ''; };"
                + " return [g('Registration.FirstName'), g('Registration.NationalId'), g('Registration.FamilyName'), g('Registration.MobileNo'), g('Registration.Email'), g('Registration.DateOfBirth'), gs('Registration.GenderID')].join('\\u0001'); }");
        String[] p = (r == null ? "" : r.toString()).split("\u0001", -1);
        String first = p.length > 0 ? p[0].trim() : "";
        String nric = p.length > 1 ? p[1].trim() : "";
        String family = p.length > 2 ? p[2].trim() : "";
        String mobile = p.length > 3 ? p[3].trim() : "";
        String email = p.length > 4 ? p[4].trim() : "";
        String dob = p.length > 5 ? p[5].trim() : "";
        String gender = p.length > 6 ? p[6].trim() : "";
        if (first.isEmpty() && nric.isEmpty()) return "";
        pFullName = first; pNric = nric; pFamilyName = family; pMobile = mobile; pEmail = email; pDob = dob; pGender = gender;
        return "Patient already bound (carried from Reservation): " + first + " | NRIC " + nric + " | Mobile " + mobile
                + " | Gender " + gender + " | DOB " + dob;
    }

    /**
     * Section — <b>NOK / Guarantor</b> (Next of Kin). Same {@code Registration.Kin*} fields as OP Registration
     * (the form embeds the same registration partial). Fills Title, Name, Relationship, Mobile (+country code),
     * NRIC, Occupation, Country, ticks "Same As Patient Address", then clicks <b>Add</b> ({@code AddKinDetails})
     * so the kin row lands in the grid. Retries (the Add no-ops if a field hasn't committed). Returns a summary.
     */
    public String fillNokSection() {
        java.util.Random rnd = new java.util.Random();
        boolean kinMale = rnd.nextBoolean();
        String kinTitle = kinMale ? "Mr." : "Mrs.";
        String[] mN = {"Tom Baker", "Chris Evans", "Paul Test", "George King"};
        String[] fN = {"Jane Doe", "Lucy Test", "Grace Hall", "Rose Adams"};
        String kinName = (kinMale ? mN : fN)[rnd.nextInt(4)];
        String kinFamily = kinName.substring(kinName.lastIndexOf(' ') + 1);
        String kinRel = new String[]{"Father", "Grandfather", "Biological Child", "Adopted Child"}[rnd.nextInt(4)];
        String kinNric = String.format("%02d%02d%02d", (1960 + rnd.nextInt(30)) % 100, 1 + rnd.nextInt(12), 1 + rnd.nextInt(28))
                + "07" + String.format("%04d", rnd.nextInt(9000) + 1000);
        String kinMobile = "1" + String.format("%08d", rnd.nextInt(90000000) + 10000000);
        String kinEmail = kinFamily.toLowerCase() + (rnd.nextInt(9000) + 1000) + "@example.com";
        // Wait for the kin dropdowns to populate (setSel no-ops on unloaded options).
        try {
            page.waitForFunction("() => ['Registration.KinTitleID','Registration.KinRelationID','Registration.KinMobileCountryCode']"
                    + ".every(ng => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); return e && e.options.length>1; })",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("fillNokSection: kin dropdowns not fully populated — proceeding"); }
        boolean added = false;
        final int kinRowsBefore = kinGridRowCount();
        for (int attempt = 0; attempt < 4 && !added; attempt++) {
            setSelLike("Registration.KinTitleID", kinTitle);
            setInput("Registration.KinName", kinName);
            // KinFamilyName is the kin PASSPORT NO. control, not a surname (same as Registration.FamilyName on the
            // patient). It must stay EMPTY for a Malaysian kin — only a foreign kin (below, guarded by
            // isForeign()) gets a passport value here. Filling it unconditionally was stamping a fake passport
            // number onto Malaysian NOK rows even though NRIC is the correct identifier for them.
            // The kin follows the PATIENT's nationality — a foreign patient with a hardcoded Malaysian kin is
            // inconsistent test data (same fix as OP Registration). VERIFY it: setSelLike falls back to the FIRST
            // option when its match misses, which quietly gave the kin "Afghan" (and a passport) on a run that
            // asked for Australian.
            kinNationalityActual = selectNationalityVerified("Registration.NOKnationalid", nationality());
            // A FOREIGN kin is identified by a passport (Registration.KinFamilyName is that box, not a surname),
            // so filling only the NRIC leaves the row un-addable.
            if (isForeign()) {
                String kinPassport = "K" + String.format("%07d", Math.abs(System.nanoTime() % 10000000));
                page.evaluate("(v) => { const A=window.angular;"
                        + " const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.KinFamilyName' && x.offsetParent!==null);"
                        + " if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v;"
                        + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                        + " e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); e.dispatchEvent(new Event('blur',{bubbles:true})); }", kinPassport);
                waitForAngular(400);
                // Real keystrokes here too — the JS set above leaves the model empty on this screen.
                System.out.println("fillNokSection: foreign kin — Passport "
                        + typeReal("Registration.KinFamilyName", kinPassport));
            }
            setInput("Registration.KinNationalId", kinNric);
            setSelLike("Registration.KinRelationID", kinRel);
            setInput("Registration.KinMobileNo", kinMobile);
            setInput("Registration.KinEmail", kinEmail);
            setSelLike("Registration.KinOccupationID", "Education");
            setSelTxt("Registration.KinMobileCountryCode", "60");
            // KinCountryID is where the kin LIVES, not what it IS — the nationality is NOKnationalid below, and
            // its ng-change (fnsetCountrywithNationalitywiseNOK) derives this field on its own. Forcing the
            // nationality in here is what stopped a foreign kin being added at all: "Australia" has no States in
            // the master data, and since HIN-3720 AddKinDetails validates Country -> State -> City. Fill it only
            // when the derivation left it empty, preferring the country of the address just copied across.
            ensureKinAddressCountry();
            // "Same As Patient Address" checkbox — real click (synthetic doesn't fire Angular).
            Object chkTagged = page.evaluate("()=>{const c=[...document.querySelectorAll('input[type=checkbox]')].find(x=>x.getAttribute('ng-model')==='chkSameasPatAddr'); if(!c || c.checked) return false; c.id='__nokSameAddr'; return true;}");
            if (Boolean.TRUE.equals(chkTagged)) {
                try { page.locator("#__nokSameAddr").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
                page.evaluate("()=>{const e=document.getElementById('__nokSameAddr'); if(e) e.removeAttribute('id');}");
            }
            waitForAngular(700);
            // "Same As Patient Address" does not bring State/City across here — AddKinDetails then refuses with
            // "Please Select State!" and the row is never added (added=false on every run so far).
            ensureKinStateAndCity();
            // RE-ASSERT the nationality right before Add — Same-As-Address, Country and City each fire a cascade
            // that can drop it, and AddKinDetails captures whatever is on the form at that instant (the same
            // re-assert OP Registration does). Report what it reads at the moment of Add, not at fill time.
            // ...but only when it actually reads empty. Re-selecting it REWRITES the kin address Country, which
            // wipes the State/City chosen a moment ago and leaves the Add refusing with "Please Select State" —
            // added=false on every KS run. Read first; repair the address after, on the runs that did re-select.
            kinNationalityActual = kinSelectedText("Registration.NOKnationalid");
            if (kinNationalityActual.isEmpty()) {
                kinNationalityActual = selectNationalityVerified("Registration.NOKnationalid", nationality());
                ensureKinAddressCountry();
                ensureKinStateAndCity();
            }
            System.out.println("fillNokSection: NOK nationality at Add = " + kinNationalityActual);
            waitForAngular(600);
            // Listen for what Add answers. "added=false" on its own says nothing about WHY the row was refused —
            // the screen names the missing field in a toast, and that is the fastest way to the actual cause.
            page.evaluate("() => { window.__nokToasts=[]; if(window.__nokObs) window.__nokObs.disconnect();"
                    + " try{ if(window.toastr) toastr.clear(); }catch(e){}"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ try{ el.remove(); }catch(e){} });"
                    + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__nokToasts.includes(t)) window.__nokToasts.push(t); }); };"
                    + " window.__nokObs=new MutationObserver(grab); window.__nokObs.observe(document.body,{childList:true,subtree:true}); }");
            // Add-kin — real click + scope-call fallback.
            Object addTagged = page.evaluate("()=>{const b=[...document.querySelectorAll('button,a')].find(x=>/AddKinDetails/.test(x.getAttribute('ng-click')||'') && x.offsetParent!==null); if(!b) return false; b.id='__nokAddBtn'; return true;}");
            if (Boolean.TRUE.equals(addTagged)) {
                try { page.locator("#__nokAddBtn").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); } catch (Exception ignore) {}
                page.evaluate("()=>{const e=document.getElementById('__nokAddBtn'); if(e) e.removeAttribute('id');}");
            }
            page.evaluate("()=>{ let done=false; document.querySelectorAll('*').forEach(el=>{ if(done)return; try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ if(typeof x.AddKinDetails==='function'){ x.$apply(()=>{ try{ x.AddKinDetails(x.Registration); }catch(e){ try{ x.AddKinDetails(); }catch(e2){} } }); done=true; break; } x=x.$parent; } }catch(e){} }); }");
            waitForAngular(1000);
            // Check the KIN grid, and demand a row was GAINED. Scanning every table on the page matched other
            // grids (and the patient's own details), so a kin that never reached KinDetailsList reported as added
            // — and the save was then rejected on the next-of-kin.
            boolean inKinGrid = Boolean.TRUE.equals(page.evaluate("(a) => { const fam=(a[0]||'').toLowerCase(), nric=(a[1]||'');"
                    + " const t=[...document.querySelectorAll('table')].find(x=>/relation|guarantor|nationality/i.test((x.querySelector('thead')||{}).innerText||''));"
                    + " if(!t) return false;"
                    + " return [...t.querySelectorAll('tbody tr')].some(r=>{ const tx=(r.textContent||''); const tl=tx.toLowerCase();"
                    + "   if(!tx.trim() || /no\\s*records|no\\s*data/i.test(tx)) return false;"
                    + "   return (fam && tl.includes(fam)) || (nric && tx.replace(/\\s+/g,'').includes(nric)); }); }",
                    java.util.Arrays.asList(kinFamily, kinNric)));
            int kinRowsNow = kinGridRowCount();
            // A GAINED ROW is the proof the Add worked. The text match is a bonus: on KLG the row carries neither
            // the family name (that control is the kin PASSPORT, cleared for a Malaysian) nor a searchable NRIC,
            // so demanding it too reported added=false over a kin that was sitting in the grid — and the retries
            // then added it again.
            added = kinRowsNow > kinRowsBefore || inKinGrid;
            Object nokToasts = page.evaluate("() => JSON.stringify(window.__nokToasts||[])");
            System.out.println("fillNokSection: attempt " + (attempt + 1) + " — kin rows "
                    + kinRowsBefore + " -> " + kinRowsNow + ", row matched=" + inKinGrid + ", added=" + added
                    + ", Add said " + nokToasts);
        }
        waitForAngular(300);
        return "NOK: " + kinTitle + " " + kinName + " (" + kinRel + ") | Nationality " + kinNationalityActual
                + " | NRIC " + kinNric + " | Mobile " + kinMobile + " | added=" + added;
    }

    /**
     * Section — <b>Payor Information</b>. Open the Payor accordion ({@code FillSponserDropDown}) so the default
     * payor GRID ROW loads, then CLICK that row ({@code EditSponser($index)}) which auto-fills the payor form
     * (Payor Mode = Self / SELFPAY CASH). Tick the Insurer "Self" radio and back-fill any empty mandatory payor
     * select. Returns a summary incl. the resulting Payor Mode.
     */
    public String fillPayorSection() {
        page.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>x.getAttribute('ng-click')==='FillSponserDropDown();' && x.offsetParent!==null); if(a) a.click(); }");
        // Payor data loads async and can be slow — WAIT (up to 20s) until the payor table actually has a DATA
        // row (not "No records"), re-clicking the accordion if needed, before clicking the row.
        String rowLoaded = "() => { const t=[...document.querySelectorAll('table')].find(x=>/PAYOR MODE|PRICING POLICY/i.test((x.querySelector('thead')||{}).innerText||'')); if(!t) return false;"
                + " return [...t.querySelectorAll('tbody tr')].some(r=>(r.textContent||'').trim() && !/no records/i.test(r.textContent||'')); }";
        boolean loaded = false;
        for (int attempt = 0; attempt < 3 && !loaded; attempt++) {
            try { page.waitForFunction(rowLoaded, null, new Page.WaitForFunctionOptions().setTimeout(8000)); loaded = true; }
            catch (Exception ignore) {
                System.out.println("fillPayorSection: payor row not loaded (attempt " + (attempt + 1) + ") — re-opening accordion");
                page.evaluate("() => { const a=[...document.querySelectorAll('a,button')].find(x=>x.getAttribute('ng-click')==='FillSponserDropDown();' && x.offsetParent!==null); if(a) a.click(); }");
                waitForAngular(1500);
            }
        }
        waitForAngular(600);
        // Click the payor grid row (its EditSponser handler auto-fills the payor form).
        page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/PAYOR MODE|PRICING POLICY/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return; const row=t.querySelector('tbody tr'); if(!row) return;"
                + " const cell=[...row.querySelectorAll('td')].find(td=>/self|selfpay|cash/i.test(td.textContent||'')) || row.querySelector('td') || row;"
                + " const sc=angular.element(cell).scope(); if(sc && typeof sc.EditSponser==='function'){ sc.$apply(function(){ sc.EditSponser(sc.$index); }); } else { cell.click(); } }");
        waitForAngular(600);
        // Ensure Insurer = Self (real click) + back-fill any empty mandatory payor select.
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
        Object payorMode = page.evaluate("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.receivabletypeid'); return e?((e.options[e.selectedIndex]||{}).text||'').trim():''; }");
        return "Payor: Mode = " + (payorMode == null ? "" : payorMode.toString()) + " (Self, row auto-filled)";
    }

    /**
     * Section 2 — <b>Admission Information</b>. Set Location + Department (loads the doctor list), wait, then
     * in ONE apply set every required admission field to known-good master-data IDs (the lists load
     * async/inconsistently; the Save validation only checks non-empty and the server accepts these IDs).
     * Non-Presence=1 skips physical Bed selection. Anchor the final apply to an ADMISSION-section element.
     */
    public String fillAdmissionSection() {
        page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Admission.AdmissionLocationID']\") || document.querySelector(\"input[ng-model='Registration.FirstName']\"); const sc=angular.element(e).scope(); let s=sc, adm=null; for(let i=0;i<15&&s;i++){ if(!adm&&s.Admission)adm=s.Admission; s=s.$parent; }"
                + " sc.$apply(function(){ if(adm){ adm.AdmissionLocationID=1; } if(typeof sc.fnSetDepartment==='function') sc.fnSetDepartment();"
                + "   if(adm){ adm.DepartmentID=1; } if(typeof sc.fnDeparmentChange==='function') sc.fnDeparmentChange(); if(typeof sc.onEmergencyOnCallChange==='function') sc.onEmergencyOnCallChange(); }); }");
        try {
            page.waitForFunction("() => { let ok=false; document.querySelectorAll('*').forEach(el=>{ if(ok)return; try{ const s=angular.element(el).scope(); if(s && Array.isArray(s.drpDoctor) && s.drpDoctor.length) ok=true; }catch(e){} }); return ok; }",
                    null, new Page.WaitForFunctionOptions().setTimeout(12000));
        } catch (Exception ignore) { System.out.println("fillAdmissionSection: doctor list not loaded in time"); }
        waitForAngular(600);
        Object rest = page.evaluate("() => { const e=document.querySelector(\"select[ng-model='Admission.DepartmentID']\") || document.querySelector(\"select[ng-model='Admission.AdmissionLocationID']\"); const sc=angular.element(e).scope(); let s=sc, adm=null, vis=null; for(let i=0;i<15&&s;i++){ if(!adm&&s.Admission)adm=s.Admission; if(!vis&&s.Visit)vis=s.Visit; s=s.$parent; }"
                + " let drd=null; document.querySelectorAll('*').forEach(el=>{ try{ const s2=angular.element(el).scope(); if(s2 && !drd && Array.isArray(s2.drpDoctor) && s2.drpDoctor.length) drd=s2.drpDoctor; }catch(e){} });"
                + " const docPick=(drd&&drd.find(d=>d.value)&&drd.find(d=>d.value).value)||15300;"
                + " sc.$apply(function(){ if(adm){ adm.AdmissionLocationID=1; adm.DepartmentID=1; adm.DoctorID=docPick; adm.AttendingDoctorID=docPick;"
                + "   adm.AdmissionTypeID=4; adm.PatientSourceID=1; adm.BedClassID=21; adm.WardID=58; adm.BillingClassID=4;"
                // Non-Presence=0 so the physical Vacant Bed Selection grid is required — selectVacantBed() then
                // picks a real free bed. If no ward has a vacant bed, selectVacantBed() flips this back to 1.
                + "   adm.NonPresenceAdmission=0; adm.AdmissionPurposeID=3; } if(vis) vis.EncounterTypeID=3; });"
                + " return 'Dept=1 | AttendingDoctor='+docPick+' | Type=4 | Source=1 | Billing=4 | Purpose=3'; }");
        waitForAngular(800);
        String doc = ensureDoctorSelected();
        // The block above assigns HARD-CODED ids (DepartmentID=1, AdmissionTypeID=4, …) straight onto the scope and
        // then reports "Dept=1 | AttendingDoctor=… | Type=4 …" whether or not any of it bound. Those ids come from
        // devhis; on KLG they match no option, so Angular renders "--Select--" and Save silently does nothing —
        // IUDAdmission's own validation rejects the form without raising a toast, and the run reported the useless
        // "No success toast appeared". Bind what did not take, and report what is REALLY selected.
        String fixed = bindEmptyMandatorySelects();
        return "Admission: " + (rest == null ? "" : rest.toString().trim()) + " | " + doc
                + (fixed.isEmpty() ? "" : " | bound-after-assign: " + fixed);
    }

    /**
     * Select a real option in every visible MANDATORY dropdown that is still empty, and verify the model took.
     *
     * <p>Assigning an id to the scope only works when that id exists in the option list of the environment in front
     * of us. Where it does not, the control shows {@code --Select--} and no amount of re-assigning the same number
     * helps — so pick the first genuine option from the DOM instead, and read the bound model back, because Angular
     * silently discards a value that is not in the list.</p>
     *
     * @return the fields it had to fix ("" when everything was already bound)
     */
    public String bindEmptyMandatorySelects() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery;"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const chosen=e=>{ const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const out=[];"
                + " [...document.querySelectorAll('select')].filter(e=>e.offsetParent!==null)"
                + "  .filter(e=>(e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0)"
                + "  .forEach(e=>{ const lab=labelOf(e);"
                + "    const mandatory=/\\*/.test(lab) || e.hasAttribute('required') || e.hasAttribute('ng-required');"
                + "    if(!mandatory || chosen(e)) return;"
                + "    const real=[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + "    if(!real.length){ out.push(lab.replace(/\\*/g,'').trim()+'=(no options)'); return; }"
                + "    e.value=real[0].value; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "    try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + "    out.push(lab.replace(/\\*/g,'').trim()+'='+(chosen(e)||'(did not bind)')); });"
                + " return out.join(', '); }");
        String s = r == null ? "" : r.toString();
        if (!s.isEmpty()) { System.out.println("bindEmptyMandatorySelects: " + s); waitForAngular(900); }
        return s;
    }

    /**
     * Walk <b>Registration Department</b> until one leaves a selectable <b>Doctor</b>, then pick that doctor.
     *
     * <p>Departments are listed alphabetically and the early ones (ALLIED HEALTH …) have no doctors attached at all,
     * so binding "the first real option" satisfies the department field and immediately breaks Save with
     * <i>"Please Select Doctor!"</i>. Only changing department can produce a doctor. Each candidate is selected, its
     * cascade allowed to reload, and the doctor list checked; the first department that yields one wins.</p>
     */
    public String walkDepartmentForDoctor() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(x=>setTimeout(x,ms));"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const real=e=>e?[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))):[];"
                + " const chosen=e=>{ const o=e&&e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const doc=()=>document.querySelector(\"select[ng-model='Admission.DoctorID']\");"
                + " const att=()=>document.querySelector(\"select[ng-model='Admission.AttendingDoctorID']\");"
                + " const dept=[...document.querySelectorAll('select')].filter(s=>s.offsetParent!==null)"
                + "   .find(s=>/registration\\s*depart/i.test(labelOf(s)))"
                + "   || document.querySelector(\"select[ng-model='Admission.DepartmentID']\");"
                + " if(!dept) return resolve('walkDepartmentForDoctor: no Registration Department field');"
                + " const opts=real(dept); let tried=0;"
                + " for(const o of opts){ tried++;"
                + "   dept.value=o.value; fire(dept);"
                + "   let d=null; for(let k=0;k<12;k++){ await sleep(500); d=doc(); if(d && real(d).length) break; }"
                + "   d=doc(); const rs=real(d);"
                + "   if(rs.length){ d.value=rs[0].value; fire(d); await sleep(400);"
                + "     const a=att(); if(a && real(a).length && !chosen(a)){ a.value=real(a)[0].value; fire(a); await sleep(300); }"
                + "     return resolve('walkDepartmentForDoctor: Department='+norm(o.textContent)+' | Doctor='+(chosen(d)||'?')"
                + "       +' | AttendingDoctor='+(a?(chosen(a)||'(unset)'):'(no field)')+' (tried '+tried+')'); } }"
                + " resolve('walkDepartmentForDoctor: NO department has a doctor attached (tried '+tried+')'); })");
        waitForAngular(800);
        return r == null ? "" : r.toString();
    }

    /**
     * Every visible mandatory (*) field still EMPTY — the list the app refuses to give us.
     *
     * <p>{@code IUDAdmission()} rejects an incomplete form without a toast, so a failed save reported only "No
     * success toast appeared" and each missing field cost a full run to find. This names them all at once.</p>
     */
    /**
     * The fields the screen ITSELF marked as missing on the last blocked save.
     *
     * <p>Newer builds (KS: hotfix/ValidationIssues, 09-08-2026) aggregate the mandatory check and tag every
     * offender with {@code has-error}. That is the app's own verdict; our asterisk-scanning fallback misreads
     * select2-wrapped fields as empty, so on that build it named four payor fields when the app wanted one.
     * Returns "" on older builds, where {@link #describeEmptyMandatory()} remains the only signal.</p>
     */
    public String appFlaggedMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const out=[];"
                + " document.querySelectorAll('.has-error').forEach(g=>{"
                + "   const lab=g.querySelector('label'); const ctl=g.querySelector('select,input,textarea');"
                + "   const name=norm(lab?lab.textContent:'') || (ctl?(ctl.getAttribute('ng-model')||ctl.name||ctl.id):'')"
                + "     || norm(g.textContent).slice(0,30);"
                + "   const model=ctl?(ctl.getAttribute('ng-model')||''):'';"
                + "   const val=ctl? (ctl.tagName==='SELECT'? norm((ctl.options[ctl.selectedIndex]||{}).textContent) : norm(ctl.value)) : '';"
                + "   out.push(name+(model?'['+model+']':'')+'=\"'+val+'\"'); });"
                + " return [...new Set(out)].join(', '); }");
        return r == null ? "" : r.toString();
    }

    public String describeEmptyMandatory() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const labelOf=e=>{ let n=e.closest('.form-group,.form-line,.row,.col-sm-2,.col-sm-3,.col-sm-4,.col-sm-6,.col-md-2,.col-md-3,.col-md-4,.col-md-6,td,div');"
                + "   for(let i=0;i<5&&n;i++){ const l=n.querySelector('label,.control-label'); if(l && norm(l.textContent)) return norm(l.textContent); n=n.parentElement; } return ''; };"
                + " const out=[];"
                + " [...document.querySelectorAll('input,select,textarea')]"
                + "  .filter(e=>e.offsetParent!==null && e.type!=='hidden' && e.type!=='button' && e.type!=='checkbox' && e.type!=='radio')"
                + "  .filter(e=>(e.getAttribute('ng-model')||'').indexOf('colFilter')<0 && (e.getAttribute('ng-model')||'').indexOf('grid.')<0)"
                // Skip what the user cannot fill anyway: Episode No is generated on save, and select2 hides its real
                // <select> behind a rendered span. Reporting those as "empty" produced a list naming Payor Mode,
                // Priority and Location as missing when the screenshot showed them plainly filled.
                + "  .filter(e=>!e.disabled && !e.readOnly && !/select2/i.test(e.className||''))"
                + "  .forEach(e=>{ const lab=labelOf(e);"
                + "    if(!(/\\*/.test(lab) || e.hasAttribute('required') || e.hasAttribute('ng-required'))) return;"
                // No ng-model means the label was matched to the wrong control by proximity — do not accuse it.
                + "    if(!(e.getAttribute('ng-model')||'').trim()) return;"
                + "    let v=(e.value||'').trim();"
                + "    if(e.tagName==='SELECT'){ const o=e.options[e.selectedIndex]; v=o?norm(o.textContent):''; if(/^-*\\s*select\\s*-*$/i.test(v)) v=''; }"
                + "    if(!v) out.push(lab.replace(/\\*/g,'').trim()+'['+(e.getAttribute('ng-model')||'?')+']'); });"
                + " return out.join(', '); }");
        return r == null ? "" : r.toString();
    }

    /**
     * <b>Doctor</b> ({@code Admission.DoctorID}) — the field Save's "Please Select Doctor!" guard checks. It is a
     * SEPARATE dropdown from Attending Doctor ({@code Admission.AttendingDoctorID}): it is filtered by Department /
     * Sub Department and so carries its own short option list. Assigning the Attending Doctor's id to it does NOT
     * stick — Angular resets the model to 0 because that id isn't among its options — so pick from its OWN list via
     * selectedIndex + change. Its options load async after the department change, so poll for a real one.
     * Returns the selected doctor (or why it could not be selected).
     */
    public String ensureDoctorSelected() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const real=sel=>[...sel.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " let sel=null;"
                + " for(let w=0;w<20;w++){ sel=document.querySelector(\"select[ng-model='Admission.DoctorID']\"); if(sel && real(sel)>=0) break; await new Promise(r=>setTimeout(r,500)); }"
                + " if(!sel) return 'Doctor=(no select)';"
                + " const i=real(sel); if(i<0) return 'Doctor=(no options)';"
                + " sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " await new Promise(r=>setTimeout(r,600));"
                + " let val=null; document.querySelectorAll('*').forEach(el=>{ if(val)return; try{ const s=window.angular.element(el).scope(); let x=s; for(let k=0;k<15&&x;k++){ if(x.Admission){ val=x.Admission.DoctorID; return; } x=x.$parent; } }catch(e){} });"
                + " return 'Doctor='+norm(sel.options[i].textContent)+' (DoctorID='+JSON.stringify(val)+')'; }");
        waitForAngular(400);
        return r == null ? "Doctor=(null)" : r.toString();
    }

    /**
     * <b>Registration Department</b> ({@code Admission.DepartmentID}) — the field Save's "Please Select Department
     * First Then Doctor !" guard checks. {@code fillAdmissionSection()} assigns a hardcoded id (1) onto the scope,
     * which mostly binds but was seen live (2026-08-24) to leave the select on "--Select--" on an otherwise
     * identical run — a silent scope-assignment failure of the same shape documented for the Doctor field.
     * Verifies the select actually shows a real option and, if not, picks the first real one from its own list.
     * Call this BEFORE {@link #ensureDoctorSelected()} — changing Department reloads and clears the Doctor list.
     */
    public String ensureDepartmentSelected() {
        Object r = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const real=sel=>[...sel.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent)));"
                + " const chosen=e=>{ const o=e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const sel=document.querySelector(\"select[ng-model='Admission.DepartmentID']\");"
                + " if(!sel) return 'Department=(no select)';"
                + " const already=chosen(sel); if(already) return 'Department='+already+' (already bound)';"
                + " const opts=real(sel); if(!opts.length) return 'Department=(no options)';"
                + " sel.value=opts[0].value; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}}"
                + " return 'Department='+(chosen(sel)||'(did not bind)')+' (re-bound)'; }");
        waitForAngular(800);   // let the Doctor list reload off this change before anything re-asserts it
        return r == null ? "Department=(null)" : r.toString();
    }

    /** What the bed step ended up doing. */
    public String lastBedInfo = "";

    /**
     * Set <b>Room Type</b> ({@code select[ng-model='Admission.BedClassID']}) and <b>Ward</b>
     * ({@code select[ng-model='Admission.WardID']}) to their first real option via a normal dropdown change —
     * deliberately NOT scanning the census grid for a specific free bed and ticking it (that scan walks every
     * Bed Class x Ward combination, up to 6.4s each, inside one page.evaluate() call with no timeout of its own —
     * confirmed live as a 30+ minute hang with near-zero CPU when beds were genuinely scarce). Room Type + Ward
     * alone is enough for Save to proceed. Expands the Admission Information panel first if the option lists
     * haven't loaded yet ({@code FillAdmissionDropDown()}). Returns "RoomType=… | Ward=…", or an "ERR:…" string if
     * either select never got a real option.
     */
    public String selectRoomTypeAndWard() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const firstReal=sel=>[...sel.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent)));"
                + " let bcSel=document.querySelector(\"select[ng-model='Admission.BedClassID']\"); let wSel=document.querySelector(\"select[ng-model='Admission.WardID']\");"
                + " if(!bcSel||!wSel) return 'ERR:no-selects';"
                + " if(firstReal(bcSel)<0){ const h=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('data-target')==='#collapseFour')||(x.getAttribute('ng-click')||'').indexOf('FillAdmissionDropDown')>=0); if(h) h.click(); for(let w=0;w<20 && firstReal(bcSel)<0;w++){ await new Promise(r=>setTimeout(r,500)); bcSel=document.querySelector(\"select[ng-model='Admission.BedClassID']\")||bcSel; } }"
                + " if(firstReal(bcSel)<0) return 'ERR:no-roomtype-options';"
                + " const fire=(sel,i)=>{ sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}} };"
                + " const bci=firstReal(bcSel); fire(bcSel,bci); await new Promise(r=>setTimeout(r,800));"
                + " wSel=document.querySelector(\"select[ng-model='Admission.WardID']\")||wSel;"
                + " let wi=firstReal(wSel);"
                + " for(let w=0;w<10 && wi<0;w++){ await new Promise(r=>setTimeout(r,400)); wSel=document.querySelector(\"select[ng-model='Admission.WardID']\")||wSel; wi=firstReal(wSel); }"
                + " if(wi<0) return 'ERR:no-ward-options | RoomType='+norm(bcSel.options[bci].textContent);"
                + " fire(wSel,wi); await new Promise(r=>setTimeout(r,300));"
                + " return 'RoomType='+norm(bcSel.options[bci].textContent)+' | Ward='+norm(wSel.options[wi].textContent); }");
        String info = r == null ? "(null)" : r.toString();
        lastBedInfo = info;
        return info;
    }

    /**
     * Section — <b>Additional Doctors</b> (collapsible {@code #collapseOne}). Expand it (its header ng-click
     * {@code FillAdditionDropDown()} loads the Classification + Additional Doctor dropdowns), pick the first real
     * of each, and click <b>Add</b> ({@code AddAdditionalDoc(AddDocList)}) to land a row in the
     * "Classification Name · Doctor Name · Remove" grid. Returns a summary.
     */
    public String fillAdditionalDoctors() {
        Object r = page.evaluate("async () => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const h=[...document.querySelectorAll('a')].find(x=>(x.getAttribute('ng-click')||'').indexOf('FillAdditionDropDown')>=0); if(h) h.click(); await new Promise(r=>setTimeout(r,2500));"
                + " const pick=(ng)=>{ const sel=document.querySelector(\"select[ng-model='\"+ng+\"']\"); if(!sel) return '(no '+ng+')'; let i=[...sel.options].findIndex(o=>o.value && !/^-*\\s*select/i.test(norm(o.textContent))); if(i<0) return '(no-opts)'; sel.selectedIndex=i; sel.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(sel).trigger('change');}catch(e){}} return norm(sel.options[i].textContent); };"
                + " const c=pick('Admission.ClassificationID'); const d=pick('Admission.AdditionalDoctorID'); await new Promise(r=>setTimeout(r,300));"
                + " let b=[...document.querySelectorAll('button,a,input[type=button]')].find(x=>(x.getAttribute('ng-click')||'').indexOf('AddAdditionalDoc')>=0);"
                + " let addWay='no-btn'; if(b){ b.click(); addWay='clicked'; } else { let sc=angular.element(document.querySelector(\"select[ng-model='Admission.ClassificationID']\")||document.body).scope(); let sx=sc; for(let i=0;i<20&&sx;i++){ if(typeof sx.AddAdditionalDoc==='function'){ sx.$apply(()=>sx.AddAdditionalDoc(sx.AddDocList)); addWay='scope-call'; break; } sx=sx.$parent; } } await new Promise(r=>setTimeout(r,900));"
                + " const grid=[...document.querySelectorAll('table')].find(t=>/classification name.*doctor name/i.test(norm((t.querySelector('thead')||{}).innerText)));"
                + " const rows=grid?[...grid.querySelectorAll('tbody tr')].filter(x=>norm(x.innerText) && !/no record/i.test(x.innerText)).length:0;"
                + " return 'Classification='+c+' | Doctor='+d+' | Add='+addWay+' | rows='+rows; }");
        waitForAngular(500);
        return r == null ? "(null)" : r.toString();
    }

    /** Click <b>Save</b> ({@code IUDAdmission();}) and return the toast (expect <b>"Patient Admitted Successfully."</b>).
     *  The clean input-based fill leaves all values in place, so Save just needs a real click + a robust
     *  toast capture (watch .toast, 30s, re-scan) — proven live (AdmissionID 10450). */
    /**
     * Save, and if the server says the NRIC belongs to an existing patient, mint a NEW identity (NRIC + matching
     * DOB) into the form and save again. The collision is a data fact, not a defect — retrying with fresh details
     * is what a tester would do, and it keeps the run reporting on the admission rather than on the clash.
     */
    public String saveAdmissionAndGetToast() {
        fillAdmissionTimeIfEmpty();   // fill the details FIRST — Save is rejected silently while Time is empty
        String toast = "";
        // 3 attempts was not always enough on a foreign/passport patient: the same async patient-lookup that
        // satisfyIdentityToast() fixes up (see its comment) can re-wipe a DIFFERENT field each round — confirmed
        // live the rejection shrank Passport+NRIC -> Passport -> First Name across attempts, converging but not
        // within 3. 5 gives that convergence room to finish.
        for (int attempt = 0; attempt < 5; attempt++) {
            toast = saveAdmissionOnce();
            // KS asks for the identity outright: "Please Enter NRIC!" and/or "Please Enter Passport No.!". Fill
            // exactly what it names and save again, rather than reporting a rejection we were told how to fix.
            String low = toast.toLowerCase();
            if (low.matches(".*please\\s+enter.*(nric|passport).*")) {
                satisfyIdentityToast(toast);
                continue;
            }
            // The same lookup-triggered wipe can also land on a plain contact field with no identity document
            // involved at all — restore just those from the values already captured in fillPatientSection(),
            // rather than re-triggering ANOTHER lookup by touching Passport/NRIC unnecessarily.
            if (low.matches(".*please\\s+enter.*(first\\s*name|date\\s*of\\s*birth|mobile|e-?mail).*")) {
                System.out.println("saveAdmission: '" + toast + "' — restoring wiped contact field(s) and retrying");
                restoreWipedContactFields(toast);
                continue;
            }
            if (!low.matches(".*(already registered|different nric).*")) return toast;
            String[] id = newIdentity();
            pDob = id[0];
            pNric = id[1];
            System.out.println("saveAdmission: NRIC already registered — retrying with NRIC " + pNric + " (DOB " + pDob + ")");
            page.evaluate("(a) => { const A=window.angular;"
                    + " const set=(ng,v)=>{ const e=[...document.querySelectorAll('input')].find(x=>x.getAttribute('ng-model')===ng && x.offsetParent!==null);"
                    + "   if(!e) return; const c=A.element(e).controller('ngModel'); e.value=v; if(c){ c.$setViewValue(v); c.$render(); }"
                    + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    // the NRIC box is the 12-char New IC one, not the passport/old-IC field
                    + " const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12')"
                    + "   || document.querySelector(\"input[ng-model='Registration.NationalId']\");"
                    + " if(nr){ const c=A.element(nr).controller('ngModel'); nr.value=a.nric; if(c){ c.$setViewValue(a.nric); c.$render(); }"
                    + "   nr.dispatchEvent(new Event('input',{bubbles:true})); nr.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " set('Registration.DateOfBirth', a.dob); }",
                    java.util.Map.of("nric", pNric, "dob", pDob));
            waitForAngular(1500);
        }
        return toast;
    }

    private String saveAdmissionOnce() {
        // Department must be verified/bound BEFORE Doctor — changing it reloads and clears the Doctor list, and a
        // silently-unbound Department (fillAdmissionSection()'s hardcoded scope assignment can fail to bind, same
        // shape as the Doctor field's own bug) is exactly what produces "Please Select Department First Then
        // Doctor !", seen live 2026-08-24 on an otherwise identical run to one that passed.
        System.out.println("saveAdmission: re-assert " + ensureDepartmentSelected());
        // Doctor is the one admission field the later sections can drop (expanding Additional Doctors reloads the
        // doctor dropdowns, and so does the Department re-bind above) — re-assert it here, or Save answers
        // "Please Select Doctor!".
        String doctorAfterDept = ensureDoctorSelected();
        System.out.println("saveAdmission: re-assert " + doctorAfterDept);
        // ensureDepartmentSelected() just picks the FIRST real Department option, and some (e.g. ACCIDENT &
        // EMERGENCY, seen live 2026-08-24) have no doctors attached at all — walk departments until one yields a
        // selectable doctor, same fallback already used below for bindEmptyMandatorySelects()'s own rebind.
        if (doctorAfterDept.toLowerCase().contains("no options") || doctorAfterDept.toLowerCase().contains("=(")) {
            System.out.println("saveAdmission: " + walkDepartmentForDoctor());
        }
        // Bind the mandatory dropdowns HERE, not during the fill. Selecting a vacant bed / flipping to Non-Presence
        // re-renders the Admission section and drops Registration Department, AttendingDoctor and Encounter Type —
        // so a bind done earlier reports "nothing to fix" and the form is empty again by the time Save runs.
        String bound = bindEmptyMandatorySelects();
        if (!bound.isEmpty()) {
            System.out.println("saveAdmission: bound empty mandatory selects -> " + bound);
            // Binding Registration Department re-filters the doctor list and DROPS the doctor chosen earlier, so
            // Save answered "Please Select Doctor!". The doctor must therefore be re-asserted AFTER the bind, not
            // before it — the cascade always wins over whatever was set first.
            waitForAngular(1200);
            String again = ensureDoctorSelected();
            System.out.println("saveAdmission: re-assert after bind " + again);
            // "(no options)" means the department we bound has NO doctors attached — taking the first option is a
            // coin flip and ALLIED HEALTH lost it. Walk the departments until one leaves a selectable doctor, the
            // same way RegistrationPage.ensureDoctorByChangingDepartment() does.
            if (again.toLowerCase().contains("no options") || again.toLowerCase().contains("=(")) {
                System.out.println("saveAdmission: " + walkDepartmentForDoctor());
            }
        }
        // Before saving, CHECK every mandatory patient field and fill ONLY the ones that are empty (filling a
        // field that's already set would fire a needless patient-lookup). The later sections (esp. the Payor
        // auto-fill) sometimes drop a value — this restores whatever is missing. Two passes catch cascades.
        for (int pass = 0; pass < 2; pass++) {
            Object filled = page.evaluate("(a) => {"
                    // FORCE-set a select to the given text (used for Gender/Race/ICCardType which can get flipped
                    // to a WRONG value — an if-empty check would skip them). force=true always re-selects.
                    + " const setSel=(ng,txt,force)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; const cur=(e.options[e.selectedIndex]||{}).text||''; const okCur=e.selectedIndex>0 && cur && !/^-*\\s*select\\s*-*$/i.test(cur.trim());"
                    + "   if(!force && okCur) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0){ if(okCur) return; i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); } if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                    + " const setInp=(ng,v)=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && (x.offsetParent!==null || !x.getAttribute('maxlength'))); if(!e) return; if((e.value||'').trim()) return; const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); };"
                    // ICCardType was hardcoded to 'New IC' here unconditionally — on a foreign/passport patient
                    // this pre-save sweep undid the Passport identification type on EVERY single Save attempt,
                    // right before the app checked it, guaranteeing the "Please Enter Passport No." loop no
                    // matter what satisfyIdentityToast() fixed beforehand. Use idType() so it matches the actual
                    // patient instead of always assuming Malaysian.
                    + " setSel('Registration.GenderID',a.gender,true); setSel('Registration.RaceID','Malay',true); setSel('Registration.ReligionID','Islam',true); setSel('Registration.MaritalStatusID','Single',true); setSel('Registration.BloodGroupID','O',true); setSel('Registration.ICCardTypeID',a.idType,true); setSel('Registration.MobileCountryCode','60',true); setSel('Registration.ResCountryID','Malaysia',false); setSel('Registration.ResStateID','Selangor',false); setSel('Registration.ResCityID','Petaling Jaya',false);"
                    + " setInp('Registration.FirstName',a.first); setInp('Registration.FamilyName',a.family); setInp('Registration.DateOfBirth',a.dob); setInp('Registration.MobileNo',a.mobile); setInp('Registration.Email',a.email); setInp('Registration.ResPinCode',a.postcode); setInp('Registration.ResHouseNo','12'); setInp('Registration.ResStreet','Jalan Test'); setInp('Registration.ResAddress','No 12, Jalan Test');"
                    + " const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")].find(x=>x.getAttribute('maxlength')==='12'); if(nr && !(nr.value||'').trim()){ const c=angular.element(nr).controller('ngModel'); nr.value=a.nric; if(c){c.$setViewValue(a.nric);c.$render();} nr.dispatchEvent(new Event('input',{bubbles:true})); nr.dispatchEvent(new Event('change',{bubbles:true})); }"
                    + " return true; }",
                    java.util.Map.of("gender", pGender, "first", pFullName, "family", pFamilyName, "dob", pDob,
                            "mobile", pMobile, "email", pEmail, "postcode", pPostCode, "nric", pNric,
                            "idType", idType()));
            waitForAngular(900);
        }
        // Record what the SAVE API answered. A rejection with no toast and nothing flagged leaves the run with no
        // evidence at all; the response body is the only remaining signal (KS returned no toast on a form the
        // screen itself considered complete).
        page.evaluate("() => { if(window.__adNet) return; window.__adNet=[];"
                + " const push=(u,s,b)=>{ try{ window.__adNet.push(String(s)+' '+String(u).split('/').pop().split('?')[0]+' -> '+String(b).replace(/\\s+/g,' ').slice(0,300)); }catch(e){} };"
                + " const of_=window.fetch; if(of_){ window.fetch=function(){ const u=arguments[0]&&arguments[0].url?arguments[0].url:arguments[0];"
                + "   return of_.apply(this,arguments).then(r=>{ if(/IUDAdmission|Admission/i.test(String(u))) r.clone().text().then(t=>push(u,r.status,t)).catch(()=>{}); return r; }); }; }"
                + " const os=XMLHttpRequest.prototype.send, oo=XMLHttpRequest.prototype.open;"
                + " XMLHttpRequest.prototype.open=function(m,u){ this.__u=u; return oo.apply(this,arguments); };"
                + " XMLHttpRequest.prototype.send=function(){ this.addEventListener('load',()=>{ if(/IUDAdmission|Admission/i.test(String(this.__u||''))) push(this.__u,this.status,this.responseText); });"
                + "   return os.apply(this,arguments); }; }");
        page.evaluate("(a) => { window.__adToasts=[]; if(window.__adObs) window.__adObs.disconnect();"
                + " const grab=()=>{ document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t && !window.__adToasts.includes(t)) window.__adToasts.push(t); }); };"
                + " window.__adObs=new MutationObserver(grab); window.__adObs.observe(document.body,{childList:true,subtree:true}); grab();"
                // FORCE Gender + ICCardType + a fallback Passport Expiry directly on every Registration object,
                // then click Save in the SAME tick — ICCardType keeps flipping to Passport during the async save.
                + " const seen=new Set(); document.querySelectorAll('*').forEach(el=>{ try{ const s=angular.element(el).scope(); let x=s; for(let i=0;i<15&&x;i++){ const reg=x.Registration; if(reg && typeof reg==='object' && !seen.has(reg)){ seen.add(reg); reg.PassportExpirydate='31/12/2045';"
                // Nationality = Malaysian (New IC / NRIC-based) ⇒ Passport No. MUST be empty — clear whatever key
                // it binds to. SKIPPED once the screen has explicitly demanded a passport ("Please Enter Passport
                // No.!"), otherwise this wipes the value that was just supplied to satisfy it and the save loops.
                + "   if(!a.keepPassport){ ['PassportNo','Passportno','passportno','PassportNumber','Passport'].forEach(k=>{ if(reg[k]!=null&&(''+reg[k]).trim()!=='') reg[k]=''; }); Object.keys(reg).forEach(k=>{ if(/passport/i.test(k)&&!/expir|date/i.test(k)&&reg[k]!=null&&(''+reg[k]).trim()!=='') reg[k]=''; }); }"
                + " } x=x.$parent; } }catch(e){} });"
                // Also blank the visible Passport No. input(s) (by label/placeholder/ng-model), excluding the expiry field.
                + " if(!a.keepPassport) (function(){ const norm=s=>(s||'').replace(/\\s+/g,' ').trim().toLowerCase(); const labelOf=e=>{ let n=e; for(let i=0;i<5&&n&&n.parentElement;i++){ n=n.parentElement; const l=n.querySelector('label'); if(l) return norm(l.textContent); } return ''; }; [...document.querySelectorAll('input')].filter(e=>{ const t=norm((e.getAttribute('ng-model')||'')+' '+(e.getAttribute('placeholder')||'')+' '+labelOf(e)); return /passport/.test(t) && !/expir|date/.test(t); }).forEach(e=>{ const c=angular.element(e).controller('ngModel'); try{e.removeAttribute('disabled');e.disabled=false;e.removeAttribute('readonly');}catch(x){} e.value=''; if(c){c.$setViewValue('');c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }); })();"
                + " const forceSel=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return; let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===(''+txt).toLowerCase()); if(i<0) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                + " forceSel('Registration.GenderID',a.gender); if(!a.keepPassport) forceSel('Registration.ICCardTypeID','New IC');"
                + " const b=[...document.querySelectorAll('button')].find(x=>x.getAttribute('ng-click')==='IUDAdmission();' && x.offsetParent!==null); if(b) b.click(); }",
                java.util.Map.of("gender", pGender, "keepPassport", keepPassport));
        // IUDAdmission raises a confirm ("Are you sure / Do you want to admit?") — answer Yes/OK while awaiting the
        // toast (without this the save hangs and no toast ever appears). Also keep harvesting toasts each tick.
        // A SUCCESSFUL save navigates the SPA, which destroys the JS execution context under whatever evaluate is
        // in flight. That is the save working, not a failure — so the polling below treats a destroyed context as
        // "saved and moved on" instead of letting the exception abort the whole test.
        boolean navigated = false;
        boolean got = false;
        for (int i = 0; i < 50 && !got && !navigated; i++) {
            try {
            page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')].find(m=>m.getBoundingClientRect().width>0 && /admit|are you sure|do you want|confirm|proceed|reserve/i.test(m.textContent||''));"
                    + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|admit|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                    + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim(); if(t){ (window.__adToasts=window.__adToasts||[]); if(!window.__adToasts.includes(t)) window.__adToasts.push(t); } }); }");
            got = Boolean.TRUE.equals(page.evaluate("() => (window.__adToasts||[]).some(a=>/admitted|success|please|select|enter|required|already|mandatory/i.test(a))"));
            if (!got) page.waitForTimeout(600);
            } catch (Exception e) {
                String msg = String.valueOf(e.getMessage());
                if (msg.contains("Execution context was destroyed") || msg.contains("navigation")) {
                    navigated = true;
                    System.out.println("saveAdmission: the app navigated after Save (context destroyed) — treating it as saved");
                } else throw e;
            }
        }
        if (navigated) {
            try { page.waitForLoadState(); } catch (Exception ignore) { }
            waitForAngular(1500);
        }
        // DIAG: capture EVERY message channel (toastr, JAlert, sweetalert, modal bodies, alerts) + save state.
        Object diag = navigated ? "(navigated after Save; url=" + page.url() + ")"
                : page.evaluate("() => { const norm=t=>(t||'').replace(/\\s+/g,' ').trim(); const msgs=new Set(window.__adToasts||[]);"
                + " document.querySelectorAll('.toast-message,.toast,[id^=toast],.jAlert,#jAlert_dialog,.sweet-alert,.bootbox,.jconfirm,.ng-confirm-box,.modal .modal-body,[class*=alert]').forEach(el=>{ if(el.offsetParent!==null){ const t=norm(el.textContent); if(t) msgs.add(t.slice(0,80)); } });"
                + " return {url:location.hash, msgs:[...msgs].slice(0,10), saveBtn:!!document.querySelector(\"button[ng-click='IUDAdmission();']\")}; }");
        System.out.println("ADMISSION SAVE DIAG => " + diag);
        if (!navigated) {
            try {
                Object net = page.evaluate("() => (window.__adNet||[]).slice(-4).join(' || ') || '(no Admission API call was made)'");
                System.out.println("ADMISSION SAVE API => " + net);
                lastSaveApi = String.valueOf(net);
                // No API call, no toast, nothing flagged: the handler never ran. A synthetic click is swallowed on
                // some builds (the same reason the occupancy actions need a real one) — retry with a REAL click
                // before concluding the save was rejected.
                boolean silent = String.valueOf(net).contains("no Admission API call")
                        && Boolean.TRUE.equals(page.evaluate("() => !(window.__adToasts||[]).length"));
                if (silent) {
                    Object tagged = page.evaluate("() => { const b=[...document.querySelectorAll('button')]"
                            + ".find(x=>x.getAttribute('ng-click')==='IUDAdmission();' && x.offsetParent!==null);"
                            + " if(!b) return false; b.id='__saveAdm'; return true; }");
                    if (Boolean.TRUE.equals(tagged)) {
                        System.out.println("saveAdmission: the synthetic click raised nothing — retrying with a REAL click");
                        try {
                            page.locator("#__saveAdm").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000));
                        } catch (Exception e) { System.out.println("saveAdmission: real click failed — " + e.getMessage()); }
                        page.evaluate("() => { const e=document.getElementById('__saveAdm'); if(e) e.removeAttribute('id'); }");
                        for (int i = 0; i < 25; i++) {
                            try {
                                page.evaluate("() => { const box=[...document.querySelectorAll('.jconfirm,.ng-confirm-box,.modal,.sweet-alert,[class*=confirm]')]"
                                        + ".find(m=>m.getBoundingClientRect().width>0 && /admit|are you sure|do you want|confirm|proceed|reserve/i.test(m.textContent||''));"
                                        + " if(box){ const y=[...box.querySelectorAll('button,a')].find(x=>/^(yes|ok|admit|continue|proceed)$/i.test((x.textContent||'').trim()) && x.offsetParent!==null); if(y) y.click(); }"
                                        + " document.querySelectorAll('.toast-message,.toast,[id^=toast]').forEach(el=>{ const t=(el.textContent||'').replace(/\\s+/g,' ').trim();"
                                        + "   if(t){ (window.__adToasts=window.__adToasts||[]); if(!window.__adToasts.includes(t)) window.__adToasts.push(t); } }); }");
                                if (Boolean.TRUE.equals(page.evaluate("() => (window.__adToasts||[]).length>0"))) break;
                                page.waitForTimeout(600);
                            } catch (Exception e) {
                                if (String.valueOf(e.getMessage()).contains("Execution context was destroyed")) break;
                                throw e;
                            }
                        }
                        Object net2 = page.evaluate("() => (window.__adNet||[]).slice(-4).join(' || ') || '(still no Admission API call)'");
                        System.out.println("ADMISSION SAVE API (after real click) => " + net2);
                        lastSaveApi = String.valueOf(net2);
                    }
                }
            } catch (Exception ignore) { }
        }
        // The toast array lives in the page that just went away — read it defensively.
        Object r;
        try {
            r = page.evaluate("() => { const a=window.__adToasts||[]; return a.find(x=>/admitted successfully/i.test(x)) || a.find(x=>/admitted|success/i.test(x)) || a.find(x=>/please|select|enter|required|already|mandatory/i.test(x)) || a[0] || ''; }");
        } catch (Exception e) {
            System.out.println("saveAdmission: could not read the toasts after the navigation - " + e.getMessage());
            r = "";
        }
        String toast = r == null ? "" : r.toString().trim();
        if (toast.isEmpty() && navigated) {
            // No toast survived the navigation. Say what happened rather than reporting an empty result: the
            // caller judges on this string, and "" reads as "the save did nothing".
            toast = "Admitted (the screen navigated away before the toast could be read; url=" + page.url() + ")";
        }
        waitForAngular(500);
        return toast;
    }

    // ---- low-level helpers (same style as RegistrationPage) --------------

    /** Select a dropdown (by ng-model) to the option matching text (or first real option) via selectedIndex+change
     *  — the correct binding for ng-options selects (proven live). */
    /**
     * Select an option by text tolerantly, and SAY SO when the wanted text is not there.
     *
     * <p>{@link #setSelTxt(String, String)} matches EXACTLY and silently takes the first option on a miss. For the
     * NOK fields that quietly wrote the wrong data and nothing reported it: asking for Nationality "Malaysian" on an
     * environment whose option reads "Malaysia" selected <b>Afghanistan</b> — the first entry of an alphabetical list
     * — so every admission stored an Afghan next of kin. Same trap for Title, Relationship and Occupation.</p>
     *
     * <p>Matching is normalised (case/punctuation-insensitive), then prefix, then substring — the fuzzy steps only
     * for values of 4+ characters, because "Mr." normalises to "mr" and prefix-matches "Mrs".</p>
     */
    /** What the kin nationality actually ended up as — reported so a wrong pick cannot pass unnoticed. */
    public String kinNationalityActual = "";

    /**
     * Type into the VISIBLE input bound to {@code ngModel} with real keystrokes and report what the model kept.
     *
     * <p>This screen ignores JS {@code $setViewValue} for several controls — the value shows in the box while the
     * model stays empty, so Save asks for a field the user can see filled in.</p>
     */
    public String typeReal(String ngModel, String value) {
        String sel = "input[ng-model='" + ngModel + "']:visible";
        try {
            com.microsoft.playwright.Locator box = page.locator(sel).first();
            box.click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000));
            box.fill("");
            box.type(value, new com.microsoft.playwright.Locator.TypeOptions().setDelay(60));
            box.press("Tab");
        } catch (Exception e) {
            System.out.println("typeReal(" + ngModel + "): " + e.getMessage().split("\n")[0]);
        }
        waitForAngular(400);
        Object v = page.evaluate("(m) => { const A=window.angular;"
                + " const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')===m && x.offsetParent!==null);"
                + " if(!e) return '(missing)'; const c=A.element(e).controller('ngModel');"
                + " const model = c && c.$modelValue!=null && c.$modelValue!=='' ? 'bound' : 'model NOT set';"
                + " return (e.value||'(empty)')+' ['+model+']'; }", ngModel);
        return ngModel.replace("Registration.", "") + "=" + v;
    }

    /**
     * Select a nationality and CHECK it: a real {@code selectOption} on the option whose text matches
     * {@code wanted} (exact, then starts-with, then contains), verifying the model committed.
     *
     * <p>{@link #setSelLike} silently takes the FIRST option when its match misses — an alphabetical list then
     * hands back "Afghan". Here a miss is reported as a miss instead.</p>
     */
    /**
     * Fill the admission <b>Time</b> when the screen left it empty.
     *
     * <p>It is the bare scope variable {@code inputTime} (not part of {@code Admission.*}), the same shape the
     * schedule masters use. KS ships it blank and rejects Save with NO toast at all — the screen's own aggregated
     * validator flags {@code inputTime} with {@code has-error} while our asterisk scan blamed GL Reference No.
     * Writes through the ngModel controller AND onto every scope holding the variable, since the timepicker
     * directive reads the scope rather than the input.</p>
     */
    private void fillAdmissionTimeIfEmpty() {
        String now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("hh:mm a"));
        Object r = page.evaluate("(v) => { const A=window.angular;"
                + " const e=[...document.querySelectorAll('input')].find(x=>(x.getAttribute('ng-model')||'')==='inputTime');"
                + " if(!e) return '(no inputTime field)';"
                + " if((e.value||'').trim()) return 'already \"'+e.value.trim()+'\"';"
                + " const c=A.element(e).controller('ngModel'); e.value=v;"
                + " if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + " e.dispatchEvent(new Event('input',{bubbles:true}));"
                + " e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " e.dispatchEvent(new Event('blur',{bubbles:true}));"
                + " try{ let s=A.element(e).scope(); for(let i=0;i<15&&s;i++){ if('inputTime' in s){ s.$apply(()=>{ s.inputTime=v; }); break; } s=s.$parent; } }catch(x){}"
                + " return 'set \"'+v+'\"'; }", now);
        System.out.println("fillAdmissionTimeIfEmpty: " + r);
        waitForAngular(500);
    }

    /** The label currently shown by the patient Nationality select ("" when unset or absent). */
    private String currentNationalityText() {
        Object t = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')==='Registration.NationalityID');"
                + " if(!e) return ''; const o=e.options[e.selectedIndex]; const s=o?norm(o.textContent):'';"
                + " return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(s)) ? s : ''; }");
        return t == null ? "" : t.toString();
    }

    public String selectNationalityVerified(String ngModel, String wanted) {
        // Read the list only once it has stopped GROWING. KS delivers the 256-entry Nationality master late: a
        // write that lands mid-load is discarded when Angular re-renders, and the select silently falls back to
        // that environment's default ("Myanmar") — which then had a Malaysian patient judged as a foreigner.
        try {
            page.evaluate("(ng) => { window.__stab=window.__stab||{}; delete window.__stab[ng]; }", ngModel);
            page.waitForFunction("(ng) => { const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                            + " if(!e) return true; const n=e.options.length;"
                            + " window.__stab=window.__stab||{}; const prev=window.__stab[ng]; window.__stab[ng]=n;"
                            + " return prev!==undefined && prev===n && n>1; }",
                    ngModel, new com.microsoft.playwright.Page.WaitForFunctionOptions().setTimeout(25000).setPollingInterval(1500));
        } catch (Exception ignore) { System.out.println("selectNationalityVerified: " + ngModel + " list never settled — proceeding"); }
        Object idx = page.evaluate("([ng,txt]) => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng); if(!e) return -1;"
                + " const opts=[...e.options].map((o,i)=>({i,n:norm(o.textContent),t:(o.textContent||'').trim()}))"
                + "   .filter(x=>e.options[x.i].value && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " const w=norm(txt); if(!w||!opts.length) return -1;"
                // Match on the STEM: this screen's list is not worded like OP Registration's, so an exact
                // "Australian" found nothing while "Australia" sat in the list (and vice versa). 6 letters is
                // enough to separate countries without letting e.g. "Indian" reach "India" + "Indonesian".
                + " const stem=w.slice(0, Math.max(5, Math.min(6, w.length)));"
                + " let m=opts.find(x=>x.n===w) || opts.filter(x=>x.n.startsWith(w)).sort((a,b)=>a.n.length-b.n.length)[0]"
                + "   || opts.filter(x=>w.startsWith(x.n) && x.n.length>=5).sort((a,b)=>b.n.length-a.n.length)[0]"
                + "   || opts.filter(x=>x.n.startsWith(stem)).sort((a,b)=>a.n.length-b.n.length)[0];"
                + " return m ? m.i : -1; }", java.util.Arrays.asList(ngModel, wanted));
        int i = idx instanceof Number ? ((Number) idx).intValue() : -1;
        if (i < 0) {
            Object offers = page.evaluate("([ng,txt]) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                    + " if(!s) return '(select not found)';"
                    + " const all=[...s.options].map(o=>(o.textContent||'').trim()).filter(t=>t);"
                    + " const near=all.filter(t=>t.toLowerCase().slice(0,3)===txt.toLowerCase().slice(0,3));"
                    + " return 'of '+all.length+' options; nearest: '+(near.length?near.slice(0,6).join(' | '):all.slice(1,6).join(' | ')); }",
                    java.util.Arrays.asList(ngModel, wanted));
            System.out.println("selectNationalityVerified: '" + wanted + "' is NOT in " + ngModel + " — " + offers);
            return "(no option matching " + wanted + " — " + offers + ")";
        }
        try {
            page.locator("select[ng-model='" + ngModel + "']").first().selectOption(
                    new com.microsoft.playwright.options.SelectOption().setIndex(i),
                    new com.microsoft.playwright.Locator.SelectOptionOptions().setTimeout(6000));
        } catch (Exception e) {
            page.evaluate("([ng,k]) => { const A=window.angular;"
                    + " const e=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng); if(!e) return;"
                    + " e.selectedIndex=k; e.dispatchEvent(new Event('change',{bubbles:true}));"
                    + " try{ A.element(e).triggerHandler('change'); }catch(x){} }", java.util.Arrays.asList(ngModel, i));
        }
        waitForAngular(700);
        Object got = page.evaluate("(ng) => { const s=[...document.querySelectorAll('select')].find(x=>(x.getAttribute('ng-model')||'')===ng);"
                + " return s && s.selectedIndex>=0 ? (s.options[s.selectedIndex].textContent||'').trim() : ''; }", ngModel);
        String chosen = got == null ? "" : got.toString();
        if (!chosen.toLowerCase().replaceAll("[^a-z0-9]", "").contains(wanted.toLowerCase().replaceAll("[^a-z0-9]", "").substring(0, Math.min(5, wanted.length())))) {
            System.out.println("selectNationalityVerified: asked for '" + wanted + "' but " + ngModel + " reads '" + chosen + "'");
            return chosen + " (WANTED " + wanted + ")";
        }
        return chosen;
    }

    private String setSelLike(String ngModel, String wanted) {
        Object r = page.evaluate("([ng,txt]) => { const norm=s=>(s||'').toLowerCase().replace(/[^a-z0-9]/g,'');"
                + " const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return null;"
                + " const opts=[...e.options].map((o,i)=>({o,i,t:(o.textContent||'').trim(),n:norm(o.textContent)}))"
                + "   .filter(x=>x.o.value && !/^-*\\s*select\\s*-*$/i.test(x.t));"
                + " if(!opts.length) return null;"
                // Matching order matters. Asking for "Malaysia" once matched "Malay" - a RACE, not a nationality -
                // because "malaysia".startsWith("malay"). So: exact first; then an option that EXTENDS the wanted
                // text (Malaysian for Malaysia), shortest such option first; and only then an option the wanted
                // text extends, and ONLY when it is at least 80% as long, which keeps "Malaysia"/"Malaysian" and
                // rejects "Malay". Every fuzzy step still needs 4+ characters, so "Mr." can never reach "Mrs".
                + " const MIN=4, NEAR=0.8; const w=norm(txt); let m=null, how='';"
                + " if(w){ m=opts.find(x=>x.n===w); if(m) how='exact';"
                + "   if(!m && w.length>=MIN){ const ext=opts.filter(x=>x.n.startsWith(w)).sort((a,b)=>a.n.length-b.n.length);"
                + "     if(ext.length){ m=ext[0]; how='extends'; } }"
                + "   if(!m && w.length>=MIN){ const sub=opts.filter(x=>x.n.length>=MIN && w.startsWith(x.n) && x.n.length>=w.length*NEAR)"
                + "       .sort((a,b)=>b.n.length-a.n.length); if(sub.length){ m=sub[0]; how='near-prefix'; } }"
                + "   if(!m && w.length>=MIN){ const c=opts.filter(x=>x.n.length>=MIN && (x.n.indexOf(w)>=0||w.indexOf(x.n)>=0)"
                + "       && x.n.length>=w.length*NEAR).sort((a,b)=>b.n.length-a.n.length); if(c.length){ m=c[0]; how='contains'; } } }"
                + " if(!m){ m=opts[0]; how='FIRST-OPTION-FALLBACK'; }"
                + " e.selectedIndex=m.i; e.dispatchEvent(new Event('change',{bubbles:true}));"
                + " const A=window.angular,$=window.jQuery;"
                + " try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}}"
                + " return JSON.stringify({text:m.t, how}); }",
                java.util.Arrays.asList(ngModel, wanted));
        waitForAngular(150);
        if (r == null) return "";
        String s = r.toString();
        java.util.regex.Matcher mt = java.util.regex.Pattern.compile("\"text\":\"([^\"]*)\"").matcher(s);
        java.util.regex.Matcher mh = java.util.regex.Pattern.compile("\"how\":\"([^\"]*)\"").matcher(s);
        String text = mt.find() ? mt.group(1) : "", how = mh.find() ? mh.group(1) : "";
        if ("FIRST-OPTION-FALLBACK".equals(how)) {
            System.out.println("setSelLike: WARNING " + ngModel + " has no option like \"" + wanted
                    + "\" — fell back to the first option \"" + text + "\" (WRONG DATA will be saved)");
        } else if (!"exact".equals(how)) {
            System.out.println("setSelLike: " + ngModel + " \"" + wanted + "\" matched \"" + text + "\" (" + how + ")");
        }
        return text;
    }

    /**
     * Set the NOK <b>State</b> and <b>City/District</b>, walking states until one yields a city.
     *
     * <p>"Same As Patient Address" does not carry these across on this screen, so {@code AddKinDetails} refused with
     * <i>"Please Select State!"</i> and every run logged the kin row as {@code added=false} — the admission saved
     * without a next of kin at all. City is loaded FROM the state, so a state with no cities is no use.</p>
     */
    /** How many rows the NOK/Guarantor grid holds (its "no records" placeholder excluded). */
    public int kinGridRowCount() {
        Object n = page.evaluate("() => { const t=[...document.querySelectorAll('table')].find(x=>/relation|guarantor|nationality/i.test((x.querySelector('thead')||{}).innerText||''));"
                + " if(!t) return 0;"
                + " return [...t.querySelectorAll('tbody tr')].filter(r=>{ const tx=(r.textContent||'').trim();"
                + "   return tx && !/no\\s*records|no\\s*data/i.test(tx); }).length; }");
        try { return (int) Double.parseDouble(String.valueOf(n)); } catch (Exception e) { return 0; }
    }

    /** Set once the screen has demanded a Passport No., so the pre-save "clear the passport" step stops undoing it. */
    private boolean keepPassport = false;

    /** What the save API answered on the last attempt — "" when the handler never called it. */
    public String lastSaveApi = "";

    /** First capture group of {@code re} in {@code s}, or "". */
    private static String group(String s, String re) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(re).matcher(s == null ? "" : s);
        return m.find() ? m.group(1) : "";
    }

    /**
     * Explain a save that produced NOTHING — no toast, no flagged field and no API call.
     *
     * <p>On builds carrying BW198 the handler is {@code async} and opens with
     * {@code var canContinue = await $scope.ShowPassportExpiryInfo(); if (!canContinue) return;}. That gate
     * {@code resolve()}s with NO ARGUMENT for any {@code callingMode} other than {@code PatientRegister} /
     * {@code EmergencyRegistrationConsious}, so it yields {@code undefined} and the save returns before it
     * validates anything or contacts the server. Reads the live calling mode so the report states the cause
     * instead of leaving "no toast appeared" to be re-diagnosed by hand — and says only what it can verify.</p>
     *
     * @return the diagnosis, or "" when this screen is not in that state
     */
    public String diagnoseSilentSave() {
        if (!lastSaveApi.isEmpty() && !lastSaveApi.toLowerCase().contains("no admission api call")) return "";
        Object r = page.evaluate("() => { const A=window.angular; let mode=null, gated=null;"
                + " try { mode=A.element(document.body).injector().get('$state').current.callingMode; } catch(e) {}"
                + " try { const seen=new Set(); document.querySelectorAll('form,div[ng-controller],body').forEach(el=>{"
                + "   let x=A.element(el).scope(); for(let i=0;i<15&&x;i++){ if(!seen.has(x.$id)){ seen.add(x.$id);"
                + "     if(typeof x.ShowPassportExpiryInfo==='function') gated=true; } x=x.$parent; } }); } catch(e) {}"
                + " return JSON.stringify({mode:mode, gated:gated}); }");
        String s = String.valueOf(r);
        String mode = group(s, "\"mode\":\"([^\"]*)\"");
        boolean gated = s.contains("\"gated\":true");
        // Report the EVIDENCE and the known cause, not a theory. A silent save here has been traced to the
        // Non-Presence fallback: when no vacant bed is found the fallback forces hardcoded BedClassID/WardID that
        // do not exist on every environment, and the save then does nothing at all. Selecting a real vacant bed
        // fixed it on KS. (An earlier reading blamed `await ShowPassportExpiryInfo()` in
        // patientRegistrationController.js — that is the OP REGISTRATION controller, not this screen's, and the
        // save proved to work with the same callingMode once a real bed was selected.)
        String bedNote = lastBedInfo.toLowerCase().contains("non-presence")
                ? " The bed step fell back to Non-Presence (" + lastBedInfo + "), whose hardcoded BedClassID/WardID"
                  + " are not valid on every environment — that is the first thing to check."
                : "";
        return "the save never reached the server (no IUDAdmission API call, with a synthetic AND a real click),"
                + " and the screen raised no message and flagged no field."
                + (mode.isEmpty() ? "" : " callingMode=\"" + mode + "\".") + bedNote;
    }

    /** What the identity recovery did, for the step text. */
    public String lastIdentityFix = "";

    /**
     * Re-supply ONLY the contact field(s) the toast actually named, from the values already captured in
     * {@link #fillPatientSection()}, without touching NRIC or Passport. Used when Save rejects on a plain
     * contact field (no identity document demanded) — the same async patient-lookup wipe
     * {@link #satisfyIdentityToast} deals with, but here there is no reason to also re-trigger it by writing to
     * Passport/NRIC.
     *
     * <p>Confirmed live (isolated, no Save involved): {@code Registration.MobileNo} fires its OWN async
     * "search patient by phone" lookup that wipes First Name right back out — a SECOND lookup trigger distinct
     * from the passport one. Blindly rewriting Mobile/Email "just in case" alongside the field that was actually
     * missing therefore re-triggers a fresh wipe and produces an infinite "Please Enter First Name!" loop
     * (confirmed live across 3 straight attempts). Touch only what the toast named.</p>
     */
    private void restoreWipedContactFields(String toast) {
        String low = toast.toLowerCase();
        boolean first = low.contains("first name") && pFullName != null && !pFullName.isEmpty();
        boolean dob = low.contains("date of birth") && pDob != null && !pDob.isEmpty();
        boolean mobile = low.contains("mobile") && pMobile != null && !pMobile.isEmpty();
        boolean email = low.matches(".*e-?mail.*") && pEmail != null && !pEmail.isEmpty();
        if (!first && !dob && !mobile && !email) return;
        if (first) clearedFieldLog.add("First Name cleared after entry (app's async patient-lookup) — restored to \"" + pFullName + "\"");
        if (dob) clearedFieldLog.add("Date of Birth cleared after entry — restored to \"" + pDob + "\"");
        if (mobile) clearedFieldLog.add("Mobile No. cleared after entry — restored to \"" + pMobile + "\"");
        if (email) clearedFieldLog.add("E-mail cleared after entry — restored to \"" + pEmail + "\"");
        page.evaluate("(a) => { const A=window.angular;"
                + " const set=(e,v)=>{ if(!e) return false; const c=A.element(e).controller('ngModel');"
                + "   e.value=v; if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); return true; };"
                + " const byNg=ng=>{ const all=[...document.querySelectorAll('input')].filter(x=>(x.getAttribute('ng-model')||'')===ng);"
                + "   return all.find(x=>x.offsetParent!==null) || all[0]; };"
                + " if(a.first) set(byNg('Registration.FirstName'),a.first); if(a.dob) set(byNg('Registration.DateOfBirth'),a.dob);"
                + " if(a.mobile) set(byNg('Registration.MobileNo'),a.mobile); if(a.email) set(byNg('Registration.Email'),a.email);"
                + " const seen=new Set(); document.querySelectorAll('*').forEach(el=>{ try{ let x=A.element(el).scope();"
                + "   for(let i=0;i<15&&x;i++){ const reg=x.Registration; if(reg && typeof reg==='object' && !seen.has(reg)){ seen.add(reg);"
                + "     if(a.first) reg.FirstName=a.first; if(a.dob) reg.DateOfBirth=a.dob;"
                + "     if(a.mobile) reg.MobileNo=a.mobile; if(a.email) reg.Email=a.email; } x=x.$parent; } }catch(e){} }); }",
                java.util.Map.of("first", first ? pFullName : "", "dob", dob ? pDob : "",
                        "mobile", mobile ? pMobile : "", "email", email ? pEmail : ""));
        waitForAngular(600);
    }

    /**
     * Give the save exactly the identity it asked for.
     *
     * <p>KS rejects the admission with <i>"Please Enter NRIC!"</i> and/or <i>"Please Enter Passport No.!"</i> — it
     * wants the fields filled rather than any cascade re-driven. Refill the NRIC (the 12-char New IC box), and
     * when a passport is demanded supply one plus an expiry and set {@link #keepPassport} so the Malaysian-patient
     * clearing step no longer wipes it on the retry.</p>
     */
    private void satisfyIdentityToast(String toast) {
        String low = toast.toLowerCase();
        boolean wantNric = low.contains("nric");
        boolean wantPassport = low.contains("passport");
        if (wantPassport) keepPassport = true;
        String passport = "A" + String.format("%07d", Math.abs(System.nanoTime() % 10000000));
        String expiry = java.time.LocalDate.now().plusYears(5)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        final String setJs = " const A=window.angular;"
                + " const set=(e,v)=>{ if(!e) return false; const c=A.element(e).controller('ngModel');"
                + "   try{ e.removeAttribute('disabled'); e.disabled=false; e.removeAttribute('readonly'); }catch(x){}"
                + "   e.value=v; if(c){ c.$setViewValue(v); if(c.$commitViewValue) c.$commitViewValue(); c.$render(); }"
                + "   e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   e.dispatchEvent(new Event('blur',{bubbles:true})); return true; };"
                + " const byNg=ng=>{ const all=[...document.querySelectorAll('input')].filter(x=>(x.getAttribute('ng-model')||'')===ng);"
                + "   return all.find(x=>x.offsetParent!==null) || all[0]; };";
        // For an already-foreign patient (isForeign()), pNric is deliberately "" — the patient is identified by
        // passport, not NRIC. Writing that empty string satisfies nothing and just burns a retry, so only attempt
        // the NRIC fix when there is an actual NRIC value to give it (a Malaysian patient whose ICCardType flipped).
        boolean fillNric = wantNric && pNric != null && !pNric.isEmpty();
        if (fillNric) clearedFieldLog.add("NRIC cleared / not accepted after entry — re-supplied");
        if (wantPassport) clearedFieldLog.add("Passport No. cleared / not accepted after entry — re-supplied (\"" + passport + "\")");
        Object r = page.evaluate("(a) => {" + setJs + " const done=[];"
                + " if(a.wantNric){ const nr=[...document.querySelectorAll(\"input[ng-model='Registration.NationalId']\")]"
                + "     .find(x=>x.getAttribute('maxlength')==='12') || byNg('Registration.NationalId');"
                + "   if(set(nr,a.nric)) done.push('NRIC='+a.nric); }"
                + " if(a.wantPassport){ if(set(byNg('Registration.FamilyName'),a.passport)) done.push('Passport='+a.passport);"
                + "   if(set(byNg('Registration.PassportExpirydate'),a.expiry)) done.push('Expiry='+a.expiry); }"
                + " return done.join(' | ') || '(nothing to fill)'; }",
                java.util.Map.of("wantNric", fillNric, "wantPassport", wantPassport,
                        "nric", pNric == null ? "" : pNric, "passport", passport, "expiry", expiry));
        // Filling the passport/NRIC field re-triggers the ASYNC "patient lookup" that fillPatientSection() already
        // learned wipes the whole identity/contact section (First Name, DOB, Mobile, Email, address — see its
        // comment on the atomic identity fill) — AND, confirmed live on a foreign/passport patient, the passport
        // value itself can fail to stick when a brand-new passport finds no matching record. It is a network
        // round-trip, so restoring in the SAME evaluate/tick as the trigger write does not help — the lookup's
        // response arrives later and clobbers it anyway. Confirmed live (no Save click involved at all — just
        // filling the Passport field, waiting, and re-reading the form): the lookup resets
        // Registration.NationalityID back to "--Select--" in addition to First Name/DOB/Mobile/Email/address —
        // and with Nationality blank the app can't tell Malaysian from foreign, so it demands BOTH Passport AND
        // NRIC regardless of what either field holds, which is exactly the loop that never progressed. Wait for
        // the lookup to actually resolve, THEN restore what it touched — Nationality and Identification Type
        // included — so this restore is the last write and nothing overwrites it afterwards.
        //
        // Mobile/Email/postcode are DELIBERATELY left out here: confirmed live (isolated, no Save involved)
        // that Registration.MobileNo fires its OWN separate async "search patient by phone" lookup that wipes
        // First Name right back out — so restoring Mobile/Email defensively "just in case" alongside the fields
        // that actually needed fixing just re-triggers a fresh wipe and produces an infinite "Please Enter
        // First Name!" loop (confirmed live across 3 straight attempts). The outer retry loop's
        // restoreWipedContactFields() picks up Mobile/Email/DOB reactively, one at a time, only when the toast
        // actually names them empty — safer than rewriting fields nobody complained about.
        waitForAngular(1800);
        if (wantPassport) {
            page.evaluate("(a) => {" + setJs
                    // Plain text inputs, so a raw model assignment is safe — unlike NationalityID/ICCardType,
                    // which bind to a select's numeric option value and must go through proper select-matching.
                    + " set(byNg('Registration.FamilyName'),a.passport); set(byNg('Registration.PassportExpirydate'),a.expiry);"
                    + " if(a.first) set(byNg('Registration.FirstName'),a.first); if(a.dob) set(byNg('Registration.DateOfBirth'),a.dob);"
                    + " const setSelLike=(ng,txt)=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                    + "   let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===txt.toLowerCase());"
                    + "   if(i<0) i=[...e.options].findIndex(o=>(o.textContent||'').toLowerCase().includes(txt.toLowerCase().slice(0,5)));"
                    + "   if(i<0 || e.selectedIndex===i) return; e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} };"
                    // Nationality first — restoring Identification Type while Nationality is still blank can get
                    // overridden by the same cascade that reacts to a Nationality change.
                    + " if(a.nationality) setSelLike('Registration.NationalityID',a.nationality);"
                    + " setSelLike('Registration.ICCardTypeID','Passport');"
                    // Push onto every Registration scope too — the pre-save sweep reads the model, not the input.
                    + " const seen=new Set(); document.querySelectorAll('*').forEach(el=>{ try{ let x=A.element(el).scope();"
                    + "   for(let i=0;i<15&&x;i++){ const reg=x.Registration; if(reg && typeof reg==='object' && !seen.has(reg)){ seen.add(reg);"
                    + "     reg.FamilyName=a.passport; reg.PassportExpirydate=a.expiry; if(a.first) reg.FirstName=a.first; if(a.dob) reg.DateOfBirth=a.dob; } x=x.$parent; } }catch(e){} }); }",
                    java.util.Map.of("passport", passport, "expiry", expiry,
                            "first", pFullName == null ? "" : pFullName, "dob", pDob == null ? "" : pDob,
                            "nationality", nationality()));
        }
        waitForAngular(900);
        lastIdentityFix = String.valueOf(r);
        System.out.println("satisfyIdentityToast: '" + toast + "' -> " + lastIdentityFix);
    }

    /** The label currently selected on a visible {@code <select>} by ng-model — "" for a "--Select--" placeholder. */
    private String kinSelectedText(String ngModel) {
        // Prefer a VISIBLE control but fall back to any match: on this screen the kin section's selects are in the
        // DOM while its panel is collapsed, so a visibility-only lookup reported "select not found" for every one
        // of them and the whole kin address went unfilled.
        Object t = page.evaluate("(ng) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const all=[...document.querySelectorAll('select')].filter(x=>(x.getAttribute('ng-model')||'')===ng);"
                + " const e=all.find(x=>x.offsetParent!==null) || all[0];"
                + " if(!e) return ''; const o=e.options[e.selectedIndex]; const s=o?norm(o.textContent):'';"
                + " return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(s)) ? s : ''; }", ngModel);
        return t == null ? "" : t.toString();
    }

    /**
     * Make sure the kin's <b>address Country</b> holds a value — WITHOUT touching its nationality.
     *
     * <p>Selecting {@code NOKnationalid} already derives this field, so this only steps in when the derivation
     * left it empty, and then prefers Malaysia: the country of the patient address that "Same As Patient Address"
     * just copied, and the only one with State/City master data on these environments.</p>
     */
    private void ensureKinAddressCountry() {
        Object res = page.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const A=window.angular; const $=window.jQuery;"
                + " const all=[...document.querySelectorAll('select')].filter(x=>(x.getAttribute('ng-model')||'')==='Registration.KinCountryID');"
                + " const e=all.find(x=>x.offsetParent!==null) || all[0];"
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

    private String ensureKinStateAndCity() {
        Object r = page.evaluate("() => new Promise(async (resolve) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                + " const A=window.angular; const $=window.jQuery; const sleep=ms=>new Promise(x=>setTimeout(x,ms));"
                // Visible first, else any match — the kin panel keeps its controls in the DOM while collapsed.
                + " const find=re=>{ const all=[...document.querySelectorAll('select')].filter(s=>re.test(s.getAttribute('ng-model')||''));"
                + "   return all.find(s=>s.offsetParent!==null) || all[0]; };"
                + " const real=e=>e?[...e.options].filter(o=>o.value && !/^-*\\s*select\\s*-*$/i.test(norm(o.textContent))):[];"
                + " const chosen=e=>{ const o=e&&e.options[e.selectedIndex]; const t=o?norm(o.textContent):'';"
                + "   return (o && o.value && !/^-*\\s*select\\s*-*$/i.test(t)) ? t : ''; };"
                + " const fire=e=>{ e.dispatchEvent(new Event('change',{bubbles:true}));"
                + "   try{A.element(e).triggerHandler('change');}catch(x){} if($){try{$(e).trigger('change');}catch(x){}} };"
                + " let st=find(/kin.*state/i); const ct0=find(/kin.*city/i);"
                + " if(!st) return resolve('(no kin State field)');"
                + " if(chosen(st) && (!ct0 || chosen(ct0))) return resolve('kept State='+chosen(st)+' City='+(ct0?chosen(ct0):'(none)'));"
                // The State list loads off the Country field's change event — but ensureKinAddressCountry() SKIPS
                // firing that event when Country is already selected (it only sets it when empty), so on a run
                // where Country was pre-filled (e.g. by "Same As Patient Address") the State list can be stuck
                // empty with nothing that would ever (re)trigger its fetch. Poll first for options that may
                // already be loading; if still empty, force-refire Country's own change event to kick the fetch.
                + " for(let w=0;w<10 && !real(st).length; w++) await sleep(500);"
                + " if(!real(st).length){ const cy=find(/kin.*countr/i); if(cy){ fire(cy);"
                + "   for(let w=0;w<10 && !real(st).length; w++){ await sleep(500); st=find(/kin.*state/i)||st; } } }"
                + " for(const o of real(st)){ st.value=o.value; fire(st); await sleep(1100);"
                + "   const c=find(/kin.*city/i); const rs=real(c);"
                + "   if(rs.length){ c.value=rs[0].value; fire(c); await sleep(500);"
                + "     if(chosen(c)) return resolve('State='+norm(o.textContent)+' City='+chosen(c)); } }"
                + " resolve('no state yielded a city (State='+chosen(st)+', '+real(st).length+' state option(s) available)'); })");
        waitForAngular(500);
        String s = r == null ? "" : r.toString();
        System.out.println("ensureKinStateAndCity: " + s);
        return s;
    }

    private void setSelTxt(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{ const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng); if(!e) return;"
                + " let i=[...e.options].findIndex(o=>(o.textContent||'').trim().toLowerCase()===txt.toLowerCase()); if(i<0) i=[...e.options].findIndex(o=>o.value && !/^-*\\s*select\\s*-*$/i.test((o.textContent||'').trim())); if(i<0) return;"
                + " e.selectedIndex=i; e.dispatchEvent(new Event('change',{bubbles:true})); const $=window.jQuery; if($){try{$(e).trigger('change');}catch(err){}} }",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(150);
    }

    /** Fill an input/textarea (by ng-model, visible) via its Angular model — the plain approach that stuck live. */
    private void setInput(String ngModel, String value) {
        page.evaluate("([ng,v])=>{ const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng && (x.offsetParent!==null || !x.getAttribute('maxlength'))); if(!e) return;"
                + " const c=angular.element(e).controller('ngModel'); e.value=v; if(c){c.$setViewValue(v);c.$render();} e.dispatchEvent(new Event('input',{bubbles:true})); e.dispatchEvent(new Event('change',{bubbles:true})); }",
                java.util.Arrays.asList(ngModel, value));
        waitForAngular(120);
    }

    private void setSel(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "if(!e)return;const o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);if(!o)return;e.value=o.value;"
                + "const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}"
                + "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(150);
    }

    private void setSelIfPresent(String ngModel, String optionText) {
        page.evaluate("([ng,txt])=>{const $=window.jQuery;const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')===ng);"
                + "if(!e)return;let o=[...e.querySelectorAll('option')].find(x=>x.textContent.trim()===txt);"
                + "if(!o) o=[...e.querySelectorAll('option')].find(x=>x.value && !/^-*\\s*select/i.test((x.textContent||'').trim()));"
                + "if(!o)return;e.value=o.value;const c=angular.element(e).controller('ngModel');if(c){c.$setViewValue(o.value);c.$render();}"
                + "if($){try{$(e).trigger('change');$(e).select2('val',o.value);}catch(err){}}e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, optionText));
        waitForAngular(150);
    }

    private void fillModel(String ngModel, String value) {
        page.evaluate("([ng,v])=>{const e=[...document.querySelectorAll('input,textarea')].find(x=>x.getAttribute('ng-model')===ng&&(x.offsetParent!==null||!x.getAttribute('maxlength')));"
                + "if(!e)return;const c=angular.element(e).controller('ngModel');e.value=v;if(c){c.$setViewValue(v);c.$render();}"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));}",
                java.util.Arrays.asList(ngModel, value));
    }

    // ---- report rasterization ---------------------------------------------

    /** Admission reports are server-generated PDFs; fetch the bytes in-browser and rasterize page 1
     *  (a Chromium PDF-viewer screenshot is blank); falls back to a direct screenshot for HTML reports. */
    public byte[] captureReportPng(Page reportTab) {
        try {
            String url = reportTab.url();
            if (url != null && url.startsWith("http")) {
                Object b64 = reportTab.evaluate(
                        "async (u) => { const r = await fetch(u, {credentials:'include'});"
                                + " const buf = await r.arrayBuffer(); const bytes = new Uint8Array(buf);"
                                + " let bin=''; const chunk=0x8000;"
                                + " for (let i=0;i<bytes.length;i+=chunk) bin += String.fromCharCode.apply(null, bytes.subarray(i,i+chunk));"
                                + " return btoa(bin); }", url);
                if (b64 != null && !b64.toString().isEmpty()) {
                    byte[] pdf = java.util.Base64.getDecoder().decode(b64.toString());
                    boolean isPdf = pdf.length > 4 && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F';
                    if (isPdf) {
                        byte[] pngFromPdf = com.kpj.core.PdfUtil.firstPageToPng(pdf);
                        if (pngFromPdf != null && pngFromPdf.length > 0) return pngFromPdf;
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("captureReportPng: PDF fetch/rasterize failed (" + e.getMessage() + ")");
        }
        byte[] png = null;
        try {
            reportTab.bringToFront();
            try { reportTab.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED); } catch (Exception ignore) {}
            // Same maximize-or-pin as the main window: a report popup opens at its own small size (704x195 for a
            // wrist band, 856x224 for a label), and those shots read as "zoomed in" next to the 1920x1075 ones.
            com.kpj.core.DevHisBase.ensureLargeWindow(reportTab);
            reportTab.waitForTimeout(2500);
            png = reportTab.screenshot(new Page.ScreenshotOptions().setTimeout(12000));
        } catch (Exception e) {
            System.out.println("captureReportPng: " + e.getMessage());
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) {}
        }
        return png;
    }

    // ---- consent form (nhisformstest report tab) ---------------------------

    /**
     * The "Consent Form" report tab (URL contains {@code nhisformstest}) is not a static report like the other
     * four — it is a live PDPA consent form with its own <b>Save As Draft / Submit / Print / Back</b> buttons.
     * {@link #captureReportPng} only screenshots it as opened (unsubmitted); this clicks <b>Submit</b>, answers
     * any confirm dialog that follows, and returns whatever toast/message resulted (or a reason it couldn't be
     * clicked). Does not assume success — callers should check the returned text.
     */
    public String submitConsentForm(Page consentTab) {
        try { consentTab.bringToFront(); } catch (Exception ignore) { }
        Object tagged;
        try {
            tagged = consentTab.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                    + " const b=[...document.querySelectorAll('button,a,input[type=button],input[type=submit]')]"
                    + "   .find(x=>/^submit$/i.test(norm(x.textContent||x.value||'')) && x.offsetParent!==null);"
                    + " if(!b) return false; b.id='__consentSubmit'; return true; }");
        } catch (Exception e) { return "Submit lookup failed - " + e.getMessage(); }
        if (!Boolean.TRUE.equals(tagged)) return "Submit button not found on Consent Form tab";
        try { consentTab.locator("#__consentSubmit").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(5000)); }
        catch (Exception e) { return "Submit click failed - " + e.getMessage(); }
        try { consentTab.evaluate("() => { const e=document.getElementById('__consentSubmit'); if(e) e.removeAttribute('id'); }"); } catch (Exception ignore) { }
        consentTab.waitForTimeout(800);
        // Answer every confirm/alert the Submit click raises — checked live: it is TWO Bootstrap modals, not one
        // ("Confirmation — Are you sure you want to continue?", id="__consentSubmit"'s dialog, then a SEPARATE
        // "Success — Form Saved Successfully!" modal with button id="messageModalOk"). Content-based matching
        // (a visible Yes/OK/Submit/Confirm button, walked up to a real dialog-sized text block — not the dialog's
        // CSS class, which was never actually the problem) finds both correctly. The real bug that took several
        // rounds to isolate: this Consent Form is a server-rendered postback page, not an SPA route — clicking
        // "Yes" on the first modal triggers a real navigation that briefly destroys the JS execution context, and
        // `consentTab.evaluate()` throws for that one poll. The fix is to swallow that specific transient error
        // and keep polling (below), not to `break` the loop on it — breaking is what left the Success modal's OK
        // permanently unclicked despite the matching logic itself being correct.
        for (int i = 0; i < 30; i++) {
            Object handled;
            try {
                handled = consentTab.evaluate("() => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim();"
                        + " const docs=[document];"
                        + " try { [...document.querySelectorAll('iframe')].forEach(f=>{ try { if (f.contentDocument) docs.push(f.contentDocument); } catch(e){} }); } catch(e){}"
                        + " for (const doc of docs) {"
                        + "   const btns=[...doc.querySelectorAll('button,a')].filter(x=>/^(yes|ok|submit|confirm)$/i.test(norm(x.textContent)) && x.getBoundingClientRect().width>0);"
                        + "   for (const b of btns) {"
                        + "     let el=b, txt='', depth=0;"
                        + "     while (el && depth<6) { txt=norm(el.textContent); if (txt.length>15) break; el=el.parentElement; depth++; }"
                        + "     if (txt.length>15) { b.click(); return txt.slice(0,150); }"
                        + "   }"
                        + " }"
                        + " return null; }");
            } catch (Exception e) {
                // Confirmed via a diagnostic dump: the OK button ("messageModalOk", "btn btn-sm message-btn") IS
                // present, correctly matched, and clickable — this evaluate() call was simply failing on a
                // TRANSIENT "execution context was destroyed" error because clicking "Yes" on the first confirm
                // triggers a real page navigation (this report is a server-rendered postback page, not an SPA
                // route), which momentarily tears down the JS context the Success dialog then loads into.
                // Breaking out of the loop here is what left it permanently unanswered — retry instead.
                System.out.println("submitConsentForm: transient error during poll (probably a navigation) - " + e.getMessage());
                consentTab.waitForTimeout(700);
                continue;
            }
            if (handled != null) System.out.println("submitConsentForm: answered -> " + handled);
            consentTab.waitForTimeout(700);
        }
        consentTab.waitForTimeout(1000);
        Object toast;
        try {
            toast = consentTab.evaluate("() => [...document.querySelectorAll('[class*=toast]')].filter(e=>e.offsetParent!==null).map(e=>(e.textContent||'').replace(/\\s+/g,' ').trim()).filter(t=>t).join(' | ')");
        } catch (Exception e) { toast = null; }
        String t = toast == null ? "" : toast.toString().trim();
        return t.isEmpty() ? "Submit clicked, confirm(s) answered — no toast observed" : t;
    }

    /** Full-page (not just viewport) screenshot of a report tab — for the submitted Consent Form, which is a tall
     *  scrollable HTML document, a viewport shot only shows whatever dialog was open at the top of it. */
    public byte[] captureReportFullPage(Page reportTab) {
        try {
            reportTab.bringToFront();
            com.kpj.core.DevHisBase.ensureLargeWindow(reportTab);
            reportTab.waitForTimeout(500);
            return reportTab.screenshot(new Page.ScreenshotOptions().setFullPage(true).setTimeout(20000));
        } catch (Exception e) {
            System.out.println("captureReportFullPage: " + e.getMessage());
            return null;
        } finally {
            try { page.bringToFront(); } catch (Exception ignore) { }
        }
    }
}
