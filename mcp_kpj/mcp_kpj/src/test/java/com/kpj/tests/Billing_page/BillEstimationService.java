package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;
import java.util.Random;

// The page object is also named BillEstimationService - referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Bill Estimation-Service</b>. The steps are the ones a person performed in the built-in browser and that were recorded:
 *
 * <ol>
 *   <li>Sidebar: Billing &gt; Bill Estimation-Service; click <b>Add</b>.</li>
 *   <li>Patient Search popup: Visit Date ticked, From two months back (day 6), Search, pick a patient.</li>
 *   <li>Type the <b>Last Name*</b> (the popup does not fill it). Bill Template "Standard Consultation Package" + <b>Add Bill Template</b>.</li>
 *   <li><b>Submit</b>; verify the toast "Bill Estimation Saved Successfully." and the new row in the list.</li>
 * </ol>
 *
 * <p>The last name is generated text (a different one on every run), not the recorded one. Pin the template with {@code -Dbe.template=}.</p>
 *
 * <p>&#9888; SAVES A REAL BILL ESTIMATION on every run (it appears in the list; there is no delete on this screen).</p>
 */
public class BillEstimationService extends DevHisBase {

    private static final String TEMPLATE = System.getProperty("be.template", "Standard Consultation Package").trim();
    private static final String[] LAST_NAMES = {"Rahman", "Lim", "Kumar", "Wong", "Abdullah", "Singh", "Chong", "Ismail", "Nair", "Teoh", "Hashim", "Pillai", "Yusof", "Chua", "Ahmad", "Devi"};

    public BillEstimationService() { super("Billing_BillEstimationService"); }

