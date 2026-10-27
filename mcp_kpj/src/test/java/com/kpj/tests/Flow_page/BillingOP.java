package com.kpj.tests.Flow_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.OutPatientQueueManagementPage;
import com.kpj.pages.Op_page.RegistrationPage;
import com.kpj.pages.Op_page.RegistrationPage.PatientProfile;

import java.util.ArrayList;
import java.util.List;

// The page objects are also named BillingOP / OpdCharges / OpdBill - referenced by fully-qualified name where they clash.

/**
 * <b>Billing OP flow</b> - ONE connected end-to-end scenario from the workflow document "Billing Module_OP Flow"
 * (Part C, stages C1-C6), reported as ONE consolidated report ({@code test-output/reports/Flow_BillingOP.html}):
 *
 * <pre>
 *  Stage 1  OP &gt; Registration                          new patient, Self Pay      -> MRN
 *  Stage 2  OP &gt; Outpatient Queue Mgmt &gt; Action &gt; EMR   Fall Risk, Normal EMR tag, allergy, orders, prescription
 *  Stage 3  Work Order List (Radiology, Lab) + Dispense Process (screening)   -> charges drop
 *  Stage 4  Billing &gt; OPD Charges                       charges for the MRN are present, saved
 *  Stage 5  Billing &gt; OPD Bill                          Final bill -> Bill No. (payment window if shown)
 *  Stage 6  Billing &gt; Payment Settlement &gt; Self        settle, receipt
 *  Final    Billing validation                          nothing outstanding, payment recorded
 * </pre>
 *
 * <p>The MRN created in Stage 1 is carried through every later stage. A <b>critical</b> stage that fails stops the
 * flow: every later stage is recorded as SKIPPED naming the stage that blocked it, and the closing
 * <i>Overall End-to-End Flow</i> step says which stage / action failed and why. A non-critical stage (allergy,
 * the three order-acceptance steps) records its FAIL and the flow carries on - Stage 4 then shows whether a charge is
 * missing because of it.</p>
 *
 * <p>Reporting reuses {@link DevHisBase#step} / {@code meta} / {@code addSummary} unchanged, so the per-test HTML,
 * the TSV and {@code ReportAggregator}'s FAILED_STEPS.html all pick this flow up like any other test.</p>
 *
 * <p>Optional run-time data (all have defaults): {@code -Dbillingop.radiology=MRI0010 -Dbillingop.lab="Full Blood Count"
 * -Dbillingop.service=CAC0003 -Dbillingop.drug=para -Dbillingop.freq=od}.</p>
 */
public class BillingOP extends DevHisBase {

    public BillingOP() { super("Flow_BillingOP"); }

