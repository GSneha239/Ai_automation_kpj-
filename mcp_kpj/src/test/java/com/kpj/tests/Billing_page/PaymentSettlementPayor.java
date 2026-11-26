package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Payment Settlement &gt; <b>Payor</b> — READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_M12_PaymentSettlement_Payor_v1.xlsx. Seven scenarios are automated, each in its own
 * method and its own report row: open the screen, search-panel controls, search without a payor, search a payor with no
 * bills, search AIA BHD. (EB), the amount columns of bill DN0000000080, and selecting that bill.</p>
 *
 * <p><b>Financial safety.</b> Nothing is settled, saved, allocated or confirmed. The write guard of
 * {@code ReadOnlyScreen} is installed BEFORE login and aborts any IUD / Save / Insert / Update / Delete request in the
 * browser; this class never clicks Settle Bill ({@code fnIUDBill()}), Demand Letter, Save or a payment window. The only
 * state change is ticking one bill checkbox (a client-side total) and unticking it again. Any write the application
 * itself attempts is listed under "Blocked write calls" in the report.</p>
 *
 * <p>Failure reasons say which side failed: {@code APPLICATION DEFECT} (the screen did something wrong),
 * {@code AUTOMATION / LOCATOR FAILURE} (the script could not drive or read the screen), or BLOCKED for missing test data.</p>
 *
 * <p>Data (override with -D): {@code pay.payor} (default "AIA BHD. (EB)"), {@code pay.bill} (default "DN0000000080"),
 * {@code pay.billBaseline} (default 16, the count seen manually - a different count is reported, not failed),
 * {@code pay.emptyPayor} (a payor with no bills; default: the first such payor found in the list).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Billing_page.PaymentSettlementPayor" -Ddevhis.postrun=off
 *   report: test-output/reports/Billing_PaymentSettlement_Payor.html
 * </pre>
 */
public class PaymentSettlementPayor extends DevHisBase {

    private static final String PAYOR = System.getProperty("pay.payor", "AIA BHD. (EB)");
    private static final String BILL = System.getProperty("pay.bill", "DN0000000080");
    private static final int BILL_BASELINE = Integer.getInteger("pay.billBaseline", 16);
    private static final String EMPTY_PAYOR = System.getProperty("pay.emptyPayor", "");
    private static final List<String> EXAMPLE_BILLS = List.of("DN0000000080", "OPB000000413", "IPB000000163");

    /** Expected amounts of {@link #BILL}, by bill-table column header (from the manual test case). */
    private static final Map<String, String> EXPECTED_AMOUNTS = new LinkedHashMap<>();
    static {
        EXPECTED_AMOUNTS.put("Total Bill Amount", "120.00");
        EXPECTED_AMOUNTS.put("Total Discount", "0.00");
        EXPECTED_AMOUNTS.put("Tax", "0.00");
        EXPECTED_AMOUNTS.put("Net Amount", "120.00");
        EXPECTED_AMOUNTS.put("Company Pay", "120.00");
        EXPECTED_AMOUNTS.put("Company Balance", "120.00");
    }

    private static final String APP_DEFECT = "FAILURE REASON - APPLICATION DEFECT: ";
    private static final String AUTOMATION = "FAILURE REASON - AUTOMATION / LOCATOR FAILURE: ";

    private com.kpj.pages.Billing_page.PaymentSettlementPayor pay;
    private int pass, fail, skipped, blocked;

    public PaymentSettlementPayor() { super("Billing_PaymentSettlement_Payor"); }

