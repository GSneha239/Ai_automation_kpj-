package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.Map;

// The page object is also named DepositReceipt — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Deposit / Receipt</b> &gt; Add Deposit/Receipt.
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>Deposit / Receipt</b>.</li>
 *   <li>Find candidate patients in the deposit list and look one up by MRN until a Visit loads
 *       (A01 MRN validation / "Patient Not Admitted", A02 Visit loads).</li>
 *   <li>A03 Type = Patient fills Payor and locks the Type; A04 the starred fields, Against choices and defaults.</li>
 *   <li>Fill the mandatory Add Deposit/Receipt details (Type, Payor, Against, Cash Counter, Amount; Restrict consumption is
 *       pre-ticked) — a missing Cash Counter list is reported as BLOCKED.</li>
 *   <li>Select the <b>Cash</b> checkbox.</li>
 *   <li>Click <b>Save</b> → the official receipt report opens in a new tab. <b>Creates a real deposit, so it only runs with
 *       {@code -Ddevhis.deposit.save=true}</b>; otherwise the step is recorded as SKIPPED.</li>
 *   <li>A05 Clear resets the form (client-side only).</li>
 * </ol>
 */
public class DepositReceipt extends DevHisBase {

    public DepositReceipt() { super("Billing_DepositReceipt"); }

    public static void main(String[] args) {
        DepositReceipt t = new DepositReceipt();
        try { t.run(); } finally { t.stop(); }
        if (t.navigationFailure != null) throw new IllegalStateException("Deposit / Receipt screen not reached - " + t.navigationFailure);
    }

    /** JUnit entry point. run() never throws (it records problems as report steps), so a navigation failure is re-raised
     *  here, after the report is written, to make JUnit / Surefire count the test as failed. */
    @org.junit.jupiter.api.Test
    void execute() {
        try { run(); } finally { stop(); }
        if (navigationFailure != null)
            org.junit.jupiter.api.Assertions.fail("Deposit / Receipt screen not reached - " + navigationFailure
                    + " (report: " + reportDir.resolve("Billing_DepositReceipt.html").toAbsolutePath() + ")");
    }

    /** Set when the Open step fails; null otherwise. */
    String navigationFailure = null;

    /** Save creates a real deposit/receipt record — opt-in only. */
    private static final boolean SAVE_ALLOWED = Boolean.getBoolean("devhis.deposit.save");

