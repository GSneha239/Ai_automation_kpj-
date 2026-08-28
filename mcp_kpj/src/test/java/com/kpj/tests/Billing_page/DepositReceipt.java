package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// The page object is also named DepositReceipt — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Deposit / Receipt</b>.
 *
 * <ol>
 *   <li>Open <b>Billing</b> → <b>Deposit / Receipt</b>.</li>
 *   <li>Fill the mandatory Add Deposit/Receipt details (Visit, Type, Against, Cash Counter, Amount).</li>
 *   <li>Select the <b>Cash</b> checkbox.</li>
 *   <li>Click <b>Save</b> → the official receipt report opens in a new tab.</li>
 * </ol>
 */
public class DepositReceipt extends DevHisBase {

    public DepositReceipt() { super("Billing_DepositReceipt"); }

    public static void main(String[] args) {
        DepositReceipt t = new DepositReceipt();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - Deposit / Receipt", "Billing > Deposit / Receipt",
                "Open Deposit/Receipt, fill the mandatory Add Deposit/Receipt fields (Visit, Type, Against, Cash Counter, Amount), tick Cash, Save; the receipt report opens in a new tab.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Billing_page.DepositReceipt dr =
                new com.kpj.pages.Billing_page.DepositReceipt(page);

        // 1) Navigate
        boolean onScreen = dr.navigateTo(BASE);
        step(page, "Open Deposit / Receipt screen", "Billing -> Deposit / Receipt",
                "The Deposit / Receipt screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Get candidate patients from the deposit-records grid (same screen), then find one with an open visit.
        java.util.List<String> candidates = dr.candidateMrnsFromGrid(6);
        step(page, "Find candidate patients", "Search the Deposit/Receipt records grid (last 6 months) for patients",
                "At least one patient MRN is found", candidates.isEmpty() ? "No patients in the records grid" : candidates.size() + " candidate MRN(s): " + candidates.subList(0, Math.min(6, candidates.size())),
                candidates.isEmpty() ? "FAIL" : "PASS");
        if (candidates.isEmpty()) { addSummary("Result", "No candidate patients available"); return; }

        // Iterate candidates until one's Visit dropdown populates (an open visit to deposit against).
        String mrn = "";
        int visits = 0;
        int tried = 0;
        for (String cand : candidates) {
            tried++;
            int v = dr.searchPatient(cand);
            if (v > 0) { mrn = cand; visits = v; break; }
            if (tried >= 8) break;   // cap the search
        }
        step(page, "Search patient by MRN (open visit)",
                "For each candidate: set Reg Type = ALL, enter MRN, search (SearchPatientByMRNo), check the Visit dropdown",
                "A patient with an open visit is found and its Visit(s) load",
                visits > 0 ? "MRN " + mrn + " -> Visit dropdown populated (" + visits + " visit(s)) after " + tried + " tried"
                        : "None of the " + tried + " candidate patients had an open visit to deposit against",
                visits > 0 ? "PASS" : "FAIL");
        if (visits <= 0) { addSummary("Result", "No candidate patient had an open visit"); return; }
        String fill = dr.fillMandatory();
        boolean fillOk = !fill.contains("Type=(no") && !fill.contains("CashCounter=(no");
        step(page, "Fill mandatory details", "Fill only the mandatory (*) fields: Deposit/Receipt Type, Deposit/Receipt Against, Cash Counter, Payor (auto), Amount",
                "The mandatory Add Deposit/Receipt fields are filled", fill, fillOk ? "PASS" : "FAIL");

        // 3) Select Cash
        String cash = dr.selectCash();
        step(page, "Select Cash checkbox", "Tick the Cash checkbox (TrueValChk)",
                "The Cash checkbox is selected", cash, cash.toLowerCase().contains("cash") ? "PASS" : "FAIL");

        // 4) Save -> report opens in a new tab
        Page report = dr.saveAndOpenReport();
        boolean reportOpened = report != null && !report.isClosed();
        String actual = reportOpened
                ? "Receipt report opened in a new tab: " + report.url() + (dr.lastToast.isEmpty() ? "" : " | toast: " + dr.lastToast)
                : "No report tab opened" + (dr.lastToast.isEmpty() ? "" : " — server said: \"" + dr.lastToast + "\"");
        step(reportOpened ? report : page, "Click Save & receipt report (new tab)",
                "Click Save (fnSaveadvance); the official receipt report opens in a new tab",
                "The receipt report opens in a new tab", actual, reportOpened ? "PASS" : "FAIL");

        try { if (report != null && report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Visit", dr.lastVisit);
        addSummary("Amount", dr.lastAmount);
        addSummary("Result", reportOpened ? "Deposit saved; receipt opened" : (dr.lastToast.isEmpty() ? "Not confirmed" : dr.lastToast));
    }
}
