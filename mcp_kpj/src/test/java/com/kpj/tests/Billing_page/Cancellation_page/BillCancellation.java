package com.kpj.tests.Billing_page.Cancellation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Cancellation &gt; <b>Bill</b> — READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_Billing_BillCancellation_ChargeConversion_v1.xlsx (Bill Cancellation section). Each scenario
 * is its own method and its own report row; each one reopens the screen so none depends on another.</p>
 *
 * <p><b>Financial safety.</b> No bill is cancelled. The {@code ReadOnlyScreen} write guard is installed BEFORE login and
 * proven live before the screen is touched; if it cannot be proven, every scenario is SKIPPED. Save
 * ({@code fnCancelReceipts();}) is only checked for presence, never clicked; the Company Approve Amount pop-up is closed
 * with its × only (its Approve / Edit / Delete are never clicked). The only state change is ticking and unticking one bill
 * row. The workbook's cancellation cases (successful cancel, Debit Note cancel, linked-transaction / receipt /
 * result-entry rejections, Save without a reason or bill) are reported SKIPPED.</p>
 *
 * <p>Failure reasons say which side failed: APPLICATION DEFECT, AUTOMATION / LOCATOR FAILURE, BLOCKED (test data not on
 * this environment), SKIPPED (financial safety), and MANUAL = the workbook expectation needs functional confirmation.</p>
 *
 * <p>Data (override with -D): {@code bc.mrn} (1100338591), {@code bc.bill} (OPB000000413), {@code bc.from} (01/01/2026),
 * {@code bc.to} (26/11/2026), {@code bc.invalidMrn} (9999999999).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Billing_page.Cancellation_page.BillCancellation" -Ddevhis.postrun=off
 *   report: test-output/reports/Billing_BillCancellation.html
 * </pre>
 */
public class BillCancellation extends DevHisBase {

    private static final String MRN = System.getProperty("bc.mrn", "1100338591");
    private static final String BILL = System.getProperty("bc.bill", "OPB000000413");
    private static final String FROM = System.getProperty("bc.from", "01/01/2026");
    private static final String TO = System.getProperty("bc.to", "26/11/2026");
    private static final String INVALID_MRN = System.getProperty("bc.invalidMrn", "9999999999");
    private static final String PAYOR = "AIA BHD. (EB)";

    private static final List<String> GRID_COLUMNS = List.of("Select checkbox", "Date", "Bill No.", "MRN No.", "IPD No./OPD No.",
            "Patient Name", "Company Name", "Total Bill Amount", "Total Discount", "Tax", "Net Amount", "Self Pay", "Company Pay",
            "Self Balance", "Company Balance", "Cash Counter", "Attachment");

    /** Expected values of {@link #BILL} by column header (workbook). */
    private static final Map<String, String> EXPECTED_BILL = new LinkedHashMap<>();
    static {
        EXPECTED_BILL.put("Date", "27/04/2026");
        EXPECTED_BILL.put("Company Name", PAYOR);
        EXPECTED_BILL.put("Total Bill Amount", "1,603.30");
        EXPECTED_BILL.put("Total Discount", "0.00");
        EXPECTED_BILL.put("Tax", "0.00");
        EXPECTED_BILL.put("Net Amount", "1,603.30");
        EXPECTED_BILL.put("Company Pay", "1,603.30");
        EXPECTED_BILL.put("Company Balance", "1,603.30");
    }

    private static final String APP_DEFECT = "FAILURE REASON - APPLICATION DEFECT: ";
    private static final String AUTOMATION = "FAILURE REASON - AUTOMATION / LOCATOR FAILURE: ";

    private com.kpj.pages.Billing_page.Cancellation_page.BillCancellation bc;
    private boolean guardOn;
    private int pass, fail, skipped, blocked, manual;

    public BillCancellation() { super("Billing_BillCancellation"); }