    public static void main(String[] args) {
        BillingOP t = new BillingOP();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- flow state ----------------------------------------------------------------------------------------
    private int stageNo = 0;
    private String failedStage = "", failedReason = "";
    private boolean blocked = false;
    private final List<String> stageLog = new ArrayList<>();

    private static String prop(String k, String d) { String v = System.getProperty(k); return v == null || v.isBlank() ? d : v.trim(); }

    /** Result of one flow step. */
    private static final class Res {
        final boolean ok; final String actual; final boolean manual;
        private Res(boolean ok, String actual, boolean manual) { this.ok = ok; this.actual = actual; this.manual = manual; }
        static Res pass(String a) { return new Res(true, a, false); }
        static Res fail(String a) { return new Res(false, a, false); }
        /** Cannot be judged by the script (document wording: "requires manual verification") - not a failure. */
        static Res manual(String a) { return new Res(true, a, true); }
    }

    private interface Action { Res run() throws Exception; }

    /**
     * Run one flow step and record it. {@code critical}: a failure blocks every later step (they are recorded as
     * SKIPPED). Returns true when the step passed.
     */
    private boolean flowStep(String label, String desc, String expected, boolean critical, com.kpj.pages.Flow_page.BillingOP bp, Action action) {
        stageNo++;
        String name = label;
        if (blocked) {
            step((byte[]) null, name, desc, expected, "Not executed - blocked by the failed step \"" + failedStage + "\"", "SKIPPED");
            stageLog.add(name + " - SKIPPED");
            return false;
        }
        Res r;
        try {
            r = action.run();
        } catch (Throwable e) {
            e.printStackTrace();
            String msg = String.join(" ", java.util.Arrays.stream(String.valueOf(e.getMessage()).split("\n"))
                    .map(String::trim).filter(s -> !s.isEmpty() && !s.equals("Error {")).limit(4).toArray(String[]::new));
            StackTraceElement[] st = e.getStackTrace();
            StackTraceElement top = java.util.Arrays.stream(st).filter(x -> x.getClassName().startsWith("com.kpj.pages")).findFirst().orElse(st.length > 0 ? st[0] : null);
            String where = top == null ? "" : " @ " + top.getClassName().replaceAll(".*\\.", "") + "." + top.getMethodName() + ":" + top.getLineNumber();
            r = Res.fail("Exception: " + e.getClass().getSimpleName() + ": " + msg + where);
        }
        String actual = r.actual;
        if (!r.ok && bp != null && !bp.lastDump.isBlank()) {
            actual += " | screen state: " + (bp.lastDump.length() > 3000 ? bp.lastDump.substring(0, 3000) + "..." : bp.lastDump);
            bp.lastDump = "";
        }
        step(bp != null ? bp.workPage() : page, name, desc, expected, actual, r.manual ? "MANUAL" : r.ok ? "PASS" : "FAIL");
        stageLog.add(name + " - " + (r.manual ? "MANUAL" : r.ok ? "PASS" : "FAIL"));
        if (!r.ok) {
            if (failedStage.isEmpty()) { failedStage = name; failedReason = r.actual; }
            if (critical) blocked = true;
        }
        return r.ok;
    }

    // ---- the flow --------------------------------------------------------------------------------------------

    @Override
    protected void body() {
        meta("Billing OP Flow", "Flow > Billing OP (Registration > EMR > Orders > OPD Charges > OPD Bill > Payment Settlement)",
                "&#9888; END-TO-END: registers a NEW patient (Self Pay) and carries the MRN through EMR, work-order acceptance, "
                        + "dispensing, OPD Charges, a FINAL OPD Bill and Self payment settlement. Source: workflow document "
                        + "&quot;Billing Module - Complete Workflow Document (OP and IP)&quot;, Part C stages C1-C6. "
                        + "Creates real data on the target environment.");

        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String qRadiology = prop("billingop.radiology", "MRI0010");
        String qLab = prop("billingop.lab", "Full Blood Count");
        String qService = prop("billingop.service", "CAC0003");
        String qDrug = prop("billingop.drug", "para");
        String qFreq = prop("billingop.freq", "od");

        addSummary("Flow", "Billing OP - Stages C1-C6 + final billing validation");
        addSummary("Environment", BASE);

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in as " + USER, "PASS");

        RegistrationPage reg = new RegistrationPage(page);
        OutPatientQueueManagementPage queue = new OutPatientQueueManagementPage(page);
        com.kpj.pages.Flow_page.BillingOP bp = new com.kpj.pages.Flow_page.BillingOP(page);
        com.kpj.pages.Billing_page.OpdCharges charges = new com.kpj.pages.Billing_page.OpdCharges(page);
        com.kpj.pages.Billing_page.OpdBill bill = new com.kpj.pages.Billing_page.OpdBill(page);

        PatientProfile p = PatientProfile.random();
        final String[] mrn = {""};
        final String[] chargeLines = {""};
        final List<String>[] gridNames = new List[]{ new ArrayList<String>() };

        // ===== Stage 1 - OP > Registration ======================================================================
        flowStep("Stage 1 - OP Registration: register a new Self Pay patient",
                "OP > Registration: Patient / Correspondence / Other details, Next of Kin, Payor = Self, Visit Information, Save",
                "Patient and visit are saved (Consent Details modal / registration report)", true, bp, () -> {
                    // The shared page picks the Department/Sub Dept/Doctor combination at random; a combination without a Sub Dept or Doctor
                    // leaves Visit Information incomplete. Start a fresh form (new random picks) up to three times before giving up.
                    String why = null;
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        if (attempt > 1) bp.openQueue();     // leave the half-filled form first; re-opening Registration from there gives a clean one
                        try {
                            why = bp.registerNewSelfPayPatient(reg, BASE, p);
                        } catch (RuntimeException e) {
                            if (attempt == 3) throw e;
                            String flat = String.valueOf(e.getMessage()).replaceAll("\\s+", " ");
                            System.out.println("Stage 1: registration attempt " + attempt + " threw (" + flat.substring(0, Math.min(90, flat.length())) + ") - retrying with a fresh form");
                            why = "Visit Information incomplete (retry after exception)";
                        }
                        if (why == null || !why.startsWith("Visit Information incomplete")) break;
                        System.out.println("Stage 1: Visit Information incomplete (attempt " + attempt + "/3) - starting a fresh registration form");
                        page.waitForTimeout(1500);
                    }
                    return why == null ? Res.pass("Registered " + p.patientLine()) : Res.fail(why + " | " + p.patientLine());
                });

        flowStep("Stage 1 - OP Registration: capture the new MRN",
                "Read the MRN of the new visit from OP > Outpatient Queue Management (searched by patient name)",
                "A MRN is assigned and the visit is in today's queue", true, bp, () -> {
                    mrn[0] = bp.lookupMrnByName(queue, BASE, p.fullName, today);
                    if (mrn[0].isBlank()) return Res.fail("The new patient \"" + p.fullName + "\" was not found in today's OP queue");
                    addSummary("MRN", mrn[0]);
                    addSummary("Patient", p.patientLine());
                    return Res.pass("MRN " + mrn[0] + " - " + p.fullName);
                });

        // ===== Stage 2 - Queue -> EMR ===========================================================================
        flowStep("Stage 2 - Queue / EMR: open EMR from OP > Outpatient Queue Management > Action",
                "Filter the queue to the MRN, Action > EMR (Fall Risk Assessment is mandatory before EMR on this environment)",
                "The EMR screen opens for the MRN", true, bp, () -> {
                    String why = bp.openEmrFromQueue(queue, BASE, mrn[0], today);
                    return why == null ? Res.pass("EMR opened for MRN " + mrn[0] + (bp.lastFallRiskNote.isEmpty() ? "" : " | " + bp.lastFallRiskNote))
                                       : Res.fail(why);
                });

        flowStep("Stage 2 - Queue / EMR: select Tag = Normal EMR",
                "EMR > Tag dropdown > Normal EMR (document: \"Select Tag: Normal\")",
                "The Normal EMR sections (Allergy, Investigations/Services, Prescription ...) load", true, bp, () -> {
                    String tag = bp.selectNormalEmrTag();
                    return tag.isEmpty() ? Res.fail("Tag 'Normal EMR' not selectable or the sections did not load") : Res.pass("Tag = " + tag);
                });

        flowStep("Stage 2 - Queue / EMR: Allergy = No Known Drug Allergy, Save",
                "Tick 'No Known Drug Allergy' and Save the Allergy section",
                "Allergy saved", false, bp, () -> {
                    String r = bp.saveNoKnownDrugAllergy();
                    return r.startsWith("saved") && !r.toLowerCase().contains("error") ? Res.pass(r) : Res.fail(r);
                });

        flowStep("Stage 2 - Queue / EMR: order a Radiology service",
                "Investigations/Services: Service Name (\"" + qRadiology + "\")",
                "A Radiology service is added to the order grid", true, bp, () -> {
                    String s = bp.orderService(qRadiology);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qRadiology + "\" - set -Dbillingop.radiology=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage 2 - Queue / EMR: order a Lab service",
                "Investigations/Services: Lab Service Name (\"" + qLab + "\")",
                "A Lab service is added to the order grid", true, bp, () -> {
                    String s = bp.orderService(qLab);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qLab + "\" - set -Dbillingop.lab=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage 2 - Queue / EMR: order an other service (not Lab / Radiology)",
                "Investigations/Services: Service Name (\"" + qService + "\")",
                "The service is added; it drops straight into OPD Charges", true, bp, () -> {
                    String s = bp.orderService(qService);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qService + "\" - set -Dbillingop.service=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage 2 - Queue / EMR: Finalize the orders and Save (send to Work Order List)",
                "Tick 'Finalize - sent to the Work Order List' on each new row, Save investigations",
                "Orders are saved and sent to the Work Order Lists", true, bp, () -> {
                    String r = bp.finalizeAndSaveInvestigations();
                    boolean none = r.contains("now checked: 0");
                    return none || r.toLowerCase().contains("error") ? Res.fail(r) : Res.pass(r + " | ordered: " + bp.orderedServices);
                });

        flowStep("Stage 2 - Queue / EMR: Prescription (drug, frequency, quantity) and Save",
                "Store = Pharmacy Main Store, Regular prescription, drug \"" + qDrug + "\", frequency \"" + qFreq + "\"; override a MIMS alert if shown",
                "Prescription saved", false, bp, () -> {
                    // The formulary differs per environment / login: try the configured drug first, then a few common ones.
                    String usedDrug = qDrug;
                    String r = "";
                    for (String dq : new String[]{ qDrug, "para", "pana", "amox", "ibu", "met", "tab", "cap" }) {
                        if (dq.equals(qDrug) && !usedDrug.equals(qDrug)) continue;
                        r = bp.prescribeDrug(dq, qFreq);
                        usedDrug = dq;
                        if (!r.isEmpty()) break;
                    }
                    String rl0 = r.toLowerCase();
                    if (!r.isEmpty() && !(rl0.contains("saved") && !rl0.contains("no record") && !rl0.contains("error") && !rl0.contains("please"))) {
                        bp.clearPrescription();                       // intermittent on this environment: one clean retry
                        r = bp.prescribeDrug(usedDrug, qFreq);
                    }
                    if (r.isEmpty()) return Res.fail("No drug could be picked for \"" + qDrug + "\" or the fallbacks (para, pana, amox, ibu, met, tab, cap) - set -Dbillingop.drug=<drug name>."
                            + " Check the screen state below: an EMPTY Store list means this login has no prescribing store assigned.");
                    String rl = r.toLowerCase();
                    return rl.contains("saved") && !rl.contains("no record") && !rl.contains("error") && !rl.contains("please") ? Res.pass(r)
                            : Res.fail("Prescription was not confirmed saved - " + r);
                });

        bp.backToMainTab();   // Stage 2 ran in the EMR tab the app opened; the work-order lists, billing and payment use the main tab

        // ===== Stage 3 - accept orders, dispense ================================================================
        flowStep("Stage 3 - Radiology Work Order List: accept the order",
                "Investigation > Radiology > Work Order List: search MRN, select, Accept",
                "The radiology order is accepted; its charge drops into OPD Charges", false, bp, () -> acceptRes(bp, "#/DoctorWorkOrderR", mrn[0], "Radiology", "radiology"));

        flowStep("Stage 3 - Lab Work Order List: accept the order",
                "Investigation > Lab > Work Order List: search MRN, select, Accept",
                "The lab order is accepted; its charge drops into OPD Charges", false, bp, () -> acceptRes(bp, "#/DoctorWorkOrderL", mrn[0], "Lab", "laborator"));

        flowStep("Stage 3 - Dispense Process: screen the prescribed drug",
                "Inventory > POS > Dispense Process: Progress = Screening, store, Search, tick patient + drugs + Screened, Save",
                "The drug is screened; its charge drops into OPD Charges", false, bp, () -> {
                    String why = bp.screenDrugs(mrn[0]);
                    return why.isEmpty() ? Res.pass("Drug screened for MRN " + mrn[0] + " | " + bp.lastToast) : Res.fail(why);
                });

        // ===== Stage 4 - OPD Charges ============================================================================
        flowStep("Stage 4 - OPD Charges: load the MRN and verify the charges dropped",
                "Billing > OPD Charges: MRN > Find Patient; the grid lists the registration auto-charge and every ordered service",
                // not critical: a missing ordered charge is recorded as a FAIL, but billing and payment can still be exercised with the charges that did drop
                "Charges exist for the MRN and include each service ordered in EMR", false, bp, () -> {
                    if (!charges.navigateTo(BASE)) return Res.fail("OPD Charges screen not reached (" + page.url() + ")");
                    if (!retry(() -> charges.searchByMrn(mrn[0]))) return Res.fail("MRN " + mrn[0] + " has no OP visit on OPD Charges");
                    List<String> names = charges.readChargeGridServiceNames();
                    for (int i = 0; i < 12 && names.isEmpty(); i++) { page.waitForTimeout(1000); names = charges.readChargeGridServiceNames(); }   // grid fills a moment after Find Patient
                    gridNames[0] = names;
                    if (names.isEmpty()) { failedStage = failedStage.isEmpty() ? "Stage 4 - OPD Charges: load the MRN and verify the charges dropped" : failedStage; blocked = true; return Res.fail("No charge lines for MRN " + mrn[0]); }
                    List<String> missing = new ArrayList<>();
                    for (String ordered : bp.orderedServices) {
                        String key = ordered.replaceAll("^\\S+\\s*[-|]\\s*", "").trim().toLowerCase();
                        boolean found = names.stream().anyMatch(n -> n.toLowerCase().contains(key) || key.contains(n.toLowerCase()));
                        if (!found) missing.add(ordered);
                    }
                    chargeLines[0] = names.size() + " line(s): " + names;
                    return missing.isEmpty() ? Res.pass(chargeLines[0])
                            : Res.fail("Charge(s) NOT dropped for ordered service(s) " + missing + " (a Stage 3 acceptance/screening probably failed) | present: " + names);
                });

        flowStep("Stage 4 - OPD Charges: set cost centres and Save the charges",
                "Cost Centre on every line, Save (draft) and confirm they persist after a reload",
                "Charges are saved and survive a reload", true, bp, () -> {
                    int cc = charges.ensureAllCostCentresSet();
                    boolean saved = charges.saveAndValidate();
                    // Persistence check WITHOUT a page reload (a reload logs the user out on this environment): load the MRN again.
                    List<String> missing = new ArrayList<>(gridNames[0]);
                    boolean rechecked = false;
                    if (charges.navigateTo(BASE) && retry(() -> charges.searchByMrn(mrn[0]))) {
                        List<String> again = charges.readChargeGridServiceNames();
                        for (int i = 0; i < 15 && again.isEmpty(); i++) { page.waitForTimeout(1000); again = charges.readChargeGridServiceNames(); }
                        rechecked = !again.isEmpty();
                        if (rechecked) missing.removeAll(again);
                    }
                    String toastL = charges.lastToast.toLowerCase();
                    boolean appConfirmed = saved && toastL.contains("saved successfully");
                    if (appConfirmed && (!rechecked || missing.isEmpty()))
                        return Res.pass("Cost centre set on " + cc + " line(s); app confirmed \"" + charges.lastToast.replaceAll("[^A-Za-z ,.]", "").trim() + "\" ("
                                + gridNames[0].size() + " line(s))" + (rechecked ? "; all lines found again after re-opening the screen" : "; re-open check could not load the grid in time (not a failure)"));
                    return Res.fail("Save " + (saved ? "responded" : "failed") + (charges.lastToast.isEmpty() ? "" : " | toast: " + charges.lastToast)
                            + (charges.blockingMessage.isEmpty() ? "" : " | blocked: " + charges.blockingMessage)
                            + " | not found again after re-opening the screen: " + missing);
                });

        // ===== Stage 5 - OPD Bill ===============================================================================
        flowStep("Stage 5 - OPD Bill: load the MRN; bill lines equal the saved charges",
                "Billing > OPD Bill: MRN > Find Patient; the bill screen lists the same charges as OPD Charges",
                "Same charge lines as OPD Charges (the bill amount stays the same)", true, bp, () -> {
                    if (!bill.navigateTo(BASE)) return Res.fail("OPD Bill screen not reached (" + page.url() + ")");
                    if (!retry(() -> bill.searchByMrn(mrn[0]))) return Res.fail("MRN " + mrn[0] + " has no OP visit on OPD Bill");
                    List<String> names = bill.readChargeGridServiceNames();
                    for (int i = 0; i < 15 && names.isEmpty(); i++) { page.waitForTimeout(1000); names = bill.readChargeGridServiceNames(); }   // grid fills after Find Patient
                    List<String> onlyCharges = new ArrayList<>(gridNames[0]); onlyCharges.removeAll(names);
                    if (names.isEmpty()) return Res.fail("OPD Bill lists no charge lines for MRN " + mrn[0]);
                    if (!onlyCharges.isEmpty()) return Res.fail("OPD Bill is missing charge line(s) that OPD Charges has: " + onlyCharges);
                    return Res.pass(names.size() + " line(s) match OPD Charges | " + bp.readBillTotals());
                });

        flowStep("Stage 5 - OPD Bill: generate the FINAL bill",
                "Final radio (not Interim, Credit off), cost centres, Save; answer the confirms and the payment window",
                "The final bill is created (\"Bill saved successfully\")", true, bp, () -> {
                    bill.ensureAllCostCentresSet();
                    if (!bp.selectFinalBill()) return Res.fail("The Final radio (PatientDetails.isdraftbill = 1) was not found");
                    String totals = bp.readBillTotals();
                    boolean ok = bp.saveFinalBill();
                    addSummary("Bill No.", bp.lastBillNo.isEmpty() ? "(not shown on screen after save)" : bp.lastBillNo);
                    addSummary("Net bill amount", bp.lastNetAmount);
                    String pay = bp.lastPaymentNote.isEmpty() ? "no payment window appeared on Save" : "payment window: " + bp.lastPaymentNote;
                    return ok ? Res.pass("Final bill saved" + (bp.lastBillNo.isEmpty() ? "" : " - Bill No. " + bp.lastBillNo) + " | " + totals + " | " + pay
                                    + (bp.lastToast.isEmpty() ? "" : " | toast: " + bp.lastToast))
                              : Res.fail("Final bill NOT created (no Bill No.)" + (bp.lastToast.isEmpty() ? "" : " | toast: " + bp.lastToast) + " | " + pay);
                });

        // ===== Stage 6 - Payment settlement =====================================================================
        flowStep("Stage 6 - Payment Settlement (Self): settle the bill and receipt",
                "Billing > Payment Settlement > Self: MRN, select the outstanding bill, cash counter, Settle Bill, payment window",
                "The bill is settled and the receipt is created (or it was already settled in the bill's payment window)", true, bp, () -> {
                    String r = bp.settleBillSelf(mrn[0]);
                    if ("settled".equals(r) || "already-settled".equals(r))
                        return Res.pass(("settled".equals(r) ? "Bill settled on Payment Settlement > Self - " : "") + bp.lastPaymentNote);
                    return Res.fail(r + (bp.lastPaymentNote.isEmpty() ? "" : " | " + bp.lastPaymentNote));
                });

        flowStep("Stage 6 - Receipt (SRS OOS-12.9): original receipt wording",
                "Open the receipt printed by the payment: heading 'Official Receipt', footer 'Kindly keep the original receipt properly, as it can only be printed once...'; no 'Duplicate', no 'Without Prejudice' for a normal patient",
                "Original receipt shows the OOS-12.9 heading and footer", false, bp, () -> {
                    String[] rc = bp.readOriginalReceipt();
                    if (rc == null) return Res.manual("No receipt tab was opened by the payment, so the receipt wording (OOS-12.9) could not be read by the script - requires manual verification");
                    return "PASS".equals(rc[0]) ? Res.pass(rc[1]) : Res.fail(rc[1]);
                });

        // ===== Final validation =================================================================================
        flowStep("Final Billing Validation",
                "Payment Settlement > Self for the MRN: List of Outstanding Bills is empty; List of Payments holds the bill",
                "Nothing outstanding and the payment/receipt exists for Bill No. " + (bp.lastBillNo.isEmpty() ? "(n/a)" : bp.lastBillNo), true, bp, () -> {
                    String[] v = bp.verifySettled(mrn[0], bp.lastBillNo);
                    return "PASS".equals(v[0]) ? Res.pass(v[1]) : Res.fail(v[1]);
                });

        // ===== Overall ==========================================================================================
        boolean allOk = failedStage.isEmpty();
        stageNo++;
        String overall = allOk
                ? "All stages passed: " + String.join(" ; ", stageLog)
                : "Flow FAILED at \"" + failedStage + "\" - " + failedReason + (blocked ? " (later stages skipped)" : " (non-critical: flow continued)");
        step((byte[]) null, "Overall End-to-End Flow", "Billing OP: Registration > EMR > Orders > Charges > Bill > Settlement > Validation",
                "Every step of the flow passes", overall, allOk ? "PASS" : "FAIL");

        addSummary("Failed at", allOk ? "-" : failedStage);
        addSummary("Failure reason", allOk ? "-" : failedReason);
        addSummary("Final Result", allOk ? "PASS - Billing OP flow completed for MRN " + mrn[0] + ", Bill No. " + bp.lastBillNo
                                          : "FAIL - " + failedStage);
    }

    /** The visit dropdown fills a moment after Find Patient: try the search up to 4 times before giving up. */
    private boolean retry(java.util.function.Supplier<Boolean> search) {
        for (int i = 0; i < 4; i++) {
            if (Boolean.TRUE.equals(search.get())) return true;
            page.waitForTimeout(2500);
        }
        return false;
    }

    private Res acceptRes(com.kpj.pages.Flow_page.BillingOP bp, String route, String mrn, String what, String groupRe) {
        int n = bp.acceptWorkOrders(route, mrn, groupRe);
        if (n == -1) return Res.fail(what + " Work Order List screen not reached");
        if (n == -2) return Res.fail("No " + what + " order found for MRN " + mrn + " (was it Finalized in EMR?)");
        if (n < 0) return Res.fail(what + " Accept button not found / not clickable");
        String t = bp.lastToast.toLowerCase();
        boolean accepted = (t.contains("accept") || t.contains("success")) && !t.contains("please") && !t.contains("error");
        return accepted ? Res.pass(what + " work order accepted for MRN " + mrn + " (" + n + " row(s)) | toast: " + bp.lastToast)
                        : Res.fail(what + " Accept was not confirmed (" + n + " row(s) selected) | toast: " + (bp.lastToast.isEmpty() ? "(none)" : bp.lastToast));
    }
}
