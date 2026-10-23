package com.kpj.tests.Flow_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Ip.InPatients.OccupancyList;

import java.util.ArrayList;
import java.util.List;

// The page objects are also named BillingIP / BillingOP / IpdCharges / IpdBill - referenced by fully-qualified name where they clash.

/**
 * <b>Billing IP flow</b> - ONE connected end-to-end scenario from the workflow document "Billing Complete Workflow
 * (OP and IP)" v3.1, Part D (stages D1-D5) plus the Part B masters that drive the IP charges, reported as ONE
 * consolidated report ({@code <outdir>/reports/Flow_BillingIP.html}, copied to {@code <outdir>/BillingIP/}):
 *
 * <pre>
 *  D1  IP &gt; Admission                                  new patient, Self payor, vacant bed        -> admitted
 *  D1  IP &gt; Inpatients &gt; Occupancy List                the admission is listed                   -> MRN / IPD No.
 *  D2  Occupancy List &gt; EMR                            Normal EMR tag, allergy, orders, prescription
 *  D2  Work Order Lists (Radiology, Lab) + Dispense Process (screening)                            -> charges drop
 *  D3  Billing &gt; IPD Charges                           search MRN, charges + auto charges present, Part B masters, Save
 *  D4  Billing &gt; IPD Bill                              Interim, then Final (payment window)       -> Bill No.
 *  D5  Bill Estimation, Occupancy List &gt; Time Trail, Additional Deposit Request
 *  Final  Billing validation                           bill settled, Final Bill Generation on the Time Trail
 * </pre>
 *
 * <p>The MRN found in D1 is carried through every later stage. A <b>critical</b> stage that fails stops the flow:
 * every later stage is recorded as SKIPPED naming the stage that blocked it, and the closing <i>Overall End-to-End
 * Flow</i> step says which stage / action failed and why. A non-critical stage (allergy, order acceptance, Part B
 * masters check, Interim bill, the D5 support screens) records its FAIL and the flow carries on.</p>
 *
 * <p>Reporting reuses {@link DevHisBase#step} / {@code meta} / {@code addSummary} unchanged, so the per-test HTML, the
 * TSV and {@code ReportAggregator}'s FAILED_STEPS.html pick this flow up like any other test.</p>
 *
 * <p>Optional run-time data (all have defaults): {@code -Dbillingip.radiology=MRI0010 -Dbillingip.lab="Full Blood Count"
 * -Dbillingip.service=CAC0003 -Dbillingip.drug=para -Dbillingip.freq=od}. {@code -Dbillingip.mrn=<MRN>} uses an
 * already-admitted patient instead of admitting a new one (needed when the environment has no vacant bed). {@code -Ddevhis.nationality=Australian}
 * admits a foreign patient (same switch as the admission test).</p>
 */
public class BillingIP extends DevHisBase {

    public BillingIP() { super("Flow_BillingIP"); }

    public static void main(String[] args) {
        BillingIP t = new BillingIP();
        try { t.run(); } finally { t.stop(); t.publishConsolidatedReport(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); publishConsolidatedReport(); } }