    public static void main(String[] args) {
        BillCancellation t = new BillCancellation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    // ---- per-scenario plumbing -------------------------------------------------------

    private interface Check { String[] run() throws Exception; }

    /** One scenario = one report row. An exception is an automation / locator failure, never a defect. */
    private void scenario(String name, String steps, String expected, Check c) {
        long t0 = System.currentTimeMillis();
        String status, actual;
        if (!guardOn) {
            status = "SKIPPED";
            actual = "SKIPPED - FINANCIAL SAFETY: the read-only write guard could not be proven, so the screen was not touched";
        } else {
            try {
                String[] r = c.run();
                status = r[0];
                actual = r[1];
            } catch (Throwable e) {
                status = "FAIL";
                actual = AUTOMATION + "exception - " + String.valueOf(e.getMessage()).split("\\R")[0];
            }
        }
        actual += " | duration " + String.format("%.1f", (System.currentTimeMillis() - t0) / 1000.0) + " s";
        step(page, name, "Steps: " + steps, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            case "MANUAL": manual++; break;
            default: fail++;
        }
    }

    private static String[] passed(String actual) { return new String[]{"PASS", actual}; }
    private static String[] defect(String actual) { return new String[]{"FAIL", APP_DEFECT + actual}; }
    private static String[] automationFailure(String actual) { return new String[]{"FAIL", AUTOMATION + actual}; }
    private static String[] blockedData(String actual) { return new String[]{"BLOCKED", "TEST DATA NOT AVAILABLE: " + actual}; }
    private static String[] needsClarification(String actual) { return new String[]{"MANUAL", "NEEDS FUNCTIONAL CONFIRMATION (not a confirmed defect): " + actual}; }

    private String[] freshScreen() {
        return bc.reopen() ? null : automationFailure("Billing > Cancellation > Bill did not open (at " + page.url() + ")");
    }

    /** Fresh screen, patient {@link #MRN} found; null = ready. */
    private String[] patientLoaded() {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        bc.setMrn(MRN);
        int status = bc.findPatient();
        if (status == 0) return automationFailure("Find Patient sent no patient lookup");
        if (status != 200) return defect("patient lookup answered HTTP " + status);
        if (bc.patientBanner().isEmpty()) return blockedData("MRN " + MRN + " not found on " + BASE);
        return null;
    }

    /** Patient loaded and its bills searched for {@link #FROM}-{@link #TO}, Reg. Type OPD; null = searched. */
    private String[] billsSearched() {
        String[] notReady = patientLoaded();
        if (notReady != null) return notReady;
        bc.setRegType("0");
        bc.setDates(FROM, TO);
        int status = bc.search();
        if (status == 0) return automationFailure("Search sent no bill request");
        if (status != 200) return defect("bill search answered HTTP " + status);
        return null;
    }

    // ---- body ----------------------------------------------------------------------------

    @Override
    protected void body() {
        meta("Billing - Cancellation - Bill (read-only)", "Billing > Cancellation > Bill",
                "Read-only checks from TestCases_Billing_BillCancellation_ChargeConversion_v1.xlsx. Write calls are aborted in the browser; "
                        + "Save is never clicked and no bill is cancelled. Patient " + MRN + ", bill " + BILL + ", " + FROM + " - " + TO + ". "
                        + "APPLICATION DEFECT = the screen is wrong; AUTOMATION / LOCATOR FAILURE = the script could not drive it; "
                        + "BLOCKED = test data missing; SKIPPED = not executed for financial safety; MANUAL = expectation needs functional confirmation.");

        String user = cfgValue("devhis.user", "DEVHIS_USER"), pwd = cfgValue("devhis.pass", "DEVHIS_PASS");
        if (user.isEmpty() || pwd.isEmpty()) {
            step("Credentials", "DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass)", "Credentials supplied by the environment",
                    "Not set - nothing was run (no built-in account is used)", "BLOCKED");
            addSummary("Scenarios", "not run - credentials missing");
            return;
        }

        bc = new com.kpj.pages.Billing_page.Cancellation_page.BillCancellation(page);
        bc.installGuards();                                // BEFORE login: write requests are aborted from here on
        new LoginPage(page).login(BASE, user, pwd);
        step("Login", user + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");
        guardOn = bc.guardVerified();
        step("Read-only guard", "Probe a fake IUD request before touching the screen", "The browser aborts write requests",
                guardOn ? "Write guard active (probe aborted)" : "Write guard NOT proven - every scenario below is SKIPPED", guardOn ? "PASS" : "FAIL");

        openBillCancellation();
        verifyBillCancellationSearchPanelAndDefaults();
        verifyBillGridAndCancellationPanel();
        searchBillCancellationWithoutPatient();
        searchBillCancellationValidPatient();
        searchBillCancellationInvalidPatient();
        searchBillsForValidPatientByDateRange();
        verifyBillAmountCalculations();
        verifyCompanyApprovedAmountPopup();
        verifyBillCheckboxSelection();
        verifyCancellationReasonIsRequired();
        cancellationOperationsNotExecuted();

        addSummary("Mode", "READ-ONLY - write calls aborted in the browser; Save never clicked; no bill cancelled");
        addSummary("Patient / bill", MRN + " / " + BILL + " (" + FROM + " - " + TO + ")");
        addSummary("Scenarios", (pass + fail + skipped + blocked + manual) + " - PASS " + pass + " / FAIL " + fail + " / MANUAL " + manual
                + " / BLOCKED " + blocked + " / SKIPPED " + skipped);
        addSummary("Blocked write calls", bc.blockedWrites.isEmpty() ? "none" : String.join(" ; ", bc.blockedWrites));
        addSummary("Failed API responses", bc.apiFailures.isEmpty() ? "none" : String.join(" ; ", bc.apiFailures));
        addSummary("Console errors", bc.consoleErrors.isEmpty() ? "none" : String.join(" ; ", bc.consoleErrors));
    }

    // ---- scenarios -------------------------------------------------------------------------

    private void openBillCancellation() {
        scenario("Open Bill Cancellation",
                "Billing > Cancellation > Bill (sidebar); read the header, browser tab title and any error",
                "Screen opens; heading 'BILL'; browser tab 'Bill'; no application error",
                () -> {
                    int failuresBefore = bc.apiFailures.size();
                    if (!bc.reopen()) return automationFailure("Billing > Cancellation > Bill did not open (at " + page.url() + ")");
                    String header = bc.headerText(), tab = page.title(), error = bc.visibleErrorText() + DevHisBase.reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(bc.apiFailures.subList(failuresBefore, bc.apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Opened " + page.url() + "; heading '" + header + "'; tab '" + tab + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    boolean ok = header.toUpperCase().startsWith("BILL") && "Bill".equalsIgnoreCase(tab.trim());
                    return ok ? passed(seen + "; no application error") : defect(seen + " - heading/tab title not 'BILL' / 'Bill'");
                });
    }

    private void verifyBillCancellationSearchPanelAndDefaults() {
        scenario("Verify search panel and default values",
                "Fresh screen; check From Date, To Date, Reg. Type, MRN, Find Patient, Search Patient, Payor, Bill No., Search; read the defaults",
                "All controls shown; From Date = To Date = today; Reg. Type = OPD; Cash Counter preselected with the user's counter",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = bc.presence(com.kpj.pages.Billing_page.Cancellation_page.BillCancellation.SEARCH_PANEL);
                    List<String> missing = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missing.add(k); });
                    String today = com.kpj.pages.Inventory_page.ReadOnlyScreen.today();
                    String from = bc.fromDate(), to = bc.toDate(), reg = bc.regTypeChecked(), counter = bc.cashCounterSelected();
                    boolean counterSet = !counter.isEmpty() && !counter.toLowerCase().contains("select");
                    String seen = "Missing " + missing + "; From '" + from + "', To '" + to + "' (today " + today + "); Reg. Type "
                            + ("0".equals(reg) ? "OPD" : "1".equals(reg) ? "IPD" : "'" + reg + "'") + "; Cash Counter '" + counter + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!missing.isEmpty()) wrong.add("controls missing " + missing);
                    if (!today.equals(from) || !today.equals(to)) wrong.add("dates are not today");
                    if (!"0".equals(reg)) wrong.add("Reg. Type is not OPD");
                    if (!counterSet) wrong.add("no Cash Counter preselected");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    private void verifyBillGridAndCancellationPanel() {
        scenario("Verify bill grid and cancellation panel",
                "Fresh screen; read the bill grid headers; check Cash Counter, Remark, Cancellation Reason, Company Approved Amount, Save (presence only)",
                "Grid columns " + GRID_COLUMNS + "; cancellation panel controls shown; Cancellation Reason is mandatory",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    List<String> headers = bc.billGridHeaders();
                    if (headers.isEmpty()) return automationFailure("bill grid not found");
                    List<String> missingCols = new ArrayList<>(GRID_COLUMNS);
                    missingCols.removeAll(headers);
                    Map<String, Boolean> panel = bc.presence(com.kpj.pages.Billing_page.Cancellation_page.BillCancellation.CANCELLATION_PANEL);
                    List<String> missingCtrls = new ArrayList<>();
                    panel.forEach((k, v) -> { if (!v) missingCtrls.add(k); });
                    boolean required = bc.reasonMarkedRequired();
                    String seen = "Headers " + headers + " | panel " + panel + " | Cancellation Reason required " + required;
                    if (!missingCols.isEmpty() || !missingCtrls.isEmpty() || !required)
                        return defect("missing columns " + missingCols + ", missing controls " + missingCtrls + (required ? "" : ", reason not mandatory") + " | " + seen);
                    return passed(seen);
                });
    }

    private void searchBillCancellationWithoutPatient() {
        scenario("Search without MRN",
                "Fresh screen; leave MRN blank; click Search",
                "Warning 'Please search patient first'; no bills loaded",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    bc.clickSearchOnly();
                    List<String> messages = bc.waitForAlert(4000);
                    int bills = bc.billNumbers().size();
                    boolean message = messages.stream().anyMatch(m -> m.toLowerCase().contains("please search patient first"));
                    String seen = "Messages " + messages + "; bills listed " + bills;
                    return message && bills == 0 ? passed(seen) : defect(seen);
                });
    }

    private void searchBillCancellationValidPatient() {
        scenario("Search valid patient",
                "Fresh screen; MRN = " + MRN + "; click Find Patient",
                "Patient banner loads with the patient's details; Payor auto-fills 'AIA BHD. (EB)'",
                () -> {
                    String[] notReady = patientLoaded();
                    if (notReady != null) return notReady;
                    String banner = bc.patientBanner(), payor = bc.payorSelected();
                    String seen = "Banner '" + trim(banner, 140) + "'; Payor '" + payor + "'";
                    if (!banner.contains(MRN)) return defect(seen + " - banner does not show MRN " + MRN);
                    return PAYOR.equalsIgnoreCase(payor) ? passed(seen) : defect(seen + " - Payor is not '" + PAYOR + "'");
                });
    }

    private void searchBillCancellationInvalidPatient() {
        scenario("Search invalid patient",
                "Fresh screen; MRN = " + INVALID_MRN + "; click Find Patient",
                "'Patient Not Found' (or the application's own validation message); no patient banner",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    bc.setMrn(INVALID_MRN);
                    int status = bc.findPatient();
                    if (status == 0) return automationFailure("Find Patient sent no patient lookup");
                    List<String> messages = bc.waitForAlert(3000);
                    String banner = bc.patientBanner();
                    String seen = "Lookup HTTP " + status + "; messages " + messages + "; banner '" + trim(banner, 80) + "'";
                    if (!banner.isEmpty()) return defect(seen + " - a patient banner is shown for an unknown MRN");
                    if (messages.isEmpty()) return defect(seen + " - no message tells the user the MRN was not found");
                    return passed(seen);
                });
    }

