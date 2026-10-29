package com.kpj.tests.AncillaryServices_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named PatientRequisition — referenced by its fully-qualified name.

/**
 * Ancillary Services &gt; Blood Bank &gt; <b>Patient Requisition</b>.
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists).</li>
 *   <li>Open <b>Ancillary Services</b> → <b>Blood Bank</b> → <b>Patient Requisition</b>.</li>
 *   <li>Click <b>New</b>.</li>
 *   <li>Select the patient with the <b>patient search</b> (popup behind the search icons beside the MRN box).</li>
 *   <li>Enter the <b>Component</b>, <b>Required Date</b> and <b>Qty</b>.</li>
 *   <li>Click the form's <b>Add</b> — the line moves into the table on the form.</li>
 *   <li>Click <b>Save</b> → verify the success toast.</li>
 *   <li>Verify the saved requisition in the list table.</li>
 * </ol>
 *
 * <p>The Required Date is tomorrow (the list is filtered by REQUIRED date, so it is looked up with the To date moved on). The row
 * count and highest requisition number are noted BEFORE the form is opened, so the new row can be told from older ones.</p>
 *
 * <p>&#9888; A successful run CREATES a REAL blood component requisition for a real patient record in the target environment.</p>
 */
public class PatientRequisition extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    /** Digits typed into the patient search popup, in turn, until a patient can be picked. Override with {@code -Dfragments=11,33}. */
    private static final String[] FRAGMENTS = {"11", "33", "12", "13", "14", "15", "16", "17", "18", "19"};

    public PatientRequisition() { super("Ancillary_BloodBank_PatientRequisition"); }

    public static void main(String[] args) {
        PatientRequisition t = new PatientRequisition();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Ancillary Services - Blood Bank - Patient Requisition", "Ancillary Services > Blood Bank > Patient Requisition",
                "&#9888; Creates a REAL requisition: New, pick the patient with the patient search, enter Component, Required Date and Qty, Add, Save, check the toast and the list table.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.AncillaryServices_page.BloodBank_page.PatientRequisition pr =
                new com.kpj.pages.AncillaryServices_page.BloodBank_page.PatientRequisition(page);

        // 1) Navigate
        boolean rendered = pr.navigateViaMenu();
        if (!rendered) addSummary("Menu offered", pr.lastMenu);
        step(page, "Open Patient Requisition screen",
                "Click Ancillary Services -> Blood Bank -> Patient Requisition",
                "The Patient Requisition list screen is shown",
                rendered ? "Opened " + page.url()
                           + (pr.lastRoute.isEmpty() ? "" : " (menu route " + pr.lastRoute + ")")
                         : "Did NOT reach the screen (" + page.url() + ") — see the menu dump",
                rendered ? "PASS" : "FAIL");
        if (!rendered) { addSummary("Result", "FAILED — screen not reached"); return; }
        addSummary("List screen controls", pr.describeControls());

        // The requisition is for tomorrow; note how the list looks for today..tomorrow BEFORE anything is saved.
        String requiredDate = java.time.LocalDate.now().plusDays(1).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        addSummary("List before the save", pr.baseline(requiredDate));

        // 2) New
        boolean opened = pr.clickNew();
        step(page, "Click New", "Click New", "The Patient Requisition entry form opens",
                opened ? pr.lastNew : "FAILS — " + pr.lastNew, opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED — the form did not open"); return; }
        addSummary("Form controls", pr.describeControls());

        // 3) Patient, by the patient search
        java.util.List<String> fragments = new java.util.ArrayList<>();
        String pinned = System.getProperty("fragments", "");
        if (!pinned.isBlank()) for (String f : pinned.split(",")) fragments.add(f.trim());
        else fragments.addAll(java.util.Arrays.asList(FRAGMENTS));
        String patient = pr.selectPatientFromSearch(fragments);
        boolean picked = !patient.isEmpty();
        step(page, "Select the patient (patient search)",
                "Click the patient search icon beside the MRN box, type digits of an MRN, click Search, click the select icon of a row",
                "The chosen patient's MRN, name and details fill the form",
                picked ? "PASSES because the patient attached to the form: " + patient + "  [" + pr.lastLookup + "]"
                       : "FAILS — " + pr.lastLookup,
                picked ? "PASS" : "FAIL");
        if (!picked) { addSummary("Result", "FAILED — no patient could be selected"); return; }

        // 4) Component, Required Date, Qty
        String entry = pr.fillRequisition(0, requiredDate);
        boolean entered = pr.detailsEntered();
        entry = pr.lastEntry;                           // carries the read-back when a box did not hold its value
        stepShot(shot(), "Enter component, required date and qty",
                "Choose the Component, type the Required Date " + requiredDate + " and the Qty",
                "Each box holds its value",
                entered ? "PASSES because each box accepted its own value and read it back: " + entry
                        : "FAILS because a value did not land in its own field: " + entry,
                entered ? "PASS" : "FAIL");
        if (!entered) { addSummary("Result", "FAILED — the requisition line could not be entered"); return; }

        // 5) Add
        boolean staged = pr.addToList();
        stepShot(shot(), "Click Add", "Click the form's Add",
                "The line appears in the table above the entry boxes with the entered values, and the boxes clear",
                staged ? "PASSES because the line is in the form's table with the values entered: " + pr.lastStaged
                       : "FAILS — " + pr.lastStaged,
                staged ? "PASS" : "FAIL");
        if (!staged) { addSummary("Result", "FAILED — the line was not added to the form's table"); return; }

        // 6) Save -> toast. The screenshot is taken at once: the screen returns to the list and the toast fades.
        String toast = pr.saveAndGetToast();
        byte[] toastShot = shot();
        boolean ok = com.kpj.pages.AncillaryServices_page.BloodBank_page.PatientRequisition.isSuccess(toast);
        // A message containing "undefined" FAILS the step even though the save goes through: the record identifier was never
        // substituted into the template, so the user is told "undefined" was saved.
        boolean malformed = toast != null && toast.toLowerCase().contains("undefined");
        String actual = toast == null || toast.isEmpty()
                ? "No message appeared — " + pr.lastSaveDiagnostics
                : (ok ? toast + "  (" + pr.lastSaveDiagnostics + ")"
                      : "Save not confirmed — the screen said: \"" + toast + "\"  (" + pr.lastSaveDiagnostics + ")");
        stepShot(toastShot, "Click Save & success toast", "Click Save; wait for the success toast",
                "'... saved successfully' toast",
                (ok ? "PASSES because the screen answered with a success message: " : "FAILS — ") + actual
                    + (malformed
                        ? "  ||  THE MESSAGE IS MALFORMED: it reads \"" + toast + "\", so the screen cannot name what it saved."
                        : ""),
                (ok && !malformed) ? "PASS" : "FAIL");

        // 7) The table — checked even when the toast was not a success, so a silent save is reported as such rather than hidden.
        boolean inList = pr.verifyInTable();
        addSummary("List check", pr.lastListCheck);
        step(page, "Check the saved requisition in the table",
                "Open the list; move the Required To Date to " + requiredDate + "; Search; find the new Patient Req. No. for MRN " + pr.lastMrn,
                "A new row carries the patient's MRN, the Component, Qty and Required Date entered",
                (inList
                    ? "PASSES because the saved requisition was found in the table with the values entered: " + pr.lastListCheck
                    : "FAILS because the saved requisition could not be found in the table exactly once. " + pr.lastListCheck
                        + (ok ? "" : "  (the Save was not confirmed either)")),
                inList ? "PASS" : "FAIL");

        addSummary("Patient", pr.lastPatient);
        addSummary("Component / Required Date / Qty", pr.lastComponent + " / " + pr.lastRequiredDate + " / " + pr.lastQty);
        addSummary("Remark", pr.lastRemark);
        addSummary("Patient Req. No.", pr.lastNo);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }

    private byte[] shot() {
        try { return page.screenshot(); } catch (Exception e) { return null; }
    }

    /** Records a step with the given screenshot, or with a fresh one of the page when none could be taken. */
    private void stepShot(byte[] png, String name, String desc, String expected, String actual, String status) {
        if (png != null && png.length > 0) step(png, name, desc, expected, actual, status);
        else step(page, name, desc, expected, actual, status);
    }
}
