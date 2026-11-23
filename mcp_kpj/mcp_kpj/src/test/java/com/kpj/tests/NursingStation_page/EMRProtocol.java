package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named EMRProtocol — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>EMR Protocol</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>EMR Protocol</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Enter the <b>template name</b>.</li>
 *   <li>Select <b>Gender</b>.</li>
 *   <li>Select the <b>form</b>.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p>The template name is made unique per run ({@code EMR Auto <timestamp>}) — protocol names are the kind
 * of field an app rejects as duplicate, which would make a fixed name pass once and fail thereafter.
 * Override with {@code -DtemplateName=}.</p>
 *
 * <p>&#9888; A successful run CREATES a real EMR protocol in the target environment.</p>
 *
 * <h2>BLOCKED on devhis — the Form list is empty</h2>
 * <p>As of 2026-08-10 the flow ends FAIL at <b>Select form</b>, and Save fails as a consequence. Steps
 * 1-5 pass: the screen opens, New opens the form, the template name is entered and a Gender is selected.</p>
 * <ul>
 *   <li>{@code EMRProtocol.FormID} offers <b>only its "-Select-" placeholder</b> ({@code opts=1}) — there
 *       is nothing to choose. Not gender-dependent: it stays empty for Male as well as Ambiguous.</li>
 *   <li>The app corroborates that the field is mandatory — Save answers <b>"Please Enter Form!"</b>.</li>
 * </ul>
 * <p>This is missing EMR form master data, not a locator problem: the correct control is found and named
 * in the report. The flow should pass unchanged once forms exist.</p>
 *
 * <p><b>Reported as FAIL, deliberately.</b> The cause is a configuration gap rather than broken code, but
 * the screen cannot be saved at all as delivered — Form is mandatory, nothing can be chosen, no record is
 * created and no success toast appears. Both steps therefore fail with the reason stated in full, rather
 * than being softened to MANUAL: an EMR protocol simply cannot be created in this environment.</p>
 *
 * <h2>The screen has TWO panels with duplicate controls</h2>
 * <p>A search filter and the New form both carry {@code EMRProtocol.GenderID} and
 * {@code EMRProtocol.FormID}, and there are two name inputs — {@code TemplateName} (search) and
 * {@code TempName} (the form). Targeting by ng-model alone fills the SEARCH panel and leaves the form
 * empty, which looks like the form ignoring input. Every control here is therefore scoped to the panel
 * that owns the Save button.</p>
 */
public class EMRProtocol extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public EMRProtocol() { super("NursingStation_EMRProtocol"); }

    public static void main(String[] args) {
        EMRProtocol t = new EMRProtocol();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - EMR Protocol", "Nursing Station > EMR Protocol",
                "&#9888; Creates a REAL EMR protocol: New, enter the template name, select Gender and the "
                        + "form, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        // Unique by default: a duplicate protocol name would make this pass once and fail on every re-run.
        String templateName = System.getProperty("templateName",
                "EMR Auto " + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMdd-HHmmss")));

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.EMRProtocol emr =
                new com.kpj.pages.NursingStation_page.EMRProtocol(page);

        // 1) Navigate — escalates menu click -> hash route -> full page load.
        boolean rendered = emr.navigateViaMenu(BASE);
        step(page, "Open EMR Protocol screen",
                "Click Nursing Station -> EMR Protocol (retrying via the route and a full page load)",
                "The EMR Protocol screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + emr.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) New
        boolean formOpen = emr.clickNew();
        emr.describeControls();   // diagnostics: the real ng-models
        step(page, "Click New", "Click New to open the protocol form",
                "The EMR Protocol form opens",
                formOpen ? "New clicked (" + page.url() + ")" : "New did NOT open a form",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — protocol form not reached"); return; }

        // 3) Template name
        String name = emr.enterTemplateName(templateName);
        boolean nameOk = !name.startsWith("(");
        step(page, "Enter template name", "Enter the template name " + templateName,
                "The template name is entered",
                nameOk ? "Template name = " + name : "Template name NOT entered " + name,
                nameOk ? "PASS" : "FAIL");

        // 4) Gender — located by its options (Male/Female), not by a guessed model name
        String gender = emr.selectGender(System.getProperty("gender"));
        boolean genderOk = !gender.startsWith("(");
        step(page, "Select gender",
                "Select the Gender (the select whose options include Male/Female; prefers a real gender "
                        + "because the Form list is filtered by it)",
                "A gender is selected",
                genderOk ? "Gender = " + gender : "Gender NOT selected " + gender,
                genderOk ? "PASS" : "FAIL");

        // 5) Form. FAIL, not MANUAL: Form is mandatory and its list is empty, so the screen cannot be
        //    saved at all as delivered. The reason is spelled out in the step so the report says WHY it
        //    failed rather than leaving a bare "(no-option)".
        String form = emr.selectForm();
        boolean formOk = !form.startsWith("(");
        boolean formListEmpty = form.startsWith("(no-option");
        step(page, "Select form", "Select the form",
                "A form is selected",
                formOk ? "Form = " + form
                       : formListEmpty
                         ? "FAILED — no form can be selected. EMRProtocol.FormID holds only its "
                           + "\"-Select-\" placeholder (opts=1) after waiting 15s for the list to load, "
                           + "while Gender beside it has 7 options. Not gender dependent (empty for Male "
                           + "and Ambiguous alike) and not a locator problem — the correct control is "
                           + "found and named. Form is mandatory, so the record cannot be saved: no EMR "
                           + "forms are configured in this environment."
                         : "Form NOT selected " + form,
                formOk ? "PASS" : "FAIL");

        // 6) Save -> toast. Also FAIL: no record is created and no success toast appears.
        String toast = emr.saveAndGetToast();
        String tl = toast == null ? "" : toast.toLowerCase();
        boolean ok = tl.contains("success") || tl.contains("saved") || tl.contains("added") || tl.contains("updated");
        boolean blockedByForm = !ok && formListEmpty;
        String actual = toast == null || toast.isEmpty()
                ? "No toast appeared — " + emr.lastSaveDiagnostics
                : (ok ? toast
                      : blockedByForm
                        ? "FAILED — nothing was saved and no success toast appeared. Save answered \""
                          + toast + "\", because the mandatory Form field has no option to choose. The "
                          + "protocol cannot be created until EMR forms are configured."
                        : "Save not confirmed — server returned: \"" + toast + "\"");
        step(page, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast", actual, ok ? "PASS" : "FAIL");

        addSummary("Template name", templateName);
        addSummary("Gender", emr.lastGender);
        addSummary("Form", emr.lastForm);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
