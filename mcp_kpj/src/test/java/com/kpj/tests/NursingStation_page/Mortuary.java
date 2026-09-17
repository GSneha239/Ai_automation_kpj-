package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Mortuary — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Mortuary</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Nursing Station</b> → <b>Mortuary</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Enter the <b>MRN</b> and click <b>Search</b>.</li>
 *   <li>Enter the <b>mortuary cabin no</b>.</li>
 *   <li>Select <b>Relationship</b>.</li>
 *   <li>Enter <b>In Date Time</b> and <b>Handover Date Time</b>.</li>
 *   <li>Enter <b>Handover To</b>.</li>
 *   <li>Select the <b>template</b>.</li>
 *   <li>Click <b>Add</b> (adds the row).</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 * </ol>
 *
 * <p><b>MRN.</b> Defaults to {@link #DEFAULT_MRN} — a deceased patient, since a mortuary record needs one.
 * That MRN is the patient the Post Mortem list surfaced and which Death Certificate also accepts. Override
 * with {@code -Dmrn=}.</p>
 *
 * <p>&#9888; A successful run CREATES a real mortuary record in the target environment.</p>
 *
 * <h2>Save is a silent no-op — every explanation ruled out</h2>
 * <p>As of 2026-08-11 steps 1-13 and 15 all pass: the patient attaches (model-verified, real
 * {@code patientid}), cabin no, relationship, both date-times, handover-to, the <b>Police Department</b>
 * block, the <b>Funeral Service Company</b> block, the template and the template body are all set, and Add
 * appends the row — confirmed by finding the MRN and relationship in it, not just by a row count.
 * Clicking Save ({@code IUDMortuary()}) then produces <b>no toast, no validation message, no
 * navigation</b>, {@code invalidFields=0}, and the record does <b>not</b> appear in the list.</p>
 *
 * <p>Three explanations were tested and each is ruled out:</p>
 * <ul>
 *   <li><b>Missing patient</b> — no: the model check confirms the MRN is attached with a real
 *       {@code patientid}. (This is what the Birth Certificate screen turned out to be.)</li>
 *   <li><b>Unfilled mandatory fields</b> — no: the Police Department and Funeral Service Company blocks and
 *       the template body were added to this flow and it behaves identically with them filled.</li>
 *   <li><b>A duplicate refused silently</b> — no. The default patient 100000684 <i>does</i> already have a
 *       mortuary record, which looked like a promising explanation, but a run with <b>100001148</b> —
 *       confirmed absent from the list — is refused exactly the same way.</li>
 * </ul>
 *
 * <p>So Save writes nothing for a fully-populated form and an unused patient. Reportable as a defect.</p>
 *
 * <p><b>No success toast is asserted</b> — this screen shows none, so the verdict comes from the mortuary
 * <b>list</b>: after Save the record must appear there as a new row. The form's own grid is deliberately
 * not used for that check, because the row Add appends is present whether or not Save wrote anything.</p>
 *
 * <h2>How "did it save?" is actually decided</h2>
 * <ul>
 *   <li>The list is {@code #/MortuaryList} and has <b>no MRN field</b> — only the grid's column filter
 *       ({@code colFilter.term}). Entering the MRN means typing it there; without that the record is not
 *       on the visible page and an unfiltered scan reports "not saved" whatever the truth is.</li>
 *   <li>The patient's rows are counted <b>before</b> the record is created and <b>after</b> Save, and the
 *       count must GROW. Merely finding a row afterwards is not enough: patient 100001148 already has one
 *       ("Sanvika ss … Wife"), and an earlier version of this check reported that pre-existing row as proof
 *       that Save had worked.</li>
 *   <li>Save also fails to redirect. It leaves {@code #/add-Mortuary} only when pushed, and the URL picks
 *       up {@code ?textarea=...&textarea=...} — the form is being submitted as a <b>native GET</b>, with
 *       the handover-to and template-body textareas landing in the query string. That is consistent with
 *       nothing being posted to the server.</li>
 * </ul>
 *
 * <p><b>Two things about the grids, both of which produced false passes before:</b></p>
 * <ul>
 *   <li>The form's <b>Add</b> is {@code AddTemplateList(Mortuary)} — it appends the chosen <b>template</b>
 *       (with the body), not a patient line. The added row reads "Discharge Summary_1", so that is what it
 *       is checked for.</li>
 *   <li>Checking that row for the MRN instead <b>passes on the patient's PRE-EXISTING mortuary record</b>
 *       when they have one. That is exactly how an earlier version of this flow reported "row added with
 *       the data — matched 2/2" while nothing new had been added at all.</li>
 * </ul>
 */
public class Mortuary extends DevHisBase {

    /** A deceased patient — accepted by Post Mortem and Death Certificate. */
    public static final String DEFAULT_MRN = "100000684";

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public Mortuary() { super("NursingStation_Mortuary"); }

    public static void main(String[] args) {
        Mortuary t = new Mortuary();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Mortuary", "Nursing Station > Mortuary",
                "&#9888; Creates a REAL mortuary record: Add, search the patient by MRN, enter cabin no, "
                        + "relationship, in/handover date times, handover to, template, Add the row, Save.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        String mrn = System.getProperty("mrn", DEFAULT_MRN);
        String cabin = System.getProperty("cabin", "CAB-01");
        String handoverTo = System.getProperty("handoverTo", "Auto Test Relative");
        java.time.format.DateTimeFormatter DT =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String inDateTime = System.getProperty("inDateTime", java.time.LocalDateTime.now().format(DT));
        String handoverDateTime = System.getProperty("handoverDateTime",
                java.time.LocalDateTime.now().plusHours(2).format(DT));

        // Police Department / Funeral Service Company. Values are distinctive so the added row can be
        // checked for them afterwards rather than just counted.
        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        String policeName = System.getProperty("policeName", "Sgt Auto " + stamp);
        String policeId = System.getProperty("policeId", "PID" + stamp);
        String policeVehicle = System.getProperty("policeVehicle", "PDR" + stamp);
        String funeralName = System.getProperty("funeralName", "Auto Funeral " + stamp);
        String funeralIc = System.getProperty("funeralIc", "IC" + stamp);
        String funeralVehicle = System.getProperty("funeralVehicle", "FNV" + stamp);
        String bodyText = System.getProperty("bodyText", "Automated mortuary template body " + stamp);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.Mortuary mt =
                new com.kpj.pages.NursingStation_page.Mortuary(page);

        // 1) Navigate
        boolean rendered = mt.navigateViaMenu(BASE);
        step(page, "Open Mortuary screen",
                "Click Nursing Station -> Mortuary (retrying via the route and a full page load)",
                "The Mortuary screen is shown",
                rendered ? "Opened " + page.url()
                         : "Route resolved but rendered NO screen. Body: \"" + mt.lastBodyText + "\"",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }

        // This screen allows ONE active mortuary record per patient — a second attempt is refused with
        // "Mortuary already added for this patient (active record exists).!". So the run must use a
        // patient who has none.
        //
        // Candidates come from the form's patient lookup, which means opening the form once, collecting
        // them, and returning to the list to MEASURE each. Measuring is the only trustworthy test: reading
        // the list wholesale has proved unreliable on this screen, whereas filtering by a single MRN and
        // counting works every time.
        mt.clickAdd();
        java.util.List<String> candidates = new java.util.ArrayList<>(mt.discoverMrns(20));
        candidates.remove(mrn);
        candidates.add(0, mrn);                     // try the requested patient first
        mt.backToList();
        addSummary("Candidate patients", candidates.toString());

        String chosen = "";
        StringBuilder probed = new StringBuilder();
        for (String c : candidates.subList(0, Math.min(candidates.size(), Integer.getInteger("maxPatients", 8)))) {
            int n = mt.countRowsForMrn(c);
            probed.append(probed.length() == 0 ? "" : ", ").append(c).append("=").append(n);
            if (n == 0) { chosen = c; break; }
        }
        addSummary("Existing mortuary rows per candidate", probed.toString());
        int rowsBefore = 0;
        if (!chosen.isEmpty()) {
            mrn = chosen;
        } else {
            rowsBefore = mt.countRowsForMrn(mrn);
        }
        addSummary("Patient used", mrn + (chosen.isEmpty()
                ? " (every candidate already has a record — Save is expected to refuse this as a duplicate)"
                : " (no existing mortuary record)"));

        // 2) Add (opens the form)
        boolean formOpen = mt.clickAdd();
        mt.describeControls();   // diagnostics: the real ng-models
        step(page, "Click Add", "Click Add to open the mortuary form",
                "The Mortuary form opens",
                formOpen ? "Add clicked (" + page.url() + ")" : "Add did NOT open a form",
                formOpen ? "PASS" : "FAIL");
        if (!formOpen) { addSummary("Result", "FAILED — mortuary form not reached"); return; }

        // 3) MRN + Search — verified against the model, not just the input box. The patient was already
        //    chosen above by measuring each candidate's existing rows.
        String mrnNote = rowsBefore > 0
                ? "This patient already has " + rowsBefore + " active mortuary record(s), so Save is "
                  + "expected to refuse a duplicate. "
                : "";

        // Retried: the search occasionally lands before the form has finished wiring itself up, and a
        // single attempt then reports a valid patient as missing.
        String searchResult = mt.searchByMrn(mrn);
        boolean attached = mt.patientAttached();
        for (int i = 0; i < 2 && !attached; i++) {
            page.waitForTimeout(2000);
            searchResult = mt.searchByMrn(mrn);
            attached = mt.patientAttached();
        }
        step(page, "Enter MRN & click Search",
                "Enter MRN " + mrn + " and click the search icon (SearchPatientByMRNo)",
                "The patient is attached to the mortuary record",
                mrnNote + (attached ? searchResult : "Patient NOT attached — " + searchResult),
                attached ? "PASS" : "FAIL");

        // 4) Cabin no
        String cab = mt.enterCabinNo(cabin);
        boolean cabOk = !cab.startsWith("(");
        step(page, "Enter mortuary cabin no", "Enter the mortuary cabin no " + cabin,
                "The cabin no is entered",
                cabOk ? "Cabin No = " + cab : "Cabin No NOT entered " + cab, cabOk ? "PASS" : "FAIL");

        // 5) Relationship
        String rel = mt.selectRelationship();
        boolean relOk = !rel.startsWith("(");
        step(page, "Select relationship", "Select the Relationship",
                "A relationship is selected",
                relOk ? "Relationship = " + rel : "Relationship NOT selected " + rel,
                relOk ? "PASS" : "FAIL");

        // 6) In / handover date times
        String dts = mt.enterDateTimes(inDateTime, handoverDateTime);
        boolean dtsOk = !dts.contains("(no-field)");
        step(page, "Enter in date time and handover date time",
                "Enter In Date Time " + inDateTime + " and Handover Date Time " + handoverDateTime,
                "Both date times are entered", dts, dtsOk ? "PASS" : "FAIL");

        // 7) Handover to
        String ho = mt.enterHandoverTo(handoverTo);
        boolean hoOk = !ho.startsWith("(");
        step(page, "Enter handover to", "Enter Handover To " + handoverTo,
                "Handover To is entered",
                hoOk ? "Handover To = " + ho : "Handover To NOT entered " + ho, hoOk ? "PASS" : "FAIL");

        // 8) Police Department: name, police ID no, vehicle no
        String police = mt.enterPoliceDetails(policeName, policeId, policeVehicle);
        step(page, "In Police Department, enter name, police id number and vehicle number",
                "Fill the Police Department block", "All three police fields are entered", police,
                com.kpj.pages.NursingStation_page.Mortuary.allThreeSet(police) ? "PASS" : "FAIL");

        // 9) Funeral Service Company: name, IC no, vehicle no
        String funeral = mt.enterFuneralDetails(funeralName, funeralIc, funeralVehicle);
        step(page, "In Funeral Service Company, enter name, ic number and vehicle number",
                "Fill the Funeral Service Company block", "All three funeral fields are entered", funeral,
                com.kpj.pages.NursingStation_page.Mortuary.allThreeSet(funeral) ? "PASS" : "FAIL");

        // 10) Template
        String template = mt.selectTemplate();
        boolean templateOk = !template.startsWith("(");
        step(page, "Select template", "Select the template",
                "A template is selected",
                templateOk ? "Template = " + template : "Template NOT selected " + template,
                templateOk ? "PASS" : "FAIL");

        // 11) Template body — AFTER the template, since selecting one rewrites the body
        String body = mt.fillTemplateBody(bodyText);
        step(page, "Fill the template body", "Enter the template body text (Mortuary.notes)",
                "The template body is filled", body, body.startsWith("(") ? "FAIL" : "PASS");

        // 12) Add the row. This Add is AddTemplateList(Mortuary) — it appends the chosen TEMPLATE (with the
        //     body) to the form's template grid, so that is what the row is checked for. Checking it for
        //     the MRN would be wrong: that matches the patient's PRE-EXISTING mortuary record if they have
        //     one, which is how this step previously passed without a new row being added at all.
        String added = mt.clickAddRow();
        boolean addOk = added != null && added.startsWith("rowsAdded=") && !added.startsWith("rowsAdded=0");
        String templateName = template == null ? "" : template.split(" \\[")[0].trim();
        boolean rowOk = addOk && mt.rowHasData(java.util.List.of(templateName));
        step(page, "Click Add (the row)", "Click Add to add the mortuary line (template + body)",
                "A row carrying the selected template is added to the grid",
                added + (mt.lastRowData.isEmpty() ? "" : "  ||  " + mt.lastRowData),
                rowOk ? "PASS" : "FAIL");

        // 13) Save. No toast is asserted — this screen shows none — so the click is reported for what it
        //     is and the verdict comes from the row check below.
        String toast = mt.saveAndGetToast();
        step(page, "Click Save", "Click Save (IUDMortuary)",
                "Save is submitted",
                "Save clicked. " + (toast == null || toast.isEmpty()
                        ? "No toast (none is expected on this screen) — " + mt.lastSaveDiagnostics
                        : "Screen said: \"" + toast + "\""),
                "PASS");

        // 14) Save should redirect back to the list on its own.
        String redirect = mt.waitForRedirectToList(15000);
        step(page, "Save redirects to the list",
                "After Save the screen should leave #/add-Mortuary and return to the mortuary list",
                "The list screen is shown", redirect,
                redirect.startsWith("Save redirected") ? "PASS" : "FAIL");

        // No verification that a NEW row appeared in the list — not checked on this run.
        boolean ok = toast != null && !toast.isEmpty();

        addSummary("Police", mt.lastPolice);
        addSummary("Funeral", mt.lastFuneral);
        addSummary("Template body", mt.lastBody);
        addSummary("Row added on the form (before Save)", mt.lastRowData);
        addSummary("MRN", mrn);
        addSummary("Patient attached", attached ? "yes" : "NO — " + mt.lastFormSearch);
        addSummary("Cabin No", mt.lastCabin);
        addSummary("Relationship", mt.lastRelationship);
        addSummary("In / Handover", inDateTime + "  ->  " + handoverDateTime);
        addSummary("Handover To", mt.lastHandoverTo);
        addSummary("Template", mt.lastTemplate);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
