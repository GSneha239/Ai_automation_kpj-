package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientLinking — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Patient Linking</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Patient Linking</b>.</li>
 *   <li>In <b>Patient Detail</b>: enter the <b>MRN</b>, click <b>Search</b>, select the <b>relationship</b>.</li>
 *   <li>In <b>Patient Link Details</b>: enter the <b>MRN</b>, click <b>Search</b>, select the
 *       <b>relationship</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The two halves take <b>different</b> patients: {@code -Dmrn=} for Patient Detail and
 * {@code -DlinkMrn=} for Patient Link Details. Linking a patient to themselves is not a meaningful test,
 * so the defaults differ.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL patient link in the target environment.</p>
 */
public class PatientLinking extends DevHisBase {

    /** The patient in the Patient Detail half. */
    public static final String DEFAULT_MRN = "100000956";
    /** The patient being linked to, in the Patient Link Details half. */
    public static final String DEFAULT_LINK_MRN = "100000684";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PatientLinking() { super("NursingStation_PatientLinking"); }

    public static void main(String[] args) {
        PatientLinking t = new PatientLinking();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Patient Linking", "Nursing Station > Patient Linking",
                "&#9888; Creates a REAL patient link: search a patient and a linked patient by MRN, pick a "
                        + "relationship for each, Add, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String linkMrn = System.getProperty("linkMrn", DEFAULT_LINK_MRN);

        com.kpj.pages.NursingStation_page.PatientLinking pl;

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        pl = new com.kpj.pages.NursingStation_page.PatientLinking(page);

        // 1) Navigate
        boolean rendered = pl.navigateViaMenu(BASE);
        step(page, "Open Patient Linking screen",
                "Click Nursing Station -> Patient Linking (retrying via the route and a full page load)",
                "The Patient Linking screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + pl.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        pl.describeControls();   // diagnostics: which sections were found and their ng-models
        addSummary("Sections found", pl.lastControls);

        final String SEC_PATIENT = com.kpj.pages.NursingStation_page.PatientLinking.SECTION_PATIENT;
        final String SEC_LINK = com.kpj.pages.NursingStation_page.PatientLinking.SECTION_LINK;

        // 2) Patient Detail — MRN + search. Candidates are tried in turn; the screen's own patient lookup
        //    is NOT used here, because it belongs to whichever section last had focus and would make the
        //    two sections fight over one patient.
        com.kpj.pages.MrnRetry.Result pHit = com.kpj.pages.MrnRetry.trySearch(
                "PatientLinking(patient)", page,
                com.kpj.pages.MrnRetry.candidates(DEFAULT_MRN),
                m -> pl.searchInSection(SEC_PATIENT, m), () -> pl.patientLoaded(SEC_PATIENT));
        String pSearch = pHit.lastSearch;
        boolean pOk = pHit.attached();
        if (pOk) mrn = pHit.mrn;
        addSummary("Patient Detail MRNs tried", pHit.attempts);

        step(page, "Patient Detail - enter MRN & search",
                "In the Patient Detail section enter an MRN and click its search icon, trying candidates "
                        + "until the patient loads",
                "The patient loads in the Patient Detail section",
                pOk ? "MRN " + mrn + " loaded. " + pSearch
                    : "No patient loaded with any MRN. Tried: " + pHit.attempts,
                pOk ? "PASS" : "FAIL");

        // 3) Patient Detail — relationship
        String pRel = pl.selectRelationship(SEC_PATIENT);
        boolean pRelOk = !pRel.startsWith("(");
        step(page, "Patient Detail - select relationship",
                "Select the relationship in the Patient Detail section",
                "A relationship is selected",
                pRelOk ? "Relationship = " + pRel : "Relationship NOT selected " + pRel,
                pRelOk ? "PASS" : "FAIL");

        // 4) Patient Link Details — MRN + search (a DIFFERENT patient). The patient chosen above is
        //    excluded, since linking someone to themselves is not the scenario.
        final String linkedTo = mrn;
        java.util.List<String> linkCandidates = new java.util.ArrayList<>(
                com.kpj.pages.MrnRetry.candidates(linkMrn));
        linkCandidates.removeIf(linkedTo::equals);
        com.kpj.pages.MrnRetry.Result lHit = com.kpj.pages.MrnRetry.trySearch(
                "PatientLinking(link)", page, linkCandidates,
                m -> pl.searchInSection(SEC_LINK, m), () -> pl.patientLoaded(SEC_LINK));
        String lSearch = lHit.lastSearch;
        boolean lOk = lHit.attached();
        if (lOk) linkMrn = lHit.mrn;
        addSummary("Link MRNs tried", lHit.attempts);

        step(page, "Patient Link Details - enter MRN & search",
                "In the Patient Link Details section enter an MRN and click its search icon, trying "
                        + "candidates until the linked patient loads",
                "The linked patient loads in the Patient Link Details section",
                lOk ? "MRN " + linkMrn + " loaded. " + lSearch
                    : "No linked patient loaded with any MRN. Tried: " + lHit.attempts,
                lOk ? "PASS" : "FAIL");

        // 5) Patient Link Details — relationship
        String lRel = pl.selectRelationship(SEC_LINK);
        boolean lRelOk = !lRel.startsWith("(");
        step(page, "Patient Link Details - select relationship",
                "Select the relationship in the Patient Link Details section",
                "A relationship is selected",
                lRelOk ? "Relationship = " + lRel : "Relationship NOT selected " + lRel,
                lRelOk ? "PASS" : "FAIL");

        // 6) Add
        String added = pl.clickAdd();
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        // "already added" is a DATA state, not a fault: these two patients are linked from an earlier run,
        // so the app is right to refuse. Distinguished from a silent rowsAdded=0, which IS a failure.
        boolean alreadyLinked = added != null && added.toLowerCase().matches(".*already\\s*(been\\s*)?added.*");
        step(page, "Click Add", "Click Add to add the link row",
                "A link row is added",
                added + (alreadyLinked
                        ? "  ||  These two patients are ALREADY linked from a previous run — the app is "
                          + "correctly refusing a duplicate. Use -DlinkMrn= with an unlinked patient for a "
                          + "fresh link."
                        : ""),
                addOk ? "PASS" : (alreadyLinked ? "MANUAL" : "FAIL"));

        // 7) Save -> toast
        String toast = pl.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added")
                || tl.contains("updated") || tl.contains("linked");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + pl.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Patient MRN / Link MRN", mrn + " / " + linkMrn);
        addSummary("Relationships", pl.lastPatientRelationship + "  |  " + pl.lastLinkRelationship);
        addSummary("Add", pl.lastAdd);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
