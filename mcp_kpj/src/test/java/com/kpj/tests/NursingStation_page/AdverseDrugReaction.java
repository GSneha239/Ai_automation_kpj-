package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named AdverseDrugReaction — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Adverse Drug Reaction</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Adverse Drug Reaction</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>Select the <b>drug administered</b>.</li>
 *   <li>Select <b>Severity</b> and <b>Reaction Recorded By</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Defaults to {@link #DEFAULT_MRN}; override with {@code -Dmrn=}. The drug list is
 * populated from the patient's administered drugs, so a patient with no drug history will leave it
 * empty — that shows up as its own step failure rather than a mysterious save error.</p>
 *
 * <p>&#9888; A successful run RECORDS a REAL adverse drug reaction against the patient.</p>
 *
 * <h2>The MRN decides whether this screen works</h2>
 * <p>This flow used to fail at <b>Enter MRN &amp; click Search</b>, with Save then answering
 * <b>"Please Select Patient!"</b>, and that was recorded here as the screen being blocked. <b>It is not.</b>
 * The screen refuses MRNs that resolve perfectly well elsewhere — 100000956 works on Vitals Details and
 * Intake Output Chart but not here. Given a patient it accepts, every step passes and the reaction saves.</p>
 *
 * <p>So the flow no longer depends on one hard-coded MRN. It tries {@code -Dmrn=}, then
 * {@link #FALLBACK_MRNS}, and if none attach it opens the screen's own patient lookup
 * ({@code openPopupScreen()} &rarr; its Search) and retries with the MRNs that returns. On 2026-08-11,
 * 100000956, 100001148, 100001061, 100001060 and 100000888 were all refused and <b>100000684</b> attached,
 * after which all six steps passed.</p>
 *
 * <p><b>Caution when reading this step.</b> The MRN sitting in the model proves nothing: it is simply the
 * value entered. An earlier version of this check matched on that alone and reported the patient as
 * attached while the app still refused to save. The assertion requires the SAME model object to carry the
 * MRN and real patient detail (a name or patient id).</p>
 *
 * <p>The drug list is populated from the patient's administered drugs, so a patient with no drug history
 * leaves it empty — that shows up as its own step failure rather than a mysterious save error.</p>
 */
public class AdverseDrugReaction extends DevHisBase {

    /** Used when {@code -Dmrn=} is not supplied. */
    public static final String DEFAULT_MRN = "100000956";

    /**
     * Tried in order after {@code -Dmrn=} when the patient does not attach.
     *
     * <p>This screen refuses MRNs that resolve on other screens, and Save then answers "Please Select
     * Patient!". Override the list with {@code -Dmrns=a,b,c}. If none attach, the run asks the screen's own
     * patient lookup for MRNs this environment actually has.</p>
     */
    public static final String[] FALLBACK_MRNS = {
            // Confirmed to attach on this screen (2026-08-11) — kept first so a run does not spend five
            // attempts getting there.
            "100000684", "100000687",
            "100001148", "100001061", "100001060", "100000888"
    };

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public AdverseDrugReaction() { super("NursingStation_AdverseDrugReaction"); }

    public static void main(String[] args) {
        AdverseDrugReaction t = new AdverseDrugReaction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Adverse Drug Reaction", "Nursing Station > Adverse Drug Reaction",
                "&#9888; Records a REAL adverse drug reaction: search the patient by MRN, select the drug "
                        + "administered, the severity and who recorded it, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.AdverseDrugReaction adr =
                new com.kpj.pages.NursingStation_page.AdverseDrugReaction(page);

        // 1) Navigate
        boolean rendered = adr.navigateViaMenu(BASE);
        step(page, "Open Adverse Drug Reaction screen",
                "Click Nursing Station -> Adverse Drug Reaction (retrying via the route and a full page load)",
                "The Adverse Drug Reaction screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + adr.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // The screen may land on a list; open the entry form only if the MRN field is not already present.
        adr.openFormIfNeeded();
        adr.describeControls();   // diagnostics: the real ng-models

        // 2) MRN + Search — verified against the model, not just the input box. This screen answers
        //    "Please Select Patient!" on Save when the lookup never resolved, so try the candidates in
        //    turn and, if none attach, ask the screen's own patient lookup for MRNs that exist here.
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (String s : System.getProperty("mrn", DEFAULT_MRN).split(",")) candidates.add(s.trim());
        for (String s : System.getProperty("mrns", String.join(",", FALLBACK_MRNS)).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !candidates.contains(t)) candidates.add(t);
        }

        String searchResult = adr.searchByMrnTrying(candidates);
        boolean attached = adr.patientAttached();

        if (!attached) {
            java.util.List<String> discovered = adr.discoverMrns(10);
            addSummary("MRNs offered by the patient lookup",
                    discovered.isEmpty() ? "(none found)" : discovered.toString());
            if (!discovered.isEmpty()) {
                searchResult = adr.searchByMrnTrying(discovered);
                attached = adr.patientAttached();
            }
        }
        if (attached) mrn = adr.workingMrn;
        addSummary("MRNs tried", adr.mrnAttempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN and click the search icon (SearchPatientByMRNo), trying candidates until a "
                        + "patient attaches",
                "The patient is attached and their administered drugs load",
                attached ? "MRN " + mrn + " attached. " + searchResult
                         : "No patient attached with any MRN. Tried: " + adr.mrnAttempts
                           + ". Last result: " + searchResult,
                attached ? "PASS" : "FAIL");

        // 3) Drug administered
        String drug = adr.selectDrugAdministered();
        boolean drugOk = !drug.startsWith("(");
        step(page, "Select drug administered",
                "Select the drug administered (the list is populated from the patient)",
                "A drug is selected",
                drugOk ? "Drug = " + drug : "Drug NOT selected " + drug,
                drugOk ? "PASS" : "FAIL");

        // 4) Severity + recorded by
        String severity = adr.selectSeverity();
        String recordedBy = adr.selectRecordedBy();
        boolean sevOk = !severity.startsWith("(") && !recordedBy.startsWith("(");
        step(page, "Select severity and reaction recorded by",
                "Select the Severity and Reaction Recorded By",
                "Both are selected",
                "Severity = " + severity + " | Recorded by = " + recordedBy,
                sevOk ? "PASS" : "FAIL");

        // 5) Save -> toast
        String toast = adr.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + adr.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Patient attached", attached ? "yes" : "NO — " + adr.lastFormSearch);
        addSummary("Drug administered", adr.lastDrug);
        addSummary("Severity", adr.lastSeverity);
        addSummary("Recorded by", adr.lastRecordedBy);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