    @Override
    protected void body() {
        meta("Billing - Deposit / Receipt", "Billing > Deposit / Receipt",
                "Open Deposit/Receipt, look a patient up by MRN, check the Add Deposit/Receipt form (A01-A05), fill the mandatory fields"
                        + " (Visit, Type, Payor, Against, Cash Counter, Amount), tick Cash, Save; the receipt report opens in a new tab."
                        + (SAVE_ALLOWED ? "" : " Save is NOT executed in this run (read-only; pass -Ddevhis.deposit.save=true to save)."));

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.DepositReceipt dr =
                new com.kpj.pages.Billing_page.DepositReceipt(page);

        // 1) Navigate
        boolean onScreen = dr.navigateTo(BASE);
        boolean addForm = onScreen && dr.waitForAddForm(30000);
        if (!onScreen) navigationFailure = dr.lastNavDiagnostics.isEmpty() ? "navigateTo() returned false" : dr.lastNavDiagnostics;
        step(page, "Open Deposit / Receipt screen", "Billing -> Deposit / Receipt",
                "The Deposit / Receipt screen is shown with the Add Deposit/Receipt form",
                onScreen ? "Opened " + page.url() + " | Add form rendered=" + addForm : "Did NOT reach the screen: " + navigationFailure,
                onScreen && addForm ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // A01 (part 1) — MRN lookup with no MRN entered
        dr.startToastCapture();
        dr.searchPatient("");
        page.waitForTimeout(1200);
        List<String> emptyMrnToasts = dr.capturedToasts();

        // 2) Get candidate patients from the deposit-records grid (same screen), then find one with an open visit.
        //    The From Date box is now ticked so the 6-month range really applies; with no hits, retry without a date filter.
        java.util.List<String> candidates = dr.candidateMrnsFromGrid(6);
        String range = "last 6 months";
        if (candidates.isEmpty()) { candidates = dr.candidateMrnsFromGrid(0); range = "no date filter (none in the last 6 months)"; }
        step(page, "Find candidate patients", "Search the Deposit/Receipt records grid (Reg. Type IPD, " + range + ") for patients",
                "At least one patient MRN is found", candidates.isEmpty() ? "No patients in the records grid" : candidates.size() + " candidate MRN(s): " + candidates.subList(0, Math.min(6, candidates.size())),
                candidates.isEmpty() ? "FAIL" : "PASS");
        if (candidates.isEmpty()) { addSummary("Result", "No candidate patients available"); return; }

        // Iterate candidates until one's Visit dropdown populates (an open visit to deposit against); keep the first
        // "Patient Not Admitted" answer as A01 evidence.
        String mrn = "";
        String notAdmittedMrn = "", notAdmittedMsg = "";
        int visits = 0;
        int tried = 0;
        for (String cand : candidates) {
            tried++;
            dr.startToastCapture();
            int v = dr.searchPatient(cand);
            if (v > 0) { mrn = cand; visits = v; break; }
            if (notAdmittedMrn.isEmpty()) {
                page.waitForTimeout(800);
                for (String t : dr.capturedToasts()) if (t.toLowerCase().contains("not admitted")) { notAdmittedMrn = cand; notAdmittedMsg = t; break; }
            }
            if (tried >= 8) break;   // cap the search
        }

        // A01 — MRN lookup messages
        boolean emptyOk = emptyMrnToasts.stream().anyMatch(t -> t.toLowerCase().contains("please enter mrno"));
        String a01 = "Empty MRN -> " + (emptyMrnToasts.isEmpty() ? "no message" : emptyMrnToasts)
                + " | not-admitted IPD patient -> " + (notAdmittedMrn.isEmpty() ? "none met among the " + tried + " candidate(s) tried" : "MRN " + notAdmittedMrn + ": '" + notAdmittedMsg + "'");
        step(page, "A01 MRN lookup messages", "Click the MRN search icon with no MRN; look up IPD candidates by MRN",
                "No MRN -> 'Please Enter MrNo!'; a patient who is not admitted (Reg. Type IPD) -> 'Patient Not Admitted !!!!'",
                a01, !emptyOk ? "FAIL" : notAdmittedMrn.isEmpty() ? "MANUAL" : "PASS");

        // A02 — the existing "open visit" search step
        step(page, "A02 Search patient by MRN (open visit)",
                "For each candidate: enter the MRN (Reg. Type left at IPD), search (SearchPatientByMRNo), check the Visit dropdown",
                "A patient with an open visit is found and its Visit(s) load",
                visits > 0 ? "MRN " + mrn + " -> Visit dropdown populated (" + visits + " visit(s)), selected '" + dr.selectedText("advance.OPDNo") + "' after " + tried + " tried"
                        : "BLOCKED: none of the " + tried + " candidate patients had an open visit to deposit against",
                visits > 0 ? "PASS" : "SKIPPED");
        if (visits <= 0) { addSummary("Result", "BLOCKED - no candidate patient had an open visit"); return; }

        // A03 — Type = Patient fills Payor and locks the Type
        String payor = dr.selectAddType("Patient");
        Map<String, Object> f3 = dr.addFormState();
        boolean a03 = "Patient".equals(f3.get("type")) && "self".equalsIgnoreCase(payor) && Boolean.TRUE.equals(f3.get("typeDisabled"));
        step(page, "A03 Deposit/Receipt Type = Patient", "Choose Deposit/Receipt Type = Patient in Add Deposit/Receipt",
                "Payor shows 'self' and the Type is locked (Reg. Type IPD)",
                "Type='" + f3.get("type") + "' locked=" + f3.get("typeDisabled") + " | Payor='" + payor + "' read-only=" + f3.get("payorDisabled"),
                a03 ? "PASS" : "FAIL");

        // A04 — starred fields, Against choices, defaults
        Map<String, Object> f4 = dr.addFormState();
        List<String> choices = dr.againstChoices();
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        @SuppressWarnings("unchecked")
        List<String> req = (List<String>) f4.getOrDefault("required", new java.util.ArrayList<String>());
        boolean a04 = req.containsAll(java.util.Arrays.asList("Deposit/Receipt Type", "Payor", "Deposit/Receipt Against", "Cash Counter", "Amount"))
                && choices.containsAll(DepositReceiptList.EXPECTED_AGAINST)
                && Boolean.TRUE.equals(f4.get("restrictChecked")) && Boolean.TRUE.equals(f4.get("restrictDisabled"))
                && today.equals(f4.get("date"));
        step(page, "A04 Add form fields and defaults", "Read the starred labels, the Deposit/Receipt Against choices, Restrict consumption and Date",
                "Starred: Type, Payor, Against, Cash Counter, Amount (+ Restrict consumption, ticked and locked); Against offers " + DepositReceiptList.EXPECTED_AGAINST + "; Date = today",
                "Starred=" + req + " | Against choices=" + choices + " | Restrict ticked=" + f4.get("restrictChecked") + " locked=" + f4.get("restrictDisabled")
                        + " | Date=" + f4.get("date") + " | Cash Counter options=" + f4.get("cashCounterOptions"),
                a04 ? "PASS" : "FAIL");

        // 3) Fill mandatory details — every starred field is checked afterwards
        String fill = dr.fillMandatory();
        List<String> missing = dr.missingMandatory();
        boolean counterBlocked = dr.cashCounterOptionCount() <= 0;
        String fillStatus = missing.isEmpty() ? "PASS" : (counterBlocked && missing.size() == 1 && missing.contains("Cash Counter")) ? "SKIPPED" : "FAIL";
        step(page, "Fill mandatory details", "Fill only the mandatory (*) fields: Deposit/Receipt Type, Payor (from Type), Deposit/Receipt Against (ui-select), Cash Counter, Amount; Restrict consumption is pre-ticked",
                "Every mandatory Add Deposit/Receipt field holds a value",
                ("SKIPPED".equals(fillStatus) ? "BLOCKED: Cash Counter list is empty for this user | " : "") + fill
                        + (missing.isEmpty() ? "" : " | still empty: " + missing),
                fillStatus);

        // 4) Select Cash
        String cash = dr.selectCash();
        step(page, "Select Cash checkbox", "Tick the Cash checkbox (TrueValChk)",
                "The Cash checkbox is selected", cash, cash.toLowerCase().contains("cash") ? "PASS" : "FAIL");

        // 5) Save -> report opens in a new tab  (creates a real record: opt-in only, and only with every field filled)
        boolean reportOpened = false;
        if (!SAVE_ALLOWED) {
            step(page, "Click Save & receipt report (new tab)", "Click Save (fnSaveadvance); the official receipt report opens in a new tab",
                    "The receipt report opens in a new tab",
                    "Not executed - Save creates a real deposit/receipt; this run is read-only (pass -Ddevhis.deposit.save=true to save)", "SKIPPED");
        } else if (!missing.isEmpty()) {
            step(page, "Click Save & receipt report (new tab)", "Click Save (fnSaveadvance); the official receipt report opens in a new tab",
                    "The receipt report opens in a new tab", "BLOCKED: not saved - mandatory field(s) still empty: " + missing, "SKIPPED");
        } else {
            Page report = dr.saveAndOpenReport();
            reportOpened = report != null && !report.isClosed();
            String actual = reportOpened
                    ? "Receipt report opened in a new tab: " + report.url() + (dr.lastToast.isEmpty() ? "" : " | toast: " + dr.lastToast)
                    : "No report tab opened" + (dr.lastToast.isEmpty() ? "" : " — server said: \"" + dr.lastToast + "\"");
            step(reportOpened ? report : page, "Click Save & receipt report (new tab)",
                    "Click Save (fnSaveadvance); the official receipt report opens in a new tab",
                    "The receipt report opens in a new tab", actual, reportOpened ? "PASS" : "FAIL");

            try { if (report != null && report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
            try { page.bringToFront(); } catch (Exception ignore) { }
        }

        // A05 — Clear resets the form (client-side CancelForm(); no server call)
        dr.clearForm();
        Map<String, Object> f5 = dr.addFormState();
        Map<String, Object> filters = dr.filterState();
        boolean a05 = String.valueOf(filters.get("mrn")).isEmpty() && String.valueOf(f5.get("visit")).isEmpty()
                && String.valueOf(f5.get("type")).matches("^-*\\s*[Ss]elect-*$|^$") && String.valueOf(f5.get("payor")).isEmpty()
                && ((List<?>) f5.getOrDefault("against", new java.util.ArrayList<>())).isEmpty() && String.valueOf(f5.get("amount")).isEmpty()
                && dr.gridDataCount() == 0;
        step(page, "A05 Clear resets the form", "Click Clear (CancelForm)",
                "MRN, Visit, Type, Payor, Against and Amount are emptied and the list is cleared",
                "MRN='" + filters.get("mrn") + "' Visit='" + f5.get("visit") + "' Type='" + f5.get("type") + "' Payor='" + f5.get("payor")
                        + "' Against=" + f5.get("against") + " Amount='" + f5.get("amount") + "' grid rows=" + dr.gridDataCount()
                        + " | Restrict consumption after Clear: ticked=" + f5.get("restrictChecked") + " locked=" + f5.get("restrictDisabled"),
                a05 ? "PASS" : "FAIL");

        addSummary("Visit", dr.lastVisit);
        addSummary("Amount", dr.lastAmount);
        addSummary("Result", !SAVE_ALLOWED ? "Read-only run - Save not executed"
                : reportOpened ? "Deposit saved; receipt opened" : (dr.lastToast.isEmpty() ? "Not confirmed" : dr.lastToast));
    }
}