    private void searchBillsForValidPatientByDateRange() {
        scenario("Search patient bills using date range",
                "Find Patient " + MRN + "; Reg. Type = OPD; From " + FROM + "; To " + TO + "; click Search",
                "Bill " + BILL + " listed: date 27/04/2026, AIA BHD. (EB), Total 1,603.30, Discount 0.00, Tax 0.00, Net 1,603.30, Company Pay 1,603.30, Company Balance 1,603.30",
                () -> {
                    String[] notReady = billsSearched();
                    if (notReady != null) return notReady;
                    Map<String, String> row = bc.billRow(BILL);
                    if (row.isEmpty()) return blockedData("bill " + BILL + " not listed for " + MRN + " (" + FROM + " - " + TO + "); bills listed " + bc.billNumbers());
                    List<String> wrong = new ArrayList<>();
                    StringBuilder shown = new StringBuilder();
                    for (Map.Entry<String, String> e : EXPECTED_BILL.entrySet()) {
                        String actual = row.get(e.getKey());
                        if (actual == null) return automationFailure("column '" + e.getKey() + "' not found | row " + row);
                        shown.append(e.getKey()).append('=').append(actual).append("; ");
                        if (!sameValue(actual, e.getValue())) wrong.add(e.getKey() + " shows '" + actual + "' (expected '" + e.getValue() + "')");
                    }
                    String seen = "Bill " + BILL + ": " + shown;
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    private void verifyBillAmountCalculations() {
        scenario("Verify bill amount calculations",
                "Search " + MRN + " (" + FROM + " - " + TO + "); read bill " + BILL + " by column header",
                "Net Amount = Total Bill Amount - Total Discount + Tax; Self Pay + Company Pay = Net Amount",
                () -> {
                    String[] notReady = billsSearched();
                    if (notReady != null) return notReady;
                    Map<String, String> row = bc.billRow(BILL);
                    if (row.isEmpty()) return blockedData("bill " + BILL + " not listed for " + MRN);
                    double total = amount(row.get("Total Bill Amount")), discount = amount(row.get("Total Discount")), tax = amount(row.get("Tax")),
                            net = amount(row.get("Net Amount")), self = amount(row.get("Self Pay")), company = amount(row.get("Company Pay"));
                    if (Double.isNaN(total) || Double.isNaN(net) || Double.isNaN(self) || Double.isNaN(company))
                        return automationFailure("amount columns not readable | row " + row);
                    boolean netOk = Math.abs(total - (Double.isNaN(discount) ? 0 : discount) + (Double.isNaN(tax) ? 0 : tax) - net) < 0.005;
                    boolean splitOk = Math.abs(self + company - net) < 0.005;
                    String seen = String.format("Total %.2f - Discount %.2f + Tax %.2f = %.2f vs Net %.2f (%s); Self %.2f + Company %.2f = %.2f vs Net (%s)",
                            total, discount, tax, total - discount + tax, net, netOk ? "ok" : "MISMATCH", self, company, self + company, splitOk ? "ok" : "MISMATCH");
                    return netOk && splitOk ? passed(seen) : defect(seen);
                });
    }

    private void verifyCompanyApprovedAmountPopup() {
        scenario("Verify Company Approved Amount popup",
                "Find Patient " + MRN + "; click Company Approved Amount; read the GL row; close with ×; compare the bill grid",
                "Pop-up opens; GL row: Payor AIA BHD. (EB), GL Reference No. dd45454, GL Max Amount 1200, Status GL Approved; after closing, the bill grid is unchanged",
                () -> {
                    String[] notReady = patientLoaded();
                    if (notReady != null) return notReady;
                    List<String> gridBefore = bc.billNumbers();
                    if (!bc.openCompanyApprovedAmount()) return automationFailure("Company Approve Amount pop-up did not open");
                    List<Map<String, String>> gl = bc.glRows();
                    boolean closed = bc.closeCompanyApprovedAmount();
                    List<String> gridAfter = bc.billNumbers();
                    if (!closed) return automationFailure("the pop-up did not close with its × | GL rows " + gl);
                    if (gl.isEmpty()) return blockedData("no GL row for " + MRN + " on " + BASE);
                    Map<String, String> row = gl.stream().filter(r -> "dd45454".equalsIgnoreCase(r.getOrDefault("GL Reference No.", "")))
                            .findFirst().orElse(null);
                    String seen = "GL rows " + gl + "; grid before " + gridBefore + ", after " + gridAfter;
                    if (row == null) return blockedData("GL Reference No. dd45454 not listed | " + seen);
                    if (!gridBefore.equals(gridAfter)) return defect("bill grid changed after closing the pop-up | " + seen);
                    List<String> wrong = new ArrayList<>();
                    if (!PAYOR.equalsIgnoreCase(row.getOrDefault("Payor", ""))) wrong.add("Payor '" + row.get("Payor") + "'");
                    if (!"GL Approved".equalsIgnoreCase(row.getOrDefault("Payor Status", ""))) wrong.add("Payor Status '" + row.get("Payor Status") + "'");
                    if (!wrong.isEmpty()) return defect(String.join("; ", wrong) + " | " + seen);
                    String glMax = row.getOrDefault("GL Max Amount", ""), approved = row.getOrDefault("Approved Amount", "");
                    if (!sameValue(glMax, "1200"))
                        return needsClarification("workbook expects GL Max Amount 1200; the pop-up shows GL Max Amount '" + glMax + "' and Approved Amount '"
                                + approved + "' - confirm which column should hold 1200 | " + seen);
                    return passed(seen);
                });
    }

    private void verifyBillCheckboxSelection() {
        scenario("Verify bill checkbox selection",
                "Search " + MRN + " (" + FROM + " - " + TO + "); tick bill " + BILL + "; untick it",
                "The row is selected after ticking and no longer selected after unticking; no request is sent",
                () -> {
                    String[] notReady = billsSearched();
                    if (notReady != null) return notReady;
                    if (bc.billRow(BILL).isEmpty()) return blockedData("bill " + BILL + " not listed for " + MRN);
                    int writesBefore = bc.blockedWrites.size();
                    boolean ticked, unticked;
                    try {
                        bc.setBillTicked(BILL, true);
                        ticked = bc.isBillTicked(BILL);
                    } finally {
                        bc.setBillTicked(BILL, false);
                    }
                    unticked = !bc.isBillTicked(BILL);
                    boolean wrote = bc.blockedWrites.size() > writesBefore;
                    String seen = "Ticked " + ticked + "; unticked " + unticked + "; write request attempted " + wrote;
                    if (wrote) return defect(seen + " - selecting a bill tried to send a write request (aborted)");
                    if (!ticked) return automationFailure("the checkbox of " + BILL + " did not tick | " + seen);
                    return unticked ? passed(seen) : defect(seen + " - the row stayed selected after unticking");
                });
    }

    private void verifyCancellationReasonIsRequired() {
        scenario("Verify Cancellation Reason validation without performing cancellation",
                "Fresh screen; read the Cancellation Reason options and its validation rule (Save is NOT clicked)",
                "Reasons are available; Cancellation Reason is marked required",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    List<String> reasons = bc.reasonOptions();
                    boolean required = bc.reasonMarkedRequired();
                    String seen = reasons.size() + " reasons (e.g. " + reasons.subList(0, Math.min(3, reasons.size())) + "); required " + required
                            + "; Save not clicked";
                    if (reasons.isEmpty()) return blockedData("no cancellation reasons configured on " + BASE + " | " + seen);
                    return required ? passed(seen) : defect(seen + " - Cancellation Reason is not marked required");
                });
    }

    private void cancellationOperationsNotExecuted() {
        scenario("Cancellation operations (not executed)",
                "Workbook cases that would change a bill: successful cancellation, Debit Note cancellation, cancel with receipts / linked "
                        + "payment, CN or DN / result entry done, Save without a reason or without a bill, re-search after cancelling",
                "Not executed - these change a bill's status",
                () -> new String[]{"SKIPPED", "SKIPPED - FINANCIAL SAFETY: Save (fnCancelReceipts) is never clicked by this read-only test; "
                        + "run these cases manually on test bills. Observation from manual testing: Save cancels without a confirmation prompt."});
    }

    // ---- helpers --------------------------------------------------------------------------

    private static String cfgValue(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isBlank()) v = System.getenv(env);
        return v == null ? "" : v.trim();
    }

    private static double amount(String s) {
        if (s == null || s.trim().isEmpty()) return Double.NaN;
        try { return Double.parseDouble(s.replace(",", "").trim()); } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** Equal as numbers when both are numbers, otherwise case-insensitive text. */
    private static boolean sameValue(String actual, String expected) {
        double a = amount(actual), e = amount(expected);
        if (!Double.isNaN(a) && !Double.isNaN(e)) return Math.abs(a - e) < 0.005;
        return actual != null && actual.trim().equalsIgnoreCase(expected.trim());
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