    public static void main(String[] args) {
        PaymentSettlementPayor t = new PaymentSettlementPayor();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- per-test-case plumbing --------------------------------------------------

    private interface Check { String[] run() throws Exception; }

    /** One test case = one report row: {STATUS, actual}. An exception is an automation/locator failure, not a defect. */
    private void testCase(String name, String scenario, String steps, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = AUTOMATION + "exception - " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, name, "Scenario: " + scenario + " | Steps: " + steps, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] passed(String actual) { return new String[]{"PASS", actual}; }
    private static String[] defect(String actual) { return new String[]{"FAIL", APP_DEFECT + actual}; }
    private static String[] automationFailure(String actual) { return new String[]{"FAIL", AUTOMATION + actual}; }
    private static String[] blockedData(String actual) { return new String[]{"BLOCKED", "TEST DATA NOT AVAILABLE: " + actual}; }

    /** Fresh screen reached through Billing; null = ready, otherwise the automation failure to report. */
    private String[] freshScreen() {
        if (!pay.reopen()) return automationFailure("Billing > Payment Settlement > Payor did not open (at " + page.url() + ")");
        return null;
    }

    /** Fresh screen with {@link #PAYOR} searched (Reg. Type All, Bill Type ALL); null = bills listed. */
    private String[] payorSearched() {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        pay.clickRegType("2");
        if (pay.pickOption(com.kpj.pages.Billing_page.PaymentSettlementPayor.PAYOR, PAYOR).isEmpty())
            return blockedData("payor '" + PAYOR + "' is not in the Payor list on " + BASE);
        int status = pay.search();
        if (status == 0) return automationFailure("Search sent no bill-list request");
        if (status != 200) return defect("bill-list API answered HTTP " + status);
        return null;
    }

    // ---- test body -----------------------------------------------------------------

    @Override
    protected void body() {
        meta("Billing - Payment Settlement - Payor (read-only)",
                "Billing > Payment Settlement > Payor",
                "Read-only checks from TestCases_M12_PaymentSettlement_Payor_v1.xlsx. Write calls are aborted in the browser; "
                        + "Settle Bill, Demand Letter, Save and payment confirmation are never clicked. Payor under test: " + PAYOR
                        + ", bill " + BILL + ". APPLICATION DEFECT = the screen is wrong; AUTOMATION / LOCATOR FAILURE = the script "
                        + "could not drive or read it; BLOCKED = test data missing on this environment.");

        String user = cfgValue("devhis.user", "DEVHIS_USER"), pass_ = cfgValue("devhis.pass", "DEVHIS_PASS");
        if (user.isEmpty() || pass_.isEmpty()) {
            step("Credentials", "DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass)", "Credentials supplied by the environment",
                    "Not set - nothing was run (no built-in account is used)", "BLOCKED");
            blocked++;
            addSummary("Test cases", "not run - credentials missing");
            return;
        }

        pay = new com.kpj.pages.Billing_page.PaymentSettlementPayor(page);
        pay.installGuards();                                   // BEFORE login: every write request is aborted from here on
        new LoginPage(page).login(BASE, user, pass_);
        step("Login", user + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");
        boolean guardOn = pay.guardVerified();
        step("Read-only guard", "Probe a fake IUD request", "The browser aborts write requests",
                guardOn ? "Write guard active (probe aborted)" : "Write guard could NOT be verified - bill selection will be skipped",
                guardOn ? "PASS" : "FAIL");

        openPaymentSettlementPayor();
        verifySearchPanelControls();
        searchWithoutSelectingPayor();
        searchPayorWithNoBills();
        searchAiaPayorBills();
        verifyBillAmountColumns();
        selectBillAndVerifyAmountToBeSettled(guardOn);

        addSummary("Mode", "READ-ONLY - write calls aborted in the browser; Settle Bill / Demand Letter / Save never clicked");
        addSummary("Payor / bill", PAYOR + " / " + BILL);
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail
                + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("Blocked write calls", pay.blockedWrites.isEmpty() ? "none" : String.join(" ; ", pay.blockedWrites));
        addSummary("Failed API responses", pay.apiFailures.isEmpty() ? "none" : String.join(" ; ", pay.apiFailures));
        addSummary("Console errors", pay.consoleErrors.isEmpty() ? "none" : String.join(" ; ", pay.consoleErrors));
    }

    // ---- the seven scenarios -------------------------------------------------------

    private void openPaymentSettlementPayor() {
        testCase("Open Payment Settlement - Payor",
                "The screen opens from the Billing menu without an application error",
                "Billing > Payment Settlement > Payor (sidebar); read the page header and any error shown",
                "Screen opens with its Search button; no application error; page title 'Payment Settlement - Company'",
                () -> {
                    int apiFailuresBefore = pay.apiFailures.size();
                    if (!pay.reopen()) return automationFailure("Billing > Payment Settlement > Payor did not open (at " + page.url() + ")");
                    String title = pay.headerText();
                    String error = pay.visibleErrorText();
                    String serverError = DevHisBase.reportPageError(page);
                    List<String> newApiFailures = new ArrayList<>(pay.apiFailures.subList(apiFailuresBefore, pay.apiFailures.size()));
                    newApiFailures.removeIf(f -> !f.contains("/api/"));
                    boolean titleOk = title.toLowerCase().contains("payment settlement - company");
                    String seen = "Opened " + page.url() + "; header '" + title + "'; browser tab '" + page.title() + "'";
                    if (!error.isEmpty() || !serverError.isEmpty() || !newApiFailures.isEmpty())
                        return defect(seen + "; error shown: '" + error + serverError + "'; failed API calls: " + newApiFailures);
                    if (!titleOk) return defect(seen + " - title is not 'Payment Settlement - Company'");
                    return passed(seen + "; no application error");
                });
    }

    private void verifySearchPanelControls() {
        testCase("Verify the search panel",
                "Every search-panel control is present",
                "Fresh screen; check From Date checkbox, From Date, To Date, Reg. Type, MRN, Find Patient, Search Patient, Payor, "
                        + "Bill Type, Search, Demand Letter",
                "All eleven controls are displayed",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = pay.searchPanelPresence();
                    List<String> missing = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missing.add(k); });
                    List<String> labels = pay.visibleLabels();
                    String seen = "Present: " + present + " | labels: " + labels;
                    return missing.isEmpty() ? passed(seen) : defect("missing " + missing + " | " + seen);
                });
    }

    private void searchWithoutSelectingPayor() {
        testCase("Search without selecting Payor",
                "Search is refused until a payor is chosen",
                "Fresh screen; leave Payor empty; click Search",
                "'Please Select The Payor' is displayed; no bills are listed",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    int callsBefore = countBillListCalls();
                    pay.clickSearchOnly();
                    List<String> messages = pay.waitForAlert(4000);
                    int bills = pay.billRows();
                    boolean requested = countBillListCalls() > callsBefore;
                    boolean message = messages.stream().anyMatch(m -> m.toLowerCase().contains("please select the payor"));
                    String seen = "Messages " + messages + "; bills listed " + Math.max(bills, 0) + "; bill-list request sent " + requested;
                    if (bills < 0) return automationFailure("bill table not found | " + seen);
                    return message && bills == 0 && !requested ? passed(seen) : defect(seen);
                });
    }

    private void searchPayorWithNoBills() {
        testCase("Search a payor with no bills",
                "A payor without outstanding bills returns an empty result with a message",
                EMPTY_PAYOR.isEmpty() ? "Fresh screen; take the first payor in the list whose search returns no bills; click Search"
                        : "Fresh screen; Payor = " + EMPTY_PAYOR + "; click Search",
                "'No records found' is displayed; the bill grid stays empty",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    pay.clickRegType("2");
                    String payor;
                    List<String> messages;
                    if (!EMPTY_PAYOR.isEmpty()) {
                        if (pay.pickOption(com.kpj.pages.Billing_page.PaymentSettlementPayor.PAYOR, EMPTY_PAYOR).isEmpty())
                            return blockedData("payor '" + EMPTY_PAYOR + "' is not in the Payor list");
                        int status = pay.search();
                        if (status != 200) return status == 0 ? automationFailure("Search sent no bill-list request") : defect("bill-list API HTTP " + status);
                        payor = EMPTY_PAYOR;
                        messages = pay.waitForAlert(3000);
                    } else {
                        String[] found = pay.findPayorWithoutBills(PAYOR, 15);
                        if (found == null) return blockedData("none of the first 15 payors returned an empty bill list");
                        payor = found[0];
                        messages = pay.waitForAlert(3000);
                    }
                    int bills = pay.billRows();
                    boolean message = messages.stream().anyMatch(m -> m.toLowerCase().contains("no record"));
                    String seen = "Payor '" + payor + "'; bills listed " + bills + "; messages " + messages;
                    if (bills != 0) return defect(seen + " - bills listed for a payor expected to have none");
                    return message ? passed(seen) : defect(seen + " - empty result but no 'No records found' message");
                });
    }

    private void searchAiaPayorBills() {
        testCase("Search AIA BHD. (EB)",
                "Outstanding bills are listed for the payor",
                "Fresh screen; Payor = " + PAYOR + "; Reg. Type = All; Bill Type = ALL; click Search",
                "Bill records are displayed (baseline " + BILL_BASELINE + " bills seen manually, e.g. " + EXAMPLE_BILLS + ")",
                () -> {
                    String[] notReady = payorSearched();
                    if (notReady != null) return notReady;
                    String billType = pay.selectedText("PatientData.billtype");
                    List<String> bills = pay.billNumbers();
                    List<String> examplesFound = new ArrayList<>(), examplesMissing = new ArrayList<>();
                    for (String b : EXAMPLE_BILLS) (bills.contains(b) ? examplesFound : examplesMissing).add(b);
                    String seen = "Bill Type '" + billType + "'; " + bills.size() + " bills listed"
                            + (bills.size() == BILL_BASELINE ? " (matches baseline)" : " (baseline " + BILL_BASELINE + " - live data has changed; not a failure)")
                            + "; examples found " + examplesFound + (examplesMissing.isEmpty() ? "" : ", not listed " + examplesMissing)
                            + "; first bills " + bills.subList(0, Math.min(5, bills.size()));
                    if (bills.isEmpty()) return defect(seen + " - no bills listed for " + PAYOR);
                    return passed(seen);
                });
    }

    private void verifyBillAmountColumns() {
        testCase("Verify bill amount columns and calculations",
                "The amounts of bill " + BILL + " sit in the right columns and agree with each other",
                "Search " + PAYOR + "; read bill " + BILL + " by column header: Total Bill Amount, Total Discount, Tax, Net Amount, "
                        + "Company Pay, Company Balance",
                "Total Bill Amount 120.00, Total Discount 0.00, Tax 0.00, Net Amount 120.00, Company Pay 120.00, Company Balance 120.00; "
                        + "Net = Total - Discount + Tax",
                () -> {
                    String[] notReady = payorSearched();
                    if (notReady != null) return notReady;
                    Map<String, String> row = pay.billRow(BILL);
                    if (row.isEmpty()) return blockedData("bill " + BILL + " is not listed for " + PAYOR + " on " + BASE);
                    List<String> wrong = new ArrayList<>(), missingColumns = new ArrayList<>();
                    StringBuilder shown = new StringBuilder();
                    for (Map.Entry<String, String> e : EXPECTED_AMOUNTS.entrySet()) {
                        String actual = row.get(e.getKey());
                        if (actual == null) { missingColumns.add(e.getKey()); continue; }
                        shown.append(e.getKey()).append('=').append(actual).append("; ");
                        if (Math.abs(amount(actual) - amount(e.getValue())) > 0.005) wrong.add(e.getKey() + " shows " + actual + " (expected " + e.getValue() + ")");
                    }
                    if (!missingColumns.isEmpty()) return automationFailure("columns not found in the bill table: " + missingColumns + " | row " + row);
                    double total = amount(row.get("Total Bill Amount")), discount = amount(row.get("Total Discount")),
                            tax = amount(row.get("Tax")), net = amount(row.get("Net Amount"));
                    boolean consistent = Math.abs(total - discount + tax - net) < 0.005;
                    String seen = "Bill " + BILL + ": " + shown + "Net = Total - Discount + Tax: " + (consistent ? "consistent" : "NOT consistent");
                    if (!wrong.isEmpty()) return defect(String.join("; ", wrong) + " | " + seen);
                    return consistent ? passed(seen) : defect(seen);
                });
    }

    private void selectBillAndVerifyAmountToBeSettled(boolean guardOn) {
        testCase("Select a bill and verify Amount to be settled",
                "Ticking a bill fills 'Amount to be settled' with its Company Balance (client side; nothing is settled)",
                "Search " + PAYOR + "; tick bill " + BILL + "; read 'Amount to be settled'; untick the bill again",
                "'Amount to be settled' shows 120.00 = the bill's Company Balance; it clears after unticking",
                () -> {
                    if (!guardOn) return new String[]{"SKIPPED", "Write guard could not be verified, so no bill was ticked"};
                    String[] notReady = payorSearched();
                    if (notReady != null) return notReady;
                    Map<String, String> row = pay.billRow(BILL);
                    if (row.isEmpty()) return blockedData("bill " + BILL + " is not listed for " + PAYOR + " on " + BASE);
                    String balance = row.getOrDefault("Company Balance", "");
                    int writesBefore = pay.blockedWrites.size();
                    String settled, rowAmount, afterUntick;
                    boolean ticked;
                    try {
                        pay.setBillTicked(BILL, true);
                        ticked = pay.isBillTicked(BILL);
                        settled = pay.amountToBeSettled();
                        rowAmount = pay.billRow(BILL).getOrDefault("Amount to be settled", "");
                    } finally {
                        pay.setBillTicked(BILL, false);     // restore the original selection state
                    }
                    afterUntick = pay.amountToBeSettled();
                    boolean wrote = pay.blockedWrites.size() > writesBefore;
                    String seen = "Ticked " + ticked + "; Amount to be settled '" + settled + "' (row '" + rowAmount + "'); Company Balance '"
                            + balance + "'; after untick '" + afterUntick + "'; write request attempted " + wrote;
                    if (!ticked) return automationFailure("the checkbox of " + BILL + " did not tick | " + seen);
                    if (wrote) return defect(seen + " - ticking a bill tried to send a write request (aborted)");
                    boolean matches = Math.abs(amount(settled) - 120.00) < 0.005 && Math.abs(amount(settled) - amount(balance)) < 0.005;
                    boolean cleared = Double.isNaN(amount(afterUntick)) || amount(afterUntick) == 0;
                    if (!matches) return defect(seen + " - amount does not equal 120.00 / the Company Balance");
                    return cleared ? passed(seen) : defect(seen + " - amount was not cleared after unticking");
                });
    }

    // ---- helpers ---------------------------------------------------------------------

    private int countBillListCalls() {
        int n = 0;
        synchronized (pay.apiLog) {
            for (String s : pay.apiLog) if (s.contains(com.kpj.pages.Billing_page.PaymentSettlementPayor.BILL_API)) n++;
        }
        return n;
    }

    /** -Dkey, else the environment variable (no built-in fallback account). */
    private static String cfgValue(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return v == null ? "" : v.trim();
    }

    /** "1,603.30" -&gt; 1603.30; blank -&gt; NaN. */
    private static double amount(String s) {
        if (s == null || s.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }
}
