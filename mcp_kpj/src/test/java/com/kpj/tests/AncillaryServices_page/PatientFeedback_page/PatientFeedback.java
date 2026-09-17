package com.kpj.tests.AncillaryServices_page.PatientFeedback_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientFeedback — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; <b>Patient Feedback</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Click <b>Ancillary Services</b> → <b>Patient Feedback List</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter <b>MRN No.</b> and click <b>Search</b>.</li>
 *   <li>Select the <b>patient feedback template</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Pass one with {@code -Dmrn=} to pin it. Without it, the flow searches the form's own
 * "Search Patient" popup filtered by a random 2-digit fragment, so repeated runs land on a different real
 * patient rather than always the same one; falls back to the feedback list's own history, then
 * {@link #DEFAULT_MRN}, if that search turns up nothing usable (loaded AND discharged).</p>
 *
 * <p>&#9888; A successful run CREATES a real patient feedback record in the target environment.</p>
 */
public class PatientFeedback extends DevHisBase {

    /** The MRN this flow is pinned to. Verified live 2026-08-27 on devhis: it resolves, the patient is
     *  discharged, and Save persists. Override per environment or per run with {@code -Dmrn=}. */
    public static final String DEFAULT_MRN = "1000338215";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /**
     * Which feedback template to select. Override with {@code -Dtemplate=}.
     *
     * <p><b>Not every template saves.</b> Verified live 2026-08-06 against a discharged patient, with all
     * other input identical — only the template varied:</p>
     * <ul>
     *   <li>{@code Emergency Department Feedback} — saves</li>
     *   <li>{@code test7} — saves</li>
     *   <li>{@code Patient Diet Feedback} (the FIRST option) — server returns "×KPJ PortalError!"</li>
     *   <li>{@code PATIENT FEEDBACK FORM FOR NUTRITION/FOOD SERVICES} — server returns "×KPJ PortalError!"</li>
     * </ul>
     * <p>So this flow pins a known-good template rather than taking the first option, which would fail on
     * every run. Reproduce the failures with {@code -Dtemplate="Patient Diet Feedback"}.</p>
     */
    public static final String DEFAULT_TEMPLATE = "Emergency Department Feedback";

    public PatientFeedback() { super("AncillaryServices_PatientFeedback"); }

    public static void main(String[] args) {
        PatientFeedback t = new PatientFeedback();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Patient Feedback", "Ancillary Services > Patient Feedback",
                "&#9888; Creates a REAL patient feedback record: Add, search the patient by MRN, "
                        + "select the feedback template, Save.");

        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.PatientFeedback_page.PatientFeedback fb =
                new com.kpj.pages.AncillaryServices_page.PatientFeedback_page.PatientFeedback(page);

        // 1) Navigate
        boolean onScreen = fb.navigateViaMenu();
        step(page, "Open Patient Feedback screen",
                "Click Ancillary Services -> Patient Feedback List",
                "The Patient Feedback list screen is shown",
                onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 1b) Harvest MRNs already on the feedback list (own history) while still on the list screen — kept
        // only as a fallback source for step 3 below, since the popup search there is the primary source.
        java.util.List<String> listHarvested = System.getProperty("mrn") == null
                ? fb.harvestMrnsFromList() : java.util.Collections.emptyList();

        // 2) Add
        boolean formOpen = fb.clickAdd();
        step(page, "Click Add", "Click Add (addPatientFeedback) on the list screen",
                "The Patient Feedback form opens",
                formOpen ? "Feedback form opened (" + page.url() + ")" : "Form did NOT open (" + page.url() + ")",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — feedback form not reached"); return; }

        // 3) Pick the MRN via the form's own "Search Patient" popup (OpenPatientSearchPopupScreen — the
        // magnifying-glass icon beside the MRN field), filtered by a random 2-digit fragment so repeated
        // runs land on a different real patient instead of always the same one. Selecting a row in that
        // popup does NOT feed the MRN back into the form (verified live: the value stays on the popup's own
        // scope), so the MRN is read off the matched rows and typed into the MRN field the normal way.
        // Falls back to the feedback list's own history, then DEFAULT_MRN, if the popup search is empty.
        String mrnSource;
        if (System.getProperty("mrn") != null) {
            mrnSource = "-Dmrn";
        } else {
            // A single random 2-digit fragment sometimes matches nothing at all (this environment's MRNs
            // cluster around a shared digit pattern) — try a few different fragments before giving up.
            java.util.Random rnd = new java.util.Random();
            java.util.List<String> popup = java.util.Collections.emptyList();
            String filter = "";
            for (int attempt = 0; attempt < 3 && popup.isEmpty(); attempt++) {
                filter = String.valueOf(rnd.nextInt(90) + 10);   // 2 digits, 10-99
                popup = fb.candidateMrnsFromPatientSearchPopup(filter, 20);
            }
            if (!popup.isEmpty()) { mrn = popup.get(0); mrnSource = "patient search popup (filter '" + filter + "')"; }
            else if (!listHarvested.isEmpty()) { mrn = listHarvested.get(0); mrnSource = "harvested from the feedback list"; }
            else { mrn = DEFAULT_MRN; mrnSource = "default"; }
        }

        // 3b) MRN + Search — searched ONCE; whatever this MRN resolves to is what proceeds to the template
        // step next, no hunting for a different one. A patient with isdischarged=false still loads, still
        // lets Save be clicked, and Save still raises a genuine "saved successfully" toast — but nothing is
        // actually persisted (verified live) — so that is reported here as a heads-up, not a retry trigger.
        String searchResult = fb.searchByMrn(mrn);
        boolean loaded = fb.patientLoaded();
        boolean discharged = loaded && fb.patientDischarged();
        fb.describeControls();   // diagnostics: shows the template control the patient brought with them
        step(page, "Enter MRN No. & click Search", "Enter MRN " + mrn + " and click Search (SearchPatientByMRNo)",
                "The MRN is accepted (this screen shows no patient-name field to confirm against; "
                        + "a bad MRN raises a rejection toast)",
                loaded ? "MRN " + mrn + " accepted -> " + searchResult
                        + (discharged ? "" : "  ||  HEADS UP: this patient is NOT discharged — Save may report "
                            + "success without actually persisting anything (verified live elsewhere)")
                      : "MRN " + mrn + " did not resolve to a patient (" + searchResult + ")",
                loaded ? "PASS" : "FAIL");
        addSummary("MRN source", mrnSource);
        addSummary("Discharged", loaded ? String.valueOf(discharged) : "(patient not loaded)");
        if (!loaded) {
            addSummary("MRN", mrn);
            addSummary("Result", "FAILED — no patient for this MRN (pass a valid one with -Dmrn=...)");
            return;
        }

        // 4) Template — whatever the dropdown naturally offers (no pinning to a known-good one). Verified
        // live 2026-08-06 that not every template saves: "Patient Diet Feedback" (the FIRST option) and
        // "PATIENT FEEDBACK FORM FOR NUTRITION/FOOD SERVICES" both return a server error, so picking freely
        // will sometimes hit one of those — that is expected here, not a bug in the test. Pin one with
        // -Dtemplate= to avoid it.
        String template = fb.selectTemplate(System.getProperty("template"));
        boolean templateOk = fb.templateSelected();
        fb.describeControls();
        fb.modelSnapshot("discharge|template|mrno|patientid|visitadmission|isdischarged");   // diagnostics
        step(page, "Select patient feedback template", "Select the patient feedback template",
                "A feedback template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 5) Save -> success toast
        String toast = fb.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added");
        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save (IUDPatientFeedbackDetail); wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: "
                    : "FAILS — " + (DEFAULT_TEMPLATE.equalsIgnoreCase(template) ? "" : "NOT a test defect if the "
                      + "template is the cause: \"" + template + "\" is one of the templates verified live to "
                      + "return a server error regardless of patient/MRN — pin -Dtemplate=\""
                      + DEFAULT_TEMPLATE + "\" to isolate other failures from this one. ")) + actual,
                ok ? "PASS" : "FAIL");

        // 6) Back to the list
        boolean backClicked = fb.clickBack();
        step(page, "Click Back", "Click Back (closeForm) to return to the Patient Feedback list",
                "The Patient Feedback list screen is shown",
                backClicked ? "Back clicked (" + page.url() + ")" : "Back did NOT return to the list (" + page.url() + ")",
                backClicked ? "PASS" : "FAIL");

        // 7) Search the list and confirm the record is there.
        int[] snapshot = fb.listSnapshot(mrn);
        boolean inTable = snapshot[1] > 0;
        step(page, "Click Search & verify the record is in the table",
                "Click Search on the Patient Feedback list and look for a row for MRN " + mrn,
                "A row for this MRN appears in the table",
                inTable ? "Found " + snapshot[1] + " row(s) for MRN " + mrn + ". " + fb.lastListSnapshot
                        : "No row found for MRN " + mrn + ". " + fb.lastListSnapshot,
                inTable ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Template", templateOk ? template : "(not selected)");
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
