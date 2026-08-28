package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named OpdCharges — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>OPD Charges</b>.
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>OPD Charges</b>.</li>
 *   <li>Pick an OP patient (patient-picker popup, defaults to today's OPD visits) — OP Visit No / Payor
 *       auto-select and any existing unbilled charge auto-loads.</li>
 *   <li>Set the Cost Centre on every charge line.</li>
 *   <li>Add a procedure (Open Procedure Popup → pick a Procedure Name → assign a Doctor to every service line
 *       it expands into → OK).</li>
 *   <li>Fill Cash Counter / Validated By only if not already defaulted.</li>
 *   <li>Save ({@code IUDBill}) → confirm "Have you verified that Cost Centers are defined…" with
 *       <b>Validated</b>.</li>
 * </ol>
 */
public class OpdCharges extends DevHisBase {

    public OpdCharges() { super("Billing_OpdCharges"); }

    public static void main(String[] args) {
        OpdCharges t = new OpdCharges();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - OPD Charges", "Billing > OPD Charges",
                "Open OPD Charges, pick an OP patient, set Cost Centre, add a procedure with doctors, Save + Validated.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.OpdCharges opc =
                new com.kpj.pages.Billing_page.OpdCharges(page);

        boolean onScreen = opc.navigateTo(BASE);
        step(page, "Open OPD Charges screen", "Billing -> OPD Charges",
                "The OPD Charges screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        String patient = opc.selectOpdPatient();
        step(page, "Enter MRN / pick OP patient and search", "Find Patient -> filter OPD (today's visits) -> Search -> pick a row",
                "An OP visit is loaded (OP Visit No / Payor auto-select)",
                patient.isEmpty() ? "No OP patient with a visit could be loaded" : "Loaded " + patient,
                patient.isEmpty() ? "FAIL" : "PASS");
        if (patient.isEmpty()) { addSummary("Result", "No OP patient available"); return; }

        java.util.List<String> before = opc.readChargeGridServiceNames();
        String procedure = opc.addProcedure("gen");
        java.util.List<String> newLines = com.kpj.pages.Billing_page.OpdCharges.diff(before, opc.readChargeGridServiceNames());
        step(page, "Open Procedure Popup, select Procedure Name and Doctor(s), OK",
                "openProcedurePopup() -> pick a procedure -> assign a Doctor to every expanded service line -> OK",
                "A procedure is added with a Doctor on each service line",
                procedure.isEmpty() ? "No procedure could be added" : "Added " + procedure + " -> new charge line(s): " + newLines,
                procedure.isEmpty() ? "FAIL" : "PASS");

        int ccFixed = opc.ensureAllCostCentresSet();
        step(page, "Select Cost Center for every charge line", "Every charge grid row needs a Cost Centre before Save",
                "All charge lines have a Cost Centre", "Cost Centre set on " + ccFixed + " row(s) that were still \"--Select--\"", "PASS");

        String fill = opc.fillMandatory();
        step(page, "Cash Counter, Total Amount, Validated By, Discount & Tax Details",
                "Fill Cash Counter / Validated By only if not already defaulted (Total Amount / Discount & Tax are computed)",
                "The mandatory bill fields are filled", fill, fill.contains("(n/a)") ? "FAIL" : "PASS");

        boolean saveResponded = opc.saveAndValidate();
        // Save can return HTTP 200 with no error toast and still silently drop the new lines server-side (a real
        // app defect reproduced live 2026-08-24, surfaced as an unhelpful "KPJ Portal / Error!" toastr on some
        // attempts and nothing at all on others for the identical steps) — so the grid state after a reload is the
        // only trustworthy signal, not the response status or the toast.
        java.util.List<String> missing = opc.missingAfterReload(opc.lastPatient, newLines);
        boolean saved = saveResponded && missing.isEmpty();
        StringBuilder actual = new StringBuilder();
        actual.append("IUDBillSaveOPD responded ").append(saveResponded ? "200, no error toast" : "with a problem");
        if (!opc.lastToast.isEmpty()) actual.append(" | toast: \"").append(opc.lastToast).append("\"");
        if (!opc.blockingMessage.isEmpty()) actual.append(" | blocked: \"").append(opc.blockingMessage).append("\"");
        actual.append(" | after reload + re-search, missing charge line(s): ").append(missing.isEmpty() ? "none (persisted)" : missing);
        step(page, "Click Save, then Validated", "Save (IUDBill) -> confirm dialog -> click Validated -> verify by reload",
                "The OPD charge bill is posted successfully AND the new charge lines survive a reload", actual.toString(), saved ? "PASS" : "FAIL");

        addSummary("Patient", opc.lastPatient + " / " + opc.lastVisit);
        addSummary("Procedure", opc.lastProcedure);
        addSummary("Result", saved ? "Bill saved and verified persisted" : "NOT reliably saved — see step 7");
    }
}