    /**
     * The framework writes the report to {@code <outdir>/reports/Flow_BillingIP.html} (+ the TSV in {@code aggregate-data}), which is
     * what the summary / FAILED_STEPS aggregation reads. A copy of both goes to {@code <outdir>/BillingIP/} so the tester finds the
     * whole Billing IP flow in one folder.
     */
    private void publishConsolidatedReport() {
        try {
            java.nio.file.Path out = reportDir.getParent() == null ? java.nio.file.Paths.get("BillingIP") : reportDir.getParent().resolve("BillingIP");
            java.nio.file.Files.createDirectories(out);
            java.nio.file.Path html = reportDir.resolve("Flow_BillingIP.html");
            if (java.nio.file.Files.exists(html)) java.nio.file.Files.copy(html, out.resolve("Flow_BillingIP.html"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            java.nio.file.Path tsv = (reportDir.getParent() == null ? java.nio.file.Paths.get("aggregate-data") : reportDir.getParent().resolve("aggregate-data")).resolve("Flow_BillingIP.tsv");
            if (java.nio.file.Files.exists(tsv)) java.nio.file.Files.copy(tsv, out.resolve("Flow_BillingIP.tsv"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Billing IP report copy: " + out.resolve("Flow_BillingIP.html").toAbsolutePath());
        } catch (Exception e) {
            System.out.println("publishConsolidatedReport: could not copy the report - " + e.getMessage());
        }
    }

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
        /** Cannot be judged by the script - not a failure. */
        static Res manual(String a) { return new Res(true, a, true); }
    }

    private interface Action { Res run() throws Exception; }

    /**
     * Run one flow step and record it. {@code critical}: a failure blocks every later step (they are recorded as
     * SKIPPED). Returns true when the step passed.
     */
    private boolean flowStep(String label, String desc, String expected, boolean critical, com.kpj.pages.Flow_page.BillingIP bp, Action action) {
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
        step(page, name, desc, expected, actual, r.manual ? "MANUAL" : r.ok ? "PASS" : "FAIL");
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
        meta("Billing IP Flow", "Flow > Billing IP (Admission > Occupancy List > EMR > Orders > IPD Charges > IPD Bill > Support screens)",
                "&#9888; END-TO-END: admits a NEW inpatient (Self payor, vacant bed) and carries the MRN through the Occupancy List, EMR, "
                        + "work-order acceptance, dispensing, IPD Charges, an Interim and a FINAL IPD Bill (payment window), then the IP support "
                        + "screens (Bill Estimation, Time Trail; the Additional Deposit Request is on hold in document v4). Source: workflow document "
                        + "&quot;Billing Module - Complete Workflow Document (OP and IP)&quot; v3.1, Part D stages D1-D5 and the Part B masters. "
                        + "Creates real data on the target environment; the deposit request is only opened, never submitted.");

        String qRadiology = prop("billingip.radiology", "MRI0010");
        String qLab = prop("billingip.lab", "Full Blood Count");
        String qService = prop("billingip.service", "CAC0003");
        String qDrug = prop("billingip.drug", "para");
        String qFreq = prop("billingip.freq", "od");

        addSummary("Flow", "Billing IP - Stages D1-D5 + Part B masters + final billing validation");
        addSummary("Environment", BASE);

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in as " + USER, "PASS");

        com.kpj.pages.Ip.Admission admission = new com.kpj.pages.Ip.Admission(page);
        OccupancyList occ = new OccupancyList(page);
        com.kpj.pages.Flow_page.BillingIP bp = new com.kpj.pages.Flow_page.BillingIP(page);
        com.kpj.pages.Flow_page.BillingOP op = new com.kpj.pages.Flow_page.BillingOP(page);   // EMR / work-order / dispense / payment window, shared with OP
        com.kpj.pages.Billing_page.IpdCharges charges = new com.kpj.pages.Billing_page.IpdCharges(page);
        com.kpj.pages.Billing_page.IpdBill bill = new com.kpj.pages.Billing_page.IpdBill(page);

        final String[] mrn = {""};
        final String[] ipdNo = {""};
        final List<String>[] chargeLines = new List[]{ new ArrayList<String>() };
        final boolean[] finalBillSaved = {false};

        // ===== D1 - IP > Admission ==============================================================================
        bp.existingMrn = prop("billingip.mrn", "");
        if (!bp.existingMrn.isEmpty()) {
            flowStep("Stage D1 - IP Admission: use the already-admitted patient (-Dbillingip.mrn)",
                    "No new admission is made; MRN " + bp.existingMrn + " must be an admitted inpatient",
                    "The MRN is on the Occupancy List (next step)", true, bp,
                    () -> Res.manual("Admission skipped by request - using MRN " + bp.existingMrn + ". The new-admission step (bed, payor, save) was NOT exercised in this run"));
        } else
        flowStep("Stage D1 - IP Admission: admit a new Self payor patient to a vacant bed",
                "IP > Admission: Patient / Correspondence, Next of Kin, Payor = Self, Admission Information, Room Type + Ward, tick a vacant bed, Save",
                "\"Patient Admitted Successfully.\"; bed charge and auto charges begin", true, bp, () -> {
                    String why = bp.admitNewSelfPayPatient(admission);
                    if (why != null) return Res.fail(why + (bp.admissionNotes.isEmpty() ? "" : " | admission as filled: " + bp.admissionNotes));
                    addSummary("Patient", bp.patientLine);
                    addSummary("Admission", bp.admissionNotes);
                    return Res.pass("Admitted | " + bp.patientLine + " | " + bp.admissionNotes);
                });

        flowStep("Stage D1 - Occupancy List: the admission is listed, capture MRN and IPD No.",
                "IP > Inpatients > Occupancy List: search the last month to today; find the new admission (matched on its NRIC)",
                "The admitted patient shows in the Occupancy List with an MRN", true, bp, () -> {
                    String[] a = bp.lookupAdmission(occ);
                    if (a == null) return Res.fail("The new admission (NRIC " + bp.nric + ") is not on the Occupancy List");
                    mrn[0] = a[0]; ipdNo[0] = a[1];
                    addSummary("MRN", mrn[0]);
                    addSummary("IPD No.", ipdNo[0].isBlank() ? "(not shown in the list)" : ipdNo[0]);
                    return Res.pass("MRN " + mrn[0] + (ipdNo[0].isBlank() ? "" : " | IPD No. " + ipdNo[0]) + " | " + a[2]);
                });

        // ===== D2 - Occupancy List -> EMR =======================================================================
        flowStep("Stage D2 - Occupancy List / EMR: select the patient and open EMR",
                "IP > Inpatients > Occupancy List: select the patient > EMR (a Fall Risk Assessment is completed first if the app demands it)",
                "The EMR screen opens for the admitted patient", true, bp, () -> {
                    String why = bp.openEmrFromOccupancy(occ, op, mrn[0]);
                    return why == null ? Res.pass("EMR opened for MRN " + mrn[0] + (bp.lastFallRiskNote.isEmpty() ? "" : " | " + bp.lastFallRiskNote)) : Res.fail(why);
                });

        flowStep("Stage D2 - EMR: select Tag = Normal EMR",
                "EMR > Tag dropdown > Normal EMR (document: \"Select EMR tag Normal\")",
                "The Normal EMR sections (Allergy, Investigations/Services, Prescription ...) load", true, bp, () -> {
                    String tag = op.selectNormalEmrTag();
                    return tag.isEmpty() ? Res.fail("Tag 'Normal EMR' not selectable or the sections did not load") : Res.pass("Tag = " + tag);
                });

        flowStep("Stage D2 - EMR: Allergy = No Known Drug Allergy, Save",
                "Tick 'No Known Drug Allergy' and Save the Allergy section (document: \"set Allergy to no known allergy\")",
                "Allergy saved", false, bp, () -> {
                    String r = op.saveNoKnownDrugAllergy();
                    return r.startsWith("saved") && !r.toLowerCase().contains("error") ? Res.pass(r) : Res.fail(r);
                });

        flowStep("Stage D2 - EMR: add a Radiology service",
                "Investigations/Services: Service Name (\"" + qRadiology + "\")",
                "A Radiology service is added to the order grid", true, bp, () -> {
                    String s = op.orderService(qRadiology);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qRadiology + "\" - set -Dbillingip.radiology=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage D2 - EMR: add a Lab service",
                "Investigations/Services: Lab Service Name (\"" + qLab + "\")",
                "A Lab service is added to the order grid", true, bp, () -> {
                    String s = op.orderService(qLab);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qLab + "\" - set -Dbillingip.lab=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage D2 - EMR: add an other service (not Lab / Radiology)",
                "Investigations/Services: Service Name (\"" + qService + "\")",
                "The service is added; it drops into IPD Charges", true, bp, () -> {
                    String s = op.orderService(qService);
                    return s.isEmpty() ? Res.fail("No service matched \"" + qService + "\" - set -Dbillingip.service=<service code/name>") : Res.pass("Added " + s);
                });

        flowStep("Stage D2 - EMR: Finalize the orders and Save (send to Work Order List)",
                "Tick 'Finalize - sent to the Work Order List' on each new row, Save investigations",
                "Orders are saved and sent to the Work Order Lists", true, bp, () -> {
                    String r = op.finalizeAndSaveInvestigations();
                    boolean none = r.contains("now checked: 0");
                    return none || r.toLowerCase().contains("error") ? Res.fail(r) : Res.pass(r + " | ordered: " + op.orderedServices);
                });

        flowStep("Stage D2 - EMR: Prescription (drug, frequency, quantity) and Save",
                "Store = Pharmacy Main Store, Regular prescription, drug \"" + qDrug + "\", frequency \"" + qFreq + "\"; override a MIMS alert if shown",
                "Prescription saved", true, bp, () -> {
                    String r = op.prescribeDrug(qDrug, qFreq);
                    String rl0 = r.toLowerCase();
                    if (!r.isEmpty() && !(rl0.contains("saved") && !rl0.contains("no record") && !rl0.contains("error") && !rl0.contains("please"))) {
                        op.clearPrescription();                       // intermittent on this environment: one clean retry
                        r = op.prescribeDrug(qDrug, qFreq);
                    }
                    if (r.isEmpty()) return Res.fail("No drug matched \"" + qDrug + "\" - set -Dbillingip.drug=<drug name>");
                    String rl = r.toLowerCase();
                    return rl.contains("saved") && !rl.contains("no record") && !rl.contains("error") && !rl.contains("please") ? Res.pass(r)
                            : Res.fail("Prescription was not confirmed saved - " + r);
                });

        // ===== D2 step 4 - accept the orders (same screens as OP stage C3) ======================================
        flowStep("Stage D2 - Radiology Work Order List: accept the order",
                "Investigation > Radiology > Work Order List: search MRN, select, Accept",
                "The radiology order is accepted; its charge drops into IPD Charges", false, bp, () -> acceptRes(op, "#/DoctorWorkOrderR", mrn[0], "Radiology", "radiology"));

        flowStep("Stage D2 - Lab Work Order List: accept the order",
                "Investigation > Lab > Work Order List: search MRN, select, Accept",
                "The lab order is accepted; its charge drops into IPD Charges", false, bp, () -> acceptRes(op, "#/DoctorWorkOrderL", mrn[0], "Lab", "laborator"));

        flowStep("Stage D2 - Dispense Process: screen the prescribed drug",
                "Inventory > POS > Dispense Process: Progress = Screening, store, Search, tick patient + drugs + Screened, Save",
                "The drug is screened; its charge drops into IPD Charges", false, bp, () -> {
                    String why = op.screenDrugs(mrn[0]);
                    return why.isEmpty() ? Res.pass("Drug screened for MRN " + mrn[0] + " | " + op.lastToast) : Res.fail(why);
                });

        // ===== D3 - IPD Charges =================================================================================
        flowStep("Stage D3 - IPD Charges: search the MRN; admission header and charges are shown",
                "Billing > IPD Charges: MRN > search; the screen shows IPD No., Ward, Room Type, Bed, Doctor, Payor ...; Bill Details lists the auto charges and every accepted order",
                "Admission loads and the grid holds the auto charge(s) plus each service ordered in EMR", true, bp, () -> {
                    if (!charges.navigateTo(BASE)) return Res.fail("IPD Charges screen not reached (" + page.url() + ")");
                    if (!retry(() -> bp.searchIpdByMrn(charges, mrn[0]))) return Res.fail("MRN " + mrn[0] + " did not load an admission on IPD Charges");
                    List<String> lines = bp.readChargeLines();
                    for (int i = 0; i < 12 && lines.isEmpty(); i++) { page.waitForTimeout(1000); lines = bp.readChargeLines(); }   // grid fills a moment after the search
                    chargeLines[0] = lines;
                    if (lines.isEmpty()) return Res.fail("No charge lines for MRN " + mrn[0] + " | header: " + bp.lastHeader);
                    List<String> names = new ArrayList<>();
                    for (String l : lines) names.add(com.kpj.pages.Flow_page.BillingIP.lineName(l));
                    List<String> missing = new ArrayList<>();
                    for (String ordered : op.orderedServices) {
                        String key = ordered.replaceAll("^\\S+\\s*[-|]\\s*", "").trim().toLowerCase();
                        boolean found = names.stream().anyMatch(n -> n.toLowerCase().contains(key) || key.contains(n.toLowerCase()));
                        if (!found) missing.add(ordered);
                    }
                    String shown = lines.size() + " line(s): " + lines + " | header: " + bp.lastHeader;
                    return missing.isEmpty() ? Res.pass(shown)
                            : Res.fail("Charge(s) NOT dropped for ordered service(s) " + missing + " (a stage D2 acceptance/screening probably failed) | present: " + shown);
                });

        flowStep("Stage D3 - Masters (Part B): bed-charge auto charge and prices are set up",
                "Observe the charge lines: the Bed master's bed charge / nursing charge service dropped at admission; each line has a price from the Service / Pricing Policy masters",
                "A bed charge line exists and no line is zero-priced", false, bp, () -> {
                    String why = bp.mastersProblems(chargeLines[0]);
                    return why.isEmpty() ? Res.pass("Bed charge present: " + bp.lastBedCharge + "; all " + chargeLines[0].size() + " line(s) priced")
                                         : Res.fail("Master data gap: " + why);
                });

        flowStep("Stage D3 - IPD Charges: cost centres, cash counter and Save",
                "Cost Centre on every line, Charge Date + mandatory Cash Counter, Save (document: \"Select the Cash Counter* (mandatory). Click Save\")",
                "The charges are saved", true, bp, () -> {
                    int cc = charges.ensureAllCostCentresSet();
                    String mand = charges.fillMandatory();
                    com.microsoft.playwright.Page rpt = charges.saveAndCapture();
                    String toast = charges.lastToast == null ? "" : charges.lastToast;
                    String tl = toast.toLowerCase();
                    boolean blockedMsg = !charges.blockingMessage.isEmpty();
                    boolean ok = !blockedMsg && !tl.contains("error") && !tl.contains("please") && (rpt != null || tl.contains("saved") || tl.contains("success"));
                    String detail = "cost centre set on " + cc + " line(s) | " + mand + (toast.isEmpty() ? "" : " | toast: " + toast) + (rpt != null ? " | report tab opened" : "");
                    bp.closeExtraTabs();
                    return ok ? Res.pass("Charges saved - " + detail)
                              : Res.fail("Charges NOT saved - " + detail + (blockedMsg ? " | blocked: " + charges.blockingMessage : "") + " | " + charges.servicesMissingCostCentre());
                });

        // ===== D4 - IPD Bill ====================================================================================
        flowStep("Stage D4 - IPD Bill: search the MRN; bill lines equal the saved charges",
                "Billing > IPD Bill: MRN > search; the bill lists the same charges as IPD Charges",
                "Same charge lines as IPD Charges", true, bp, () -> {
                    if (!bill.navigateTo(BASE)) return Res.fail("IPD Bill screen not reached (" + page.url() + ")");
                    if (!retry(() -> bill.searchByMrn(mrn[0]))) return Res.fail("MRN " + mrn[0] + " did not load an admission on IPD Bill");
                    List<String> lines = bp.readChargeLines();
                    for (int i = 0; i < 15 && lines.isEmpty(); i++) { page.waitForTimeout(1000); lines = bp.readChargeLines(); }
                    if (lines.isEmpty()) return Res.fail("IPD Bill lists no charge lines for MRN " + mrn[0]);
                    List<String> names = new ArrayList<>();
                    for (String l : lines) names.add(com.kpj.pages.Flow_page.BillingIP.lineName(l));
                    List<String> only = new ArrayList<>();
                    for (String l : chargeLines[0]) if (!names.contains(com.kpj.pages.Flow_page.BillingIP.lineName(l))) only.add(com.kpj.pages.Flow_page.BillingIP.lineName(l));
                    if (!only.isEmpty()) return Res.fail("IPD Bill is missing charge line(s) that IPD Charges has: " + only);
                    return Res.pass(names.size() + " line(s) match IPD Charges | " + op.readBillTotals());
                });

        flowStep("Stage D4 - IPD Bill: generate the INTERIM bill",
                "Interim radio (Credit off), cost centres, Save; confirm \"proceed saving the interim bill\" (document D4.3)",
                "The interim bill is saved", false, bp, () -> {
                    bill.ensureAllCostCentresSet();
                    if (!bp.selectInterimBill()) return Res.fail("The Interim radio (PatientDetails.isdraftbill) was not found");
                    boolean ok = bill.saveAndValidate();
                    String closed = bp.closeOpenDialogs();
                    String detail = (bill.lastToast.isEmpty() ? "" : "toast: " + bill.lastToast) + (closed.isEmpty() ? "" : " | closed: " + closed);
                    if (!ok) return Res.fail("Interim bill NOT saved" + (bill.blockingMessage.isEmpty() ? "" : " | blocked: " + bill.blockingMessage) + " | " + detail);
                    return Res.pass("Interim bill saved | " + detail);
                });

        flowStep("Stage D4 - IPD Bill: generate the FINAL bill and pay in the payment window",
                "Reload the MRN, Final radio (not Interim, Credit off), cost centres, Save; answer the confirms and the payment window (document D4.4)",
                "The final bill is created (\"Bill saved successfully\") and paid in the payment window", true, bp, () -> {
                    if (!bill.navigateTo(BASE) || !retry(() -> bill.searchByMrn(mrn[0]))) return Res.fail("MRN " + mrn[0] + " could not be reloaded on IPD Bill");
                    page.waitForTimeout(2500);
                    bill.ensureAllCostCentresSet();
                    if (!op.selectFinalBill()) return Res.fail("The Final radio (PatientDetails.isdraftbill = 1) was not found");
                    String totals = op.readBillTotals();
                    boolean ok = op.saveFinalBill();
                    addSummary("Bill No.", op.lastBillNo.isEmpty() ? "(not shown on screen after save)" : op.lastBillNo);
                    addSummary("Net bill amount", op.lastNetAmount);
                    String pay = op.lastPaymentNote.isEmpty() ? "no payment window appeared on Save" : "payment window: " + op.lastPaymentNote;
                    finalBillSaved[0] = ok;
                    return ok ? Res.pass("Final bill saved" + (op.lastBillNo.isEmpty() ? "" : " - Bill No. " + op.lastBillNo) + " | " + totals + " | " + pay
                                    + (op.lastToast.isEmpty() ? "" : " | toast: " + op.lastToast))
                              : Res.fail("Final bill NOT created" + (op.lastToast.isEmpty() ? "" : " | toast: " + op.lastToast) + " | " + pay);
                });

        flowStep("Stage D4 - Receipt (SRS OOS-12.9): original receipt wording",
                "Open the receipt printed by the payment: heading 'Official Receipt', footer 'Kindly keep the original receipt properly, as it can only be printed once...'; no 'Duplicate'; 'Without Prejudice' only for a patient unblocked for Legal Proceedings",
                "Original receipt shows the OOS-12.9 heading and footer", false, bp, () -> {
                    String[] rc = op.readOriginalReceipt();
                    if (rc == null) return Res.manual("No receipt tab was opened by the payment, so the receipt wording (OOS-12.9) could not be read by the script - requires manual verification");
                    return "PASS".equals(rc[0]) ? Res.pass(rc[1]) : Res.fail(rc[1]);
                });

        // ===== D5 - IP-only support screens =====================================================================
        flowStep("Stage D5 - Bill Estimation: open Billing > Bill Estimation-Service",
                "Billing > Bill Estimation-Service: load the MRN (the screen alerts when the bill goes past the estimate; nothing is printed)",
                "The Bill Estimation screen opens for the admission", false, bp, () -> {
                    String why = bp.openBillEstimation(mrn[0]);
                    return why == null ? Res.pass(bp.lastEstimate) : Res.fail(why);
                });

        flowStep("Stage D5 - Time Trail: Patient Journey Time Trail shows the admission stamps",
                "IP > Inpatients > Occupancy List > Time Trail icon: read the time stamps (Registration / Bed Allocation ... Final Bill Generation)",
                "The Time Trail opens and \"Final Bill Generation\" is recorded after the final bill (document D4)", false, bp, () -> {
                    String why = bp.openTimeTrail(occ, mrn[0]);
                    if (why != null) return Res.fail(why);
                    addSummary("Time Trail", bp.lastTimeTrail);
                    bp.closeModal();
                    if (!bp.lastTimeTrail.contains("Registration / Bed Allocation: shown")) return Res.fail("Time Trail opened but has no Registration / Bed Allocation stamp | " + bp.lastTimeTrail);
                    return finalBillSaved[0] && !bp.timeTrailHasFinalBill()
                            ? Res.fail("The final bill was saved but the Time Trail has no \"Final Bill Generation\" stamp | " + bp.lastTimeTrail)
                            : Res.pass(bp.lastTimeTrail);
                });

        flowStep("Stage D5 - Additional Deposit Request: ON HOLD (workflow document v4, stage E3)",
                "Not run: the document puts the Additional Deposit Request steps on hold because the enhancement did not meet the requirement",
                "Not executed until the document owner lifts the hold", false, bp,
                () -> Res.manual("On hold per workflow document v4 (review comment on stage E3) - not executed"));

        // ===== Final validation =================================================================================
        flowStep("Final Billing Validation",
                "Billing > Payment Settlement > Self for the MRN: no outstanding bill, the payment / receipt is listed",
                "Final bill saved; nothing outstanding for Bill No. " + (op.lastBillNo.isEmpty() ? "(n/a)" : op.lastBillNo), true, bp, () -> {
                    if (!finalBillSaved[0]) return Res.fail("The final IPD bill was not saved");
                    String[] v = op.verifySettled(mrn[0], op.lastBillNo);
                    return "PASS".equals(v[0]) ? Res.pass(v[1]) : Res.fail(v[1]);
                });

        // ===== Overall ==========================================================================================
        boolean allOk = failedStage.isEmpty();
        stageNo++;
        String overall = allOk
                ? "All stages passed: " + String.join(" ; ", stageLog)
                : "Flow FAILED at \"" + failedStage + "\" - " + failedReason + (blocked ? " (later stages skipped)" : " (non-critical: flow continued)");
        step((byte[]) null, "Overall End-to-End Flow", "Billing IP: Admission > Occupancy List > EMR > Orders > Charges > Bill > Support screens > Validation",
                "Every step of the flow passes", overall, allOk ? "PASS" : "FAIL");

        addSummary("Failed at", allOk ? "-" : failedStage);
        addSummary("Failure reason", allOk ? "-" : failedReason);
        addSummary("Final Result", allOk ? "PASS - Billing IP flow completed for MRN " + mrn[0] + ", Bill No. " + op.lastBillNo
                                          : "FAIL - " + failedStage);
    }

    /** The patient / grid fills a moment after a search: try it up to 4 times before giving up. */
    private boolean retry(java.util.function.Supplier<Boolean> search) {
        for (int i = 0; i < 4; i++) {
            if (Boolean.TRUE.equals(search.get())) return true;
            page.waitForTimeout(2500);
        }
        return false;
    }

    private Res acceptRes(com.kpj.pages.Flow_page.BillingOP op, String route, String mrn, String what, String groupRe) {
        int n = op.acceptWorkOrders(route, mrn, groupRe);
        if (n == -1) return Res.fail(what + " Work Order List screen not reached");
        if (n == -2) return Res.fail("No " + what + " order found for MRN " + mrn + " (was it Finalized in EMR?)");
        if (n < 0) return Res.fail(what + " Accept button not found / not clickable");
        String t = op.lastToast.toLowerCase();
        boolean accepted = (t.contains("accept") || t.contains("success")) && !t.contains("please") && !t.contains("error");
        return accepted ? Res.pass(what + " work order accepted for MRN " + mrn + " (" + n + " row(s)) | toast: " + op.lastToast)
                        : Res.fail(what + " Accept was not confirmed (" + n + " row(s) selected) | toast: " + (op.lastToast.isEmpty() ? "(none)" : op.lastToast));
    }
}
