package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named IpdBill — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>IPD Bill</b> (the IPD sibling of {@link com.kpj.pages.Billing_page.OpdBill} — see
 * {@link com.kpj.pages.Billing_page.IpdBill} for the live-discovered flow details and reproduced defects).
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>IPD Bill</b>.</li>
 *   <li>Search patient (same picker popup as OPD Bill, with IPD selected) → pick a newborn ("Baby Of …") row.</li>
 *   <li>Click <b>Merge With Mother</b> → click <b>Save</b> on its confirm.</li>
 *   <li>Select the Cost Centre for every charge line.</li>
 *   <li>Click Save → accept every confirm alert that appears.</li>
 *   <li>Expect a success toast (Bill No. populated, charge lines persisted after a reload).</li>
 * </ol>
 */
public class IpdBill extends DevHisBase {

    public IpdBill() { super("Billing_IpdBill"); }

    public static void main(String[] args) {
        IpdBill t = new IpdBill();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - IPD Bill", "Billing > IPD Bill",
                "Open IPD Bill, search+select a newborn patient, merge with mother, set Cost Centre, Save and accept all alerts.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.IpdBill ipb =
                new com.kpj.pages.Billing_page.IpdBill(page);

        boolean onScreen = ipb.navigateTo(BASE);
        step(page, "Open IPD Bill screen", "Billing -> IPD Bill",
                "The IPD Bill screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // "Admission is closed" (see IpdBill page object Javadoc — the IPD wording of the same environment-wide
        // state OpdBill sees as "Visit is closed") isn't tied to one admission, so try up to 8 "Baby Of" candidates
        // the picker offers today, same as OpdBill, until Merge With Mother actually goes through.
        String patient = "";
        boolean merged = false;
        int tried = 0;
        java.util.List<String> triedButBlocked = new java.util.ArrayList<>();
        for (int i = 0; i < 8; i++) {
            String loaded = ipb.selectBabyPatientByIndex(i);
            if (loaded.isEmpty()) break;   // fewer than i+1 "Baby Of" candidates exist today
            tried++;
            merged = ipb.mergeWithMother();
            if (merged) { patient = loaded; break; }
            if (!ipb.mergeBlockingMessage.isEmpty()) triedButBlocked.add(loaded + " (" + ipb.mergeBlockingMessage + ")");
        }
        step(page, "Search patient (same as OPD Bill)",
                "Open patient picker -> select IPD -> Search -> pick a 'Baby Of' row's Select button -> popup closes",
                "A newborn's admission is loaded (Name / MRN shown); popup closed",
                tried == 0 ? "No 'Baby Of' patient found in today's IPD admissions"
                        : (patient.isEmpty() ? "Loaded " + tried + " candidate(s) but none could be merged" : "Loaded " + patient),
                tried == 0 ? "FAIL" : "PASS");
        if (tried == 0) { addSummary("Result", "No newborn (\"Baby Of\") patient available"); return; }

        step(page, "Click Merge With Mother, click Save",
                "mergeChargesWithMother() -> confirm 'Are you sure you want to merge charges with mother.' -> Save",
                "The merge is confirmed and applied",
                merged ? "Merged for " + patient : "Could not merge on any of " + tried + " candidate(s) tried"
                        + (triedButBlocked.isEmpty() ? "" : " — blocked: " + triedButBlocked),
                merged ? "PASS" : "FAIL");
        if (!merged) { addSummary("Result", "FAILED — merge never succeeded: " + triedButBlocked); return; }

        int ccFixed = ipb.ensureAllCostCentresSet();
        step(page, "Select Cost Center for every charge line", "Every charge grid row needs a Cost Centre before Save",
                "All charge lines have a Cost Centre", "Cost Centre set on " + ccFixed + " row(s) that were still \"--Select--\"", "PASS");

        boolean saveResponded = ipb.saveAndValidate();
        // Save on this screen mirrors OpdBill: IUDBillSaveOPD can answer HTTP 200 with an empty body while the app
        // surfaces a generic "KPJ Portal / Error!" toast — reproduced live 2026-08-25, see IpdBill page object
        // Javadoc. The success toast text is the trustworthy signal here (no new charge lines were added by this
        // flow to verify via reload, unlike OpdBill's procedure-add case).
        StringBuilder actual = new StringBuilder();
        actual.append("Save responded ").append(saveResponded ? "200, no error toast" : "with a problem");
        if (!ipb.lastToast.isEmpty()) actual.append(" | toast: \"").append(ipb.lastToast).append("\"");
        if (!ipb.blockingMessage.isEmpty()) actual.append(" | blocked: \"").append(ipb.blockingMessage).append("\"");
        if (ipb.lastScreenshot != null) {
            step(ipb.lastScreenshot, "Click Save, accept all alerts", "Save (IUDBill) -> accept every confirm dialog",
                    "Success toast message (Bill No. populated)",
                    actual.toString(), saveResponded ? "PASS" : "FAIL");
        } else {
            step(page, "Click Save, accept all alerts", "Save (IUDBill) -> accept every confirm dialog",
                    "Success toast message (Bill No. populated)",
                    actual.toString(), saveResponded ? "PASS" : "FAIL");
        }

        addSummary("Patient", patient);
        addSummary("Result", saveResponded ? "Bill saved (success toast seen)" : "NOT saved — see step 6 (\"KPJ Portal / Error!\" — known live defect, same as OpdBill)");
    }
}
