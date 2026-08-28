package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named BirthCertificate — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Birth Certificate</b>.
 *
 * <p>Intended steps: MRN + Search → certificate template → Child Details (name of child, name of father,
 * address, gender, date of birth, place of birth) → form template body → Department + Doctor →
 * Authenticate → Save → toast.</p>
 *
 * <h2>Two DevHIS findings this flow pins down</h2>
 *
 * <p><b>1. The menu link does not render the screen.</b> Clicking <i>Nursing Station → Birth
 * Certificate</i> leaves an empty shell (body text just "Welcome: Employee 1306:(KPJ)", no form).
 * Navigating to the same route another way renders it correctly, so the route is fine and the menu path
 * is not. {@code navigateViaMenu} therefore escalates: menu click → hash change → full page load.</p>
 *
 * <p><b>2. The screen accepts only certain patients — it is NOT a broken Save.</b> This flow used to end
 * with "Patient Not Found!", an empty model and a Save that did nothing, and that was recorded here as a
 * defect. <b>That conclusion was wrong.</b> The cause is the MRN: this screen rejects MRNs that resolve
 * perfectly well elsewhere (100000956 works on Vitals Details and Intake Output Chart but not here).
 * Given a patient it accepts, every step passes and the certificate saves.</p>
 *
 * <p>So the flow no longer depends on one hard-coded MRN. It tries {@code -Dmrn=}, then
 * {@link #FALLBACK_MRNS}, and if none attach it opens the screen's <b>own patient lookup</b>
 * ({@code openPopupScreen()} → {@code SearchPatient(0)}) and retries with the MRNs that lookup returns —
 * patients this environment actually has. A patient is judged attached by the Angular model, not by the
 * toast. On 2026-08-11 that discovery returned 100001148, 100001061, 176377384, 100001060, 146928904 and
 * 100000888; MRN <b>100001148</b> attached and all 12 steps passed, Save included.</p>
 *
 * <p>Useful facts from the earlier investigation that still hold: the Save handler is present
 * ({@code typeof scope.IUDDischaresummary === 'function'}), the sibling <b>Death Certificate</b> screen
 * reuses the same controller and handler, and the Child Details block has no bearing on whether Save
 * works. All of that was consistent with the real cause — the patient, not the plumbing.</p>
 */
public class BirthCertificate extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /**
     * Used when {@code -Dmrn=} is not supplied — a patient the screen's own query can actually return.
     *
     * <p>See {@link #FALLBACK_MRNS} for why this is a Death Certificate MRN.</p>
     */
    public static final String DEFAULT_MRN = "100000684";

    /**
     * Tried in order after {@code -Dmrn=} when the patient does not attach.
     *
     * <p><b>Why this screen refuses ordinary MRNs.</b> Its search posts
     * {@code {"ExecFlag":"GetPatientInfo","MRNO":…,"opd_ipd":2,"isdead":1}} — it asks for an
     * <b>inpatient who is marked deceased</b>. Anything else comes back as an empty array, and the screen
     * then silently clears the whole model without a message, which is why this used to look like a
     * broken Save. Captured from the live request on 2026-08-13; the Save handler being
     * {@code IUDDischaresummary} and the Death Certificate screen sharing this controller are the same
     * story — Birth Certificate is a copy of Death Certificate and inherited its patient query.</p>
     *
     * <p>So the MRNs that work here are the ones the Death Certificate flow uses. Override the whole list
     * with {@code -Dmrns=a,b,c}. If none attach, the run asks the screen's own patient lookup — though
     * that lookup returns living outpatients, which this query cannot match.</p>
     */
    public static final String[] FALLBACK_MRNS = {
            // Deceased inpatients — the only kind this screen's query returns (100000684 = LISA, verified).
            "100000684", "100000687",
            // Kept as cheap extra tries; these attach on other Nursing Station screens.
            "100001148", "100001061", "100001060", "100000888"
    };

    public BirthCertificate() { super("NursingStation_BirthCertificate"); }

    public static void main(String[] args) {
        BirthCertificate t = new BirthCertificate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Birth Certificate", "Nursing Station > Birth Certificate",
                "Issue a birth certificate: search the patient by MRN, pick the template, fill the child "
                        + "details and template body, pick Department + Doctor, authenticate and Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String childName = System.getProperty("childName", "Baby Auto Test");
        String fatherName = System.getProperty("fatherName", "Ahmad Auto Test");
        String address = System.getProperty("address", "12 Jalan Test, Kuala Lumpur");
        String dob = System.getProperty("dob",
                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        String placeOfBirth = System.getProperty("placeOfBirth", "KPJ Hospital");
        String bodyText = System.getProperty("bodyText",
                "<p>Automated test — birth certificate template body.</p>");

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.BirthCertificate bc =
                new com.kpj.pages.NursingStation_page.BirthCertificate(page);

        // 1) Navigate — escalates menu click -> hash route -> full page load before giving up.
        boolean rendered = bc.navigateViaMenu(BASE);
        bc.describeControls();
        step(page, "Open Birth Certificate screen",
                "Click Nursing Station -> Birth Certificate (then retry via the route and a full page load)",
                "The Birth Certificate form is shown",
                rendered ? "Opened " + page.url()
                         : "Route " + page.url() + " resolved but rendered NO form. Body: \""
                           + bc.lastBodyText + "\" | controls: " + bc.lastControls,
                rendered ? "PASS" : "FAIL");
        if (!rendered) {
            addSummary("MRN", mrn);
            addSummary("Route", com.kpj.pages.NursingStation_page.BirthCertificate.ROUTE);
            addSummary("Body served", bc.lastBodyText);
            addSummary("Result",
                    "BLOCKED — the screen does not render. Tried the menu link, the hash route and a full "
                    + "page load; each resolved the route but served an empty shell. The sibling "
                    + "Certificate screen (#/Certificate) renders normally.");
            return;
        }

        // 2) Add opens the certificate form
        boolean formOpen = bc.clickAdd();
        step(page, "Click Add", "Click Add (AddBirthCertificate) to open the certificate form",
                "The Birth Certificate form opens",
                formOpen ? "Form opened (" + page.url() + ")" : "Form did NOT open",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — certificate form not reached"); return; }

        // 3) MRN + Search on the form (this is what binds the patient to the certificate).
        //    This screen answers "Patient Not Found!" for an MRN that works elsewhere, so try the
        //    candidates in turn and, if none attach, ask the app for real MRNs via its own patient lookup.
        java.util.List<String> candidates = new java.util.ArrayList<>();
        for (String s : System.getProperty("mrn", DEFAULT_MRN).split(",")) candidates.add(s.trim());
        for (String s : System.getProperty("mrns", String.join(",", FALLBACK_MRNS)).split(",")) {
            String t = s.trim();
            if (!t.isEmpty() && !candidates.contains(t)) candidates.add(t);
        }

        String searchResult = bc.searchPatientTryingMrns(candidates);
        boolean attached = bc.patientAttached();

        if (!attached) {
            java.util.List<String> discovered = bc.discoverMrns(6);
            addSummary("MRNs offered by the patient lookup", discovered.isEmpty() ? "(none found)" : discovered.toString());
            if (!discovered.isEmpty()) {
                searchResult = bc.searchPatientTryingMrns(discovered);
                attached = bc.patientAttached();
            }
        }
        if (attached) mrn = bc.workingMrn;
        addSummary("MRNs tried", bc.mrnAttempts);

        step(page, "Enter MRN & click Search",
                "Enter an MRN and click the search icon (SearchPatientByMRNo), trying the candidates in "
                        + "turn until a patient attaches",
                "The patient is attached to the certificate",
                attached ? "MRN " + mrn + " attached. " + searchResult
                         : "No patient attached with any MRN. Tried: " + bc.mrnAttempts
                           + ". Last result: " + searchResult,
                attached ? "PASS" : "FAIL");

        // 4) Certificate template
        String template = bc.selectTemplate();
        boolean templateOk = !template.startsWith("(");
        step(page, "Select certificate template",
                "Select the Certificate Template (ng-model BirthCertificate1.Dischargeid)",
                "A certificate template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 5) Child details
        String child = bc.fillChildDetails(childName, fatherName, address);
        boolean childOk = !child.contains("(no-field)");
        step(page, "Enter child details", "Enter Name of child, Name of Father and Address",
                "All three child fields are entered", child, childOk ? "PASS" : "FAIL");

        // 6) Gender
        String gender = bc.selectGender();
        boolean genderOk = !gender.startsWith("(");
        step(page, "Select gender", "Select the child's Gender",
                "A gender is selected",
                genderOk ? "Gender = " + gender : "Gender NOT selected " + gender,
                genderOk ? "PASS" : "FAIL");

        // 7) DOB + place of birth
        String dobPlace = bc.enterBirthDateAndPlace(dob, placeOfBirth);
        boolean dobOk = !dobPlace.contains("(no-field)");
        step(page, "Enter date of birth and place of birth",
                "Enter Date of Birth " + dob + " and Place of Birth " + placeOfBirth,
                "Both are entered", dobPlace, dobOk ? "PASS" : "FAIL");

        // 8) Form template body (CKEditor)
        String bodyRes = bc.enterTemplateBody(bodyText);
        boolean bodyOk = !bodyRes.startsWith("(");
        step(page, "Enter details in the form template body",
                "Type into the certificate body (a CKEditor, not a textarea)",
                "The template body holds the text",
                bodyOk ? "Body written via " + bodyRes : "Could not write the body " + bodyRes,
                bodyOk ? "PASS" : "FAIL");

        // 9) Department + doctor
        String deptDoc = bc.selectDepartmentAndDoctor();
        boolean ddOk = !deptDoc.contains("(no-option)") && !deptDoc.contains("(no-field)");
        step(page, "Select department and doctor", "Select the Department then the Doctor",
                "Both are selected", deptDoc, ddOk ? "PASS" : "FAIL");

        // 10) Authenticate
        boolean auth = bc.tickAuthenticate();
        step(page, "Tick Authenticate", "Tick the Authenticate checkbox (BirthCertificate1.isfinalized)",
                "Authenticate is ticked",
                auth ? "Authenticate ticked" : "Authenticate checkbox not found / not ticked",
                auth ? "PASS" : "FAIL");

        // 11) Save -> toast. Count the tabs first: Save is supposed to generate the certificate report,
        // which DevHIS opens in a new one.
        int tabsBefore = page.context().pages().size();
        String toast = bc.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + bc.lastSaveDiagnostics
                : (ok ? toast : "Save not confirmed — server returned: \"" + toast + "\"");
        // A silent save is ambiguous — settle it by looking for the certificate in the list.
        String postCheck = "";
        if (!ok) {
            postCheck = bc.verifySavedInList(mrn, childName);
            addSummary("Post-save check", postCheck);
        }
        step(page, "Click Save & success toast", "Click Save (IUDDischaresummary); wait for the success toast",
                "'... saved successfully' toast",
                actual + (postCheck.isEmpty() ? "" : "  ||  Post-save check: " + postCheck),
                ok ? "PASS" : "FAIL");

        // 12) Save must generate the certificate report — and a blank one is a failure.
        String reportInfo = bc.captureReport(tabsBefore, 15000);
        addSummary("Report", reportInfo);
        boolean reportOk = bc.reportProduced() && !bc.reportBlank;
        step(page, "Save generates the certificate report",
                "After Save, the birth certificate report is generated",
                "A report opens and carries the certificate's content",
                (reportOk
                    ? "PASSES because a report was generated and it carries content: " + reportInfo
                    : bc.reportProduced()
                    ? "FAILS because the report generated by Save is BLANK — it opens, but there is "
                      + "nothing on it, so the certificate cannot be printed or handed to the patient. "
                      + "Judged on the response bytes and on the rendered page (a PDF viewer exposes no "
                      + "text, so an empty-text check would call every report blank). Evidence: "
                      + reportInfo
                    : "FAILS because Save generated NO report at all: " + reportInfo),
                reportOk ? "PASS" : "FAIL");

        addSummary("MRN", mrn);
        addSummary("Template", bc.lastTemplate);
        addSummary("Child / Father", childName + " / " + fatherName);
        addSummary("Gender", bc.lastGender);
        addSummary("DOB / Place", dob + " / " + placeOfBirth);
        addSummary("Department / Doctor", bc.lastDepartment + " / " + bc.lastDoctor);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
