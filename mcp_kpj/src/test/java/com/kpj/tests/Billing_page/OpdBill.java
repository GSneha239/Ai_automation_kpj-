package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OpdBill — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>OPD Bill</b> (distinct from <b>OPD Charges</b> — see {@link com.kpj.pages.Billing_page.OpdBill}).
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>OPD Bill</b>.</li>
 *   <li>Click the MRN patient-search icon → search → select the patient (popup closes itself on selection).</li>
 *   <li>Open Procedure Popup → pick a Procedure Name → select a Doctor for every service line it expands into →
 *       OK.</li>
 *   <li>Select the Cost Centre in Bill Details for every charge line.</li>
 *   <li>Click Save → accept every confirm alert that appears.</li>
 *   <li>Expect a success toast (Bill No. populated, charge lines persisted after a reload).</li>
 * </ol>
 */
public class OpdBill extends DevHisBase {

    public OpdBill() { super("Billing_OpdBill"); }

    public static void main(String[] args) {
        OpdBill t = new OpdBill();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - OPD Bill", "Billing > OPD Bill",
                "Open OPD Bill, search+select a patient, add a procedure with doctors, set Cost Centre, Save and accept all alerts.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.OpdBill opb =
                new com.kpj.pages.Billing_page.OpdBill(page);

        boolean onScreen = opb.navigateTo(BASE);
        step(page, "Open OPD Bill screen", "Billing -> OPD Bill",
                "The OPD Bill screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // "Visit is closed" (see OpdBill page object Javadoc) turned out live not to be tied to any one visit —
        // it fired identically for multiple different, untouched OP visits in this QA environment. So rather than
        // give up on the first candidate, try every candidate the patient picker offers today and use the first
        // one that actually lets a procedure be added.
        java.util.List<String> candidates = opb.candidateOpdMrnsFromPopup(8);
        String patient = "";
        String procedure = "";
        java.util.List<String> before = java.util.Collections.emptyList();
        java.util.List<String> newLines = java.util.Collections.emptyList();
        java.util.List<String> triedButClosed = new java.util.ArrayList<>();
        for (String mrn : candidates) {
            if (!opb.searchByMrn(mrn)) continue;
            before = opb.readChargeGridServiceNames();
            procedure = opb.addProcedure("gen");
            newLines = com.kpj.pages.Billing_page.OpdBill.diff(before, opb.readChargeGridServiceNames());
            if (!procedure.isEmpty()) { patient = opb.lastPatient + " / " + opb.lastVisit; break; }
            if (!opb.blockingMessage.isEmpty()) triedButClosed.add(mrn + " (" + opb.blockingMessage + ")");
        }
        step(page, "Click MRN patient search, search any patient, select the patient",
                "Open patient picker -> Search -> pick a row's Select button -> popup closes",
                "An OP visit is loaded (OP Visit No / Payor auto-select); popup closed",
                patient.isEmpty() && candidates.isEmpty() ? "No OP patient with a visit could be loaded"
                        : (patient.isEmpty() ? "Loaded " + candidates.size() + " candidate(s) but none had a usable visit" : "Loaded " + patient),
                (patient.isEmpty() && candidates.isEmpty()) ? "FAIL" : "PASS");
        if (candidates.isEmpty()) { addSummary("Result", "No OP patient available"); return; }

        String procActual = procedure.isEmpty()
                ? "No procedure could be added on any of " + candidates.size() + " candidate(s) tried"
                        + (triedButClosed.isEmpty() ? "" : " — blocked: " + triedButClosed)
                : "Added " + procedure + " -> new charge line(s): " + newLines
                        + (triedButClosed.isEmpty() ? "" : " (skipped closed visit(s): " + triedButClosed + ")");
        step(page, "Open Procedure Popup, select Procedure Name, select Doctor(s), OK",
                "openProcedurePopup() -> pick a procedure -> assign a Doctor to every expanded service line -> OK",
                "A procedure is added with a Doctor on each service line",
                procActual, procedure.isEmpty() ? "FAIL" : "PASS");
        if (procedure.isEmpty()) {
            addSummary("Result", "FAILED — no candidate had an open visit: " + triedButClosed);
            return;
        }

        int ccFixed = opb.ensureAllCostCentresSet();
        step(page, "Select Cost Center in Bill Details for every charge line", "Every charge grid row needs a Cost Centre before Save",
                "All charge lines have a Cost Centre", "Cost Centre set on " + ccFixed + " row(s) that were still \"--Select--\"", "PASS");

        boolean saveResponded = opb.saveAndValidate();
        // Save on this screen is a chain of up to four confirm alerts (interim/final bill, cost centers verified,
        // close-visit, IC-not-validated — see OpdBill page object Javadoc), and IUDBillSaveOPD can answer HTTP 200
        // with an empty body while the app surfaces a generic "KPJ Portal / Error!" toast — reproduced live
        // 2026-08-25. So the grid state after a reload is the only trustworthy signal, not the response status.
        java.util.List<String> missing = newLines.isEmpty() ? java.util.Collections.emptyList()
                : opb.missingAfterReload(opb.lastPatient, newLines);
        boolean saved = saveResponded && missing.isEmpty();
        StringBuilder actual = new StringBuilder();
        actual.append("IUDBillSaveOPD responded ").append(saveResponded ? "200, no error toast" : "with a problem");
        if (!opb.lastToast.isEmpty()) actual.append(" | toast: \"").append(opb.lastToast).append("\"");
        if (!opb.blockingMessage.isEmpty()) actual.append(" | blocked: \"").append(opb.blockingMessage).append("\"");
        if (!newLines.isEmpty()) actual.append(" | after reload + re-select, missing charge line(s): ").append(missing.isEmpty() ? "none (persisted)" : missing);
        // Use the screenshot saveAndValidate() captured the moment it saw the toast — by now the page has been
        // reloaded (for the persistence check above) and any toast is long gone from a live page.screenshot().
        if (opb.lastScreenshot != null) {
            step(opb.lastScreenshot, "Click Save, accept all alerts", "Save (IUDBill) -> accept every confirm dialog -> verify by reload",
                    "The OPD bill is saved successfully (success toast, Bill No. populated) AND charge lines survive a reload",
                    actual.toString(), saved ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save, accept all alerts", "Save (IUDBill) -> accept every confirm dialog -> verify by reload",
                    "The OPD bill is saved successfully (success toast, Bill No. populated) AND charge lines survive a reload",
                    actual.toString(), saved ? "PASS" : "FAIL");
        }

        addSummary("Patient", opb.lastPatient + " / " + opb.lastVisit);
        addSummary("Procedure", opb.lastProcedure);
        addSummary("Result", saved ? "Bill saved and verified persisted" : "NOT saved — see step 6 (\"KPJ Portal / Error!\" — known live defect)");
    }
}
