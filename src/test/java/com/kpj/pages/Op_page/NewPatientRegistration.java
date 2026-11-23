package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;
import com.kpj.pages.Op_page.RegistrationPage.SaveOutcome;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Register a brand-new OP patient and find the MRN it was given — the building block for tests that need a patient who
 * has never been used (e.g. Nursing Station &gt; DeathRegister, where every saved record uses a patient up).
 *
 * <p>It composes the existing page objects rather than re-implementing them: {@link RegistrationPage} fills and saves
 * the OP &gt; Registration form (the same sections, in the same order, as {@code RegistrationTest}, without that
 * test's validation-defect probes), and {@link OutPatientQueueManagementPage} opens the Outpatient Queue Management
 * screen and reads the grid. Registration shows the new MRN only inside a PDF label, so the MRN is read from that
 * queue grid, the way a user would find it.</p>
 *
 * <p>The stock random patient names come from a six-name list, so the same name turns up run after run (today's queue
 * already held two "Linda Carter"s). The family name is therefore replaced with a unique one — "Dr" plus seven random
 * letters — which makes the queue search unambiguous. The save answer's MRN
 * ({@code patientregistration/IUD} → {@code Message}) is kept as a cross-check of the queue's.</p>
 */
public class NewPatientRegistration extends BasePage {

    private final RegistrationPage reg;
    private final OutPatientQueueManagementPage queue;

    public NewPatientRegistration(Page page) {
        super(page);
        this.reg = new RegistrationPage(page);
        this.queue = new OutPatientQueueManagementPage(page);
    }

    /** What registering (and then finding) the patient produced. */
    public static class Result {
        public boolean saved;
        /** The patient as registered; null until the form was started. */
        public PatientProfile profile;
        /** Full name as registered, and the unique family-name token used to find the patient again. */
        public String name = "", family = "";
        public String saveToast = "";
        /** The MRN / visit id the save answer carried (cross-check; "" when not read). */
        public String saveMrn = "", visitId = "";
        /** How complete the visit section was ("" = complete) — a patient with no visit has no episode. */
        public String visitNote = "";
        /** The MRN found in the Outpatient Queue Management grid, and the queue row's details. */
        public String mrn = "", queueRow = "", mrnSource = "";
        /** Why a step failed ("" = no problem). */
        public String problem = "";
        /** True when the queue grid's MRN equals the save answer's. */
        public boolean mrnsAgree;
    }

    /** The registration form object, for callers that report its details. */
    public RegistrationPage registration() { return reg; }

    private static String uniqueFamily() {
        Random r = new Random(System.nanoTime());
        StringBuilder sb = new StringBuilder("Dr");
        for (int i = 0; i < 7; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    private void closeExtraTabs() {
        try {
            for (Page pg : new java.util.ArrayList<>(page.context().pages())) {
                if (pg != page && !pg.isClosed()) pg.close();
            }
        } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }
    }

    // ---- 1) register -----------------------------------------------------------------------------------------------

    /**
     * OP &gt; Registration: fill every section for a new random patient with a unique name, Save, confirm, and tidy up
     * the Consent modal and the report / consent tabs the save opens. {@link Result#saved} says whether the save went
     * through; {@link Result#problem} says why not.
     */
    public Result registerPatient(String baseUrl) {
        Result r = new Result();
        closeExtraTabs();
        if (!reg.open(baseUrl)) { r.problem = "the OP > Registration form's dropdown master data did not load"; return r; }

        PatientProfile p = PatientProfile.random();
        String family = uniqueFamily();
        String first = p.fullName.contains(" ") ? p.fullName.substring(0, p.fullName.indexOf(' ')) : p.fullName;
        p.fullName = first + " " + family;
        p.familyName = family;
        p.email = family.toLowerCase() + (1000 + new Random().nextInt(9000)) + "@example.com";
        r.profile = p; r.name = p.fullName; r.family = family;
        System.out.println("NewPatientRegistration: registering " + p.patientLine());

        reg.fillPatientInformation(p);
        reg.fillCorrespondence(p);
        reg.fillOtherDetails();
        boolean kin = reg.addNextOfKin(p);
        boolean payor = reg.selectPayorSelf();
        reg.fillVisitInformation();
        boolean visitOk = reg.visitInformationComplete();
        r.visitNote = visitOk ? "" : "Visit Information incomplete — " + reg.lastVisitState;
        if (!kin) System.out.println("NewPatientRegistration: the next of kin did not reach the grid (" + reg.lastKinFailReason + ")");
        if (!payor) System.out.println("NewPatientRegistration: Payor = Self did not populate (" + reg.lastPayorState + ")");

        String blocked = reg.clickSaveAwaitConfirm(p);
        if (blocked != null) {
            r.problem = "Save was blocked: " + blocked
                    + (reg.lastMandatoryFields.isEmpty() ? "" : " — the screen flagged: " + reg.lastMandatoryFields)
                    + (kin ? "" : " — the next of kin was not added")
                    + (payor ? "" : " — Payor = Self did not populate");
            return r;
        }
        SaveOutcome save = reg.confirmSaveAndAwaitSuccess();
        r.saved = save.saved;
        r.saveToast = save.toast == null ? "" : save.toast;
        r.saveMrn = reg.lastMrn; r.visitId = reg.lastVisitId;
        if (!save.saved) { r.problem = "the save was not confirmed: " + r.saveToast; return r; }

        // tidy up what the save opens: the PDPA Consent modal, the registration label tab, the consent form tab
        if (reg.waitConsentDetailsModal()) reg.closeConsentDetailsModal();
        closeExtraTabs();
        waitForAngular(800);
        return r;
    }

    // ---- 2) find the MRN in Outpatient Queue Management ------------------------------------------------------------

    private boolean onQueueScreen() {
        return page.url().toLowerCase().contains("#/queuemanagement")
                && Boolean.TRUE.equals(page.evaluate("() => [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()) && b.offsetParent!==null)"));
    }

    /**
     * Open <b>Outpatient Queue Management</b>. Straight after Registration the app guards a typed route with its
     * "Reload page" prompt, and {@link OutPatientQueueManagementPage#navigateTo} (a typed route) then sits for 30 s waiting
     * for a screen that never loads — so the OP menu entry goes first, and the existing {@code navigateTo} is the fallback.
     * Returns true when the queue screen is up.
     */
    public boolean openQueue(String baseUrl) {
        try { page.waitForFunction("() => [...document.querySelectorAll('a')].some(x=>x.textContent.trim()==='OP')", null, new Page.WaitForFunctionOptions().setTimeout(20000)); } catch (Exception ignore) { }
        for (int i = 0; i < 3 && !onQueueScreen(); i++) {
            try {
                page.evaluate("() => { const op=[...document.querySelectorAll('a')].find(x=>x.textContent.trim()==='OP'); if(op) op.click(); }");
                waitForAngular(1200);
                page.evaluate("() => { const a=document.querySelector(\"a[href='#/queueManagement']\"); if(a) a.click(); }");
            } catch (Exception e) { System.out.println("NewPatientRegistration: menu click raced a navigation - " + e.getMessage()); }
            waitForAngular(2500);
            try {
                page.waitForFunction("() => [...document.querySelectorAll('button')].some(b=>/^search$/i.test((b.textContent||'').trim()))",
                        null, new Page.WaitForFunctionOptions().setTimeout(15000));
            } catch (Exception ignore) { }
        }
        if (onQueueScreen()) return true;
        System.out.println("NewPatientRegistration: the menu did not open the queue screen — trying the existing navigateTo");
        queue.navigateTo(baseUrl);
        return onQueueScreen();
    }

    /**
     * Search today's queue and read the new patient's MRN off the grid. The patient is found by the unique family-name
     * token; when several rows match, the one with the saved visit id is taken (else the newest). The MRN is compared
     * with the save answer's. Fills {@link Result#mrn}, {@link Result#queueRow}, {@link Result#mrnSource},
     * {@link Result#mrnsAgree}; {@link Result#problem} says what went wrong.
     */
    public Result findMrnInQueue(String baseUrl, Result r) {
        r.mrn = ""; r.queueRow = ""; r.mrnSource = ""; r.mrnsAgree = false;
        if (!openQueue(baseUrl)) { r.problem = "Outpatient Queue Management did not open"; return r; }
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        List<Map<String, String>> rows = java.util.Collections.emptyList();
        for (int attempt = 0; attempt < 3 && rows.isEmpty(); attempt++) {
            queue.searchQueue(today, today);
            rows = queue.queueRowsMatching(r.family);   // letters only, so it is a safe JavaScript regex as it stands
            if (rows.isEmpty()) waitForAngular(2000);   // a just-registered visit can take a moment to reach the queue
        }
        if (rows.isEmpty()) {
            r.problem = "the queue grid has no row for \"" + r.name + "\" (searched " + today + ")";
            return r;
        }
        Map<String, String> row = rows.get(0);
        if (!r.visitId.isEmpty()) {
            for (Map<String, String> c : rows) if (r.visitId.equals(c.get("visitId"))) { row = c; break; }
        }
        r.mrn = row.getOrDefault("mrn", "");
        r.queueRow = "name " + row.get("name") + ", MRN " + r.mrn + ", visit " + row.get("visitId") + ", queue no " + row.get("queueNo")
                + (rows.size() > 1 ? " (" + rows.size() + " rows matched; the one for visit " + row.get("visitId") + " was taken)" : "");
        r.mrnSource = "Outpatient Queue Management grid";
        r.mrnsAgree = r.saveMrn.isEmpty() || r.saveMrn.equals(r.mrn);
        if (!r.mrnsAgree) r.problem = "the queue grid says MRN " + r.mrn + " but the save answer says " + r.saveMrn;
        if (r.mrn.isEmpty()) r.problem = "the queue row has no MRN";
        return r;
    }
}