    public static void main(String[] args) {
        BillEstimationService t = new BillEstimationService();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static double num(String s) {
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (Exception e) { return Double.NaN; }
    }

    @Override
    protected void body() {
        meta("Billing - Bill Estimation-Service", "Billing > Bill Estimation-Service",
                "&#9888; Saves a REAL bill estimation: Add; pick a patient in Patient Search; last name; Bill Template + Add Bill Template; Submit; verify the toast and the new list row.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.BillEstimationService be = new com.kpj.pages.Billing_page.BillEstimationService(page);

        boolean on = be.navigateViaMenu();
        step(page, "Open Bill Estimation-Service", "Click Billing -> Bill Estimation-Service", "The Bill Estimation list is shown",
                on ? "Opened " + page.url() : "Did NOT reach the list (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Bill Estimation-Service not reached"); return; }

        List<String[]> before = be.searchList();

        boolean form = be.clickAdd();
        step(page, "Click Add", "Click Add (AddEstimationMaster())", "The Bill Estimation form (#/add-BillEstimation) opens",
                form ? "Form opened (" + page.url().replaceAll(".*#", "#") + "); the list held " + before.size() + " estimation(s) for today" : "The form did NOT open (" + page.url() + ")", form ? "PASS" : "FAIL");
        if (!form) { addSummary("Result", "FAILED - form not opened"); return; }

        be.problems.clear();
        boolean popup = be.openPatientSearch();
        step(page, "Open Patient Search", "Click the search button beside MRN (openPopupScreen();)", "The Patient Search popup opens",
                popup ? "Patient Search popup opened" : "Problem - " + be.problems, popup ? "PASS" : "FAIL");
        if (!popup) { addSummary("Result", "FAILED - popup not opened"); return; }

        be.problems.clear();
        boolean visit = be.ensureVisitDateTicked();
        String from = be.pickVisitFrom(2, 6);
        step(page, "Set the Visit Date filter", "Tick Visit Date; From = two months back, day 6", "Visit Date is ticked and From is the 6th, two months back",
                visit && from.startsWith("06/") && be.problems.isEmpty() ? "Visit Date ticked; From " + from : "Problem - ticked=" + visit + ", From=" + from + " " + be.problems, visit && from.startsWith("06/") ? "PASS" : "FAIL");

        be.problems.clear();
        int found = be.popupSearch();
        List<String> pats = be.popupRows();
        step(page, "Search patients", "Click Search (SearchPatient(0);)", "Patients are listed",
                found > 0 ? found + " patient(s) listed, first: " + pats.get(0) : "No patient listed" + (be.problems.isEmpty() ? "" : " - " + be.problems), found > 0 ? "PASS" : "FAIL");
        if (found == 0) { addSummary("Result", "FAILED - no patients"); return; }

        // the first registered patient (an MRN made of digits; emergency 'ED...' numbers are skipped)
        int idx = 0;
        for (int i = 0; i < pats.size(); i++) { if (pats.get(i).matches("^\\d{6,}.*")) { idx = i; break; } }
        be.problems.clear();
        String mrn = be.pickPatient(idx);
        step(page, "Pick a patient", "Click the select button of the patient (SetSearchPatient(PR);): " + pats.get(idx), "The patient's details load into the form",
                mrn.isEmpty() ? "Problem - the form did not fill " + be.problems : "MRN " + mrn + ", Name " + be.firstName() + ", Last Name \"" + be.lastName() + "\"", mrn.isEmpty() ? "FAIL" : "PASS");
        if (mrn.isEmpty()) { addSummary("Result", "FAILED - patient not picked"); return; }
        final String patientName = be.firstName();   // the form empties itself after Submit

        String last = LAST_NAMES[new Random().nextInt(LAST_NAMES.length)];
        be.problems.clear();
        String typed = be.fillBox("BillEstimation.lastName", last);
        step(page, "Enter the Last Name", "Type \"" + last + "\" in Last Name* (generated; the recording typed another)", "The last name is accepted",
                typed.isEmpty() ? "Problem - " + be.problems : "Last Name = " + typed, typed.equals(last) ? "PASS" : "FAIL");

        be.problems.clear();
        String tplMsg = be.pickTemplate(TEMPLATE);
        step(page, "Select the Bill Template", "Choose \"" + TEMPLATE + "\" in Bill Template", "Bill Template = " + TEMPLATE,
                tplMsg.isEmpty() ? "Bill Template = " + TEMPLATE : "Problem - " + tplMsg, tplMsg.isEmpty() ? "PASS" : "FAIL");

        be.problems.clear();
        String totalBefore = be.totalBillAmount();
        String totalAfter = be.clickAddTemplate();
        boolean added = be.problems.isEmpty() && num(totalAfter) > 0;
        step(page, "Click Add Bill Template", "Click Add Bill Template (GetBillTemplateServices())", "The template's services are added (Total Bill Amount > 0)",
                added ? "Total Bill Amount " + (totalBefore.isEmpty() ? "(blank)" : totalBefore) + " -> " + totalAfter : "Problem - Total Bill Amount " + totalAfter + " " + be.problems, added ? "PASS" : "FAIL");

        be.problems.clear();
        String toast = be.clickSubmit();
        boolean saved = toast.toLowerCase().contains("saved successfully");
        step(page, "Click Submit", "Click Submit (IUDBillEstimation())", "'Bill Estimation Saved Successfully.' toast",
                toast.isEmpty() ? "No toast appeared" + (be.problems.isEmpty() ? "" : " - " + be.problems) : toast, saved ? "PASS" : "FAIL");
        addSummary("Patient", "MRN " + mrn + ", " + patientName + " " + last);
        addSummary("Bill Template / total", TEMPLATE + " / " + totalAfter);
        addSummary("Submit toast", toast.isEmpty() ? "(none)" : toast);
        if (!saved) { addSummary("Result", "FAILED - not saved"); return; }

        // back to the list (never a typed hash) and look for the new estimation
        page.waitForTimeout(2000);
        boolean list = be.onList() || be.clickBack();
        List<String[]> after = list ? be.searchList() : new java.util.ArrayList<>();
        String[] mine = null;
        for (String[] r : after) { if (r[0].equals(mrn)) mine = r; }
        step(page, "Verify the new estimation in the list", "Back to the list; Search (today); look for MRN " + mrn,
                "The new estimation is listed",
                !list ? "The list did not come back (" + page.url() + ")"
                      : mine == null ? "No row for MRN " + mrn + " (the list held " + before.size() + ", now " + after.size() + ")"
                      : "Listed: MRN " + mine[0] + ", " + mine[1] + ", " + mine[2] + ", " + mine[3] + ", total " + mine[5] + " (the list held " + before.size() + ", now " + after.size() + ")",
                mine != null && after.size() > before.size() ? "PASS" : "FAIL");
        addSummary("List", before.size() + " -> " + after.size());
        addSummary("Result", mine != null ? "Saved and listed" : "Saved but not found in the list");
    }
}
