package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; <b>Charge Conversion</b> — READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_Billing_BillCancellation_ChargeConversion_v1.xlsx (Charge Conversion section). Each scenario
 * is its own method and its own report row; each one reopens the screen so none depends on another.</p>
 *
 * <p><b>Financial safety.</b> No charge is converted and no sponsor is changed. The {@code ReadOnlyScreen} write guard is
 * installed BEFORE login and proven live before the screen is touched; if it cannot be proven, every scenario is SKIPPED.
 * Convert ({@code FnChargeConvert();}) is only checked for presence, never clicked; the Convert Sponsor dropdown is only
 * read, never changed. The only state change is ticking and unticking Select All. The workbook's conversion cases
 * (convert to a sponsor, Convert without a sponsor / without services, re-search after converting) are reported SKIPPED.</p>
 *
 * <p>Failure reasons say which side failed: APPLICATION DEFECT, AUTOMATION / LOCATOR FAILURE, BLOCKED (test data not on
 * this environment), SKIPPED (financial safety), and MANUAL = the workbook expectation needs functional confirmation.</p>
 *
 * <p>Data (override with -D): {@code cc.mrn} (1100339667, OPD visit with services), {@code cc.noVisitMrn} (1100339686),
 * {@code cc.invalidMrn} (9999999999), {@code cc.service} (MR1000).</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Billing_page.ChargeConversion" -Ddevhis.postrun=off
 *   report: test-output/reports/Billing_ChargeConversion.html
 * </pre>
 */
public class ChargeConversion extends DevHisBase {

    private static final String MRN = System.getProperty("cc.mrn", "1100339667");
    private static final String NO_VISIT_MRN = System.getProperty("cc.noVisitMrn", "1100339686");
    private static final String INVALID_MRN = System.getProperty("cc.invalidMrn", "9999999999");
    private static final String SERVICE = System.getProperty("cc.service", "MR1000");

    private static final List<String> GRID_COLUMNS = List.of("Select All checkbox", "Service Code", "Service Name", "Unit Purchase Price",
            "Quantity", "Total Amount", "Concession Percentage", "Discount Amount", "Remark", "Tax Amount", "Net Amount", "Self Amt.",
            "Company Amt.");

    private static final String APP_DEFECT = "FAILURE REASON - APPLICATION DEFECT: ";
    private static final String AUTOMATION = "FAILURE REASON - AUTOMATION / LOCATOR FAILURE: ";

    private com.kpj.pages.Billing_page.ChargeConversion cc;
    private boolean guardOn;
    private int pass, fail, skipped, blocked, manual;

    public ChargeConversion() { super("Billing_ChargeConversion"); }

    public static void main(String[] args) {
        ChargeConversion t = new ChargeConversion();
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
        return cc.reopen() ? null : automationFailure("Billing > Charge Conversion did not open (at " + page.url() + ")");
    }

    /** Fresh screen, MRN {@link #MRN} searched and its services listed; null = ready. */
    private String[] servicesLoaded() {
        String[] notOpen = freshScreen();
        if (notOpen != null) return notOpen;
        cc.setMrn(MRN);
        int status = cc.searchByMrn();
        if (status == 0) return automationFailure("Search sent no patient lookup");
        if (status != 200) return defect("patient lookup answered HTTP " + status);
        if (cc.patientBanner().isEmpty()) return blockedData("MRN " + MRN + " not found on " + BASE);
        if (cc.serviceRows().isEmpty())
            return blockedData("MRN " + MRN + " has no open visit with services on " + BASE + " (messages " + cc.alerts() + ")");
        return null;
    }

    // ---- body ----------------------------------------------------------------------------

    @Override
    protected void body() {
        meta("Billing - Charge Conversion (read-only)", "Billing > Charge Conversion",
                "Read-only checks from TestCases_Billing_BillCancellation_ChargeConversion_v1.xlsx. Write calls are aborted in the browser; "
                        + "Convert is never clicked, no sponsor is changed. Patient " + MRN + " (service " + SERVICE + "). "
                        + "APPLICATION DEFECT = the screen is wrong; AUTOMATION / LOCATOR FAILURE = the script could not drive it; "
                        + "BLOCKED = test data missing; SKIPPED = not executed for financial safety; MANUAL = expectation needs functional confirmation.");

        String user = cfgValue("devhis.user", "DEVHIS_USER"), pwd = cfgValue("devhis.pass", "DEVHIS_PASS");
        if (user.isEmpty() || pwd.isEmpty()) {
            step("Credentials", "DEVHIS_USER / DEVHIS_PASS (or -Ddevhis.user / -Ddevhis.pass)", "Credentials supplied by the environment",
                    "Not set - nothing was run (no built-in account is used)", "BLOCKED");
            addSummary("Scenarios", "not run - credentials missing");
            return;
        }

        cc = new com.kpj.pages.Billing_page.ChargeConversion(page);
        cc.installGuards();                                // BEFORE login: write requests are aborted from here on
        new LoginPage(page).login(BASE, user, pwd);
        step("Login", user + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");
        guardOn = cc.guardVerified();
        step("Read-only guard", "Probe a fake IUD request before touching the screen", "The browser aborts write requests",
                guardOn ? "Write guard active (probe aborted)" : "Write guard NOT proven - every scenario below is SKIPPED", guardOn ? "PASS" : "FAIL");

        openChargeConversion();
        verifyChargeConversionPatientPanel();
        verifyServicesGridColumnsAndButtons();
        searchChargeConversionWithoutMrn();
        searchChargeConversionValidOpdPatient();
        searchChargeConversionPatientWithoutActiveVisit();
        searchChargeConversionInvalidMrn();
        verifyChargeConversionServiceAmounts();
        verifyServiceAmountsAreReadOnly();
        verifySelectAllAndUntick();
        verifyConvertSponsorDropdown();
        conversionOperationsNotExecuted();

        addSummary("Mode", "READ-ONLY - write calls aborted in the browser; Convert never clicked; no sponsor changed");
        addSummary("Patient / service", MRN + " / " + SERVICE);
        addSummary("Scenarios", (pass + fail + skipped + blocked + manual) + " - PASS " + pass + " / FAIL " + fail + " / MANUAL " + manual
                + " / BLOCKED " + blocked + " / SKIPPED " + skipped);
        addSummary("Blocked write calls", cc.blockedWrites.isEmpty() ? "none" : String.join(" ; ", cc.blockedWrites));
        addSummary("Failed API responses", cc.apiFailures.isEmpty() ? "none" : String.join(" ; ", cc.apiFailures));
        addSummary("Console errors", cc.consoleErrors.isEmpty() ? "none" : String.join(" ; ", cc.consoleErrors));
    }

    // ---- scenarios -------------------------------------------------------------------------

    private void openChargeConversion() {
        scenario("Open Charge Conversion",
                "Billing > Charge Conversion (sidebar); read the header, browser tab title and any error",
                "Screen opens; heading 'CHARGE CONVERSION'; browser tab 'Charge Conversion'; no application error",
                () -> {
                    int failuresBefore = cc.apiFailures.size();
                    if (!cc.reopen()) return automationFailure("Billing > Charge Conversion did not open (at " + page.url() + ")");
                    String header = cc.headerText(), tab = page.title(), error = cc.visibleErrorText() + DevHisBase.reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(cc.apiFailures.subList(failuresBefore, cc.apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Opened " + page.url() + "; heading '" + header + "'; tab '" + tab + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    boolean ok = header.toUpperCase().contains("CHARGE CONVERSION") && "Charge Conversion".equalsIgnoreCase(tab.trim());
                    return ok ? passed(seen + "; no application error") : defect(seen + " - heading/tab title not 'CHARGE CONVERSION' / 'Charge Conversion'");
                });
    }

    private void verifyChargeConversionPatientPanel() {
        scenario("Verify patient panel and default values",
                "Fresh screen; check MRN, Search, Search Patient, OPD / IPD, OPD/IPD No., Payor, Convert Sponsor; read the defaults",
                "All controls shown; OPD selected by default; MRN and OPD/IPD No. empty",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = cc.presence(com.kpj.pages.Billing_page.ChargeConversion.PATIENT_PANEL);
                    List<String> missing = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missing.add(k); });
                    String visit = cc.visitTypeChecked(), mrn = cc.inputValue(com.kpj.pages.Billing_page.ChargeConversion.MRN), opd = cc.opdNo();
                    String seen = "Missing " + missing + "; visit type " + ("0".equals(visit) ? "OPD" : "1".equals(visit) ? "IPD" : "'" + visit + "'")
                            + "; MRN '" + (mrn == null ? "" : mrn) + "'; OPD/IPD No. '" + opd + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!missing.isEmpty()) wrong.add("controls missing " + missing);
                    if (!"0".equals(visit)) wrong.add("OPD is not the default");
                    if (mrn != null && !mrn.trim().isEmpty()) wrong.add("MRN is not empty");
                    if (!opd.isEmpty()) wrong.add("OPD/IPD No. is not empty");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    private void verifyServicesGridColumnsAndButtons() {
        scenario("Verify services grid columns and buttons",
                "Fresh screen; read the services grid headers; check Select All, Convert, Cancel (presence only)",
                "Grid columns " + GRID_COLUMNS + "; Select All, Convert and Cancel shown",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    List<String> headers = cc.serviceGridHeaders();
                    if (headers.isEmpty()) return automationFailure("services grid not found");
                    List<String> missingCols = new ArrayList<>(GRID_COLUMNS);
                    missingCols.removeAll(headers);
                    Map<String, Boolean> buttons = cc.presence(com.kpj.pages.Billing_page.ChargeConversion.BUTTONS);
                    List<String> missingBtns = new ArrayList<>();
                    buttons.forEach((k, v) -> { if (!v) missingBtns.add(k); });
                    String seen = "Headers " + headers + " | buttons " + buttons;
                    if (!missingCols.isEmpty() || !missingBtns.isEmpty())
                        return defect("missing columns " + missingCols + ", missing buttons " + missingBtns + " | " + seen);
                    return passed(seen);
                });
    }

    private void searchChargeConversionWithoutMrn() {
        scenario("Search without MRN",
                "Fresh screen; leave MRN blank; click Search",
                "Warning 'Please Enter MRN No.'; no patient or services loaded",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    cc.clickSearchOnly();
                    List<String> messages = cc.waitForAlert(4000);
                    int rows = cc.serviceRows().size();
                    boolean message = messages.stream().anyMatch(m -> m.toLowerCase().contains("please enter mrn"));
                    String seen = "Messages " + messages + "; service rows " + rows + "; banner '" + trim(cc.patientBanner(), 60) + "'";
                    return message && rows == 0 ? passed(seen) : defect(seen);
                });
    }

    private void searchChargeConversionValidOpdPatient() {
        scenario("Search valid OPD patient",
                "Fresh screen; OPD; MRN = " + MRN + "; click Search",
                "Patient banner loads; OPD/IPD No. and Payor auto-fill; the visit's services are listed",
                () -> {
                    String[] notReady = servicesLoaded();
                    if (notReady != null) return notReady;
                    String banner = cc.patientBanner(), opd = cc.opdNo(), payor = cc.payorSelected();
                    int rows = cc.serviceRows().size();
                    String seen = "Banner '" + trim(banner, 120) + "'; OPD/IPD No. '" + opd + "'; Payor '" + payor + "'; service rows " + rows;
                    List<String> wrong = new ArrayList<>();
                    if (!banner.contains(MRN)) wrong.add("banner does not show MRN " + MRN);
                    if (opd.isEmpty()) wrong.add("OPD/IPD No. not filled");
                    if (payor.isEmpty() || payor.toLowerCase().contains("select")) wrong.add("Payor not filled");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    private void searchChargeConversionPatientWithoutActiveVisit() {
        scenario("Search patient without an active visit",
                "Fresh screen; OPD; MRN = " + NO_VISIT_MRN + " (registered patient with no open OPD visit); click Search",
                "No services are listed and the user is told there is no visit to convert (workbook wording to be confirmed)",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    cc.setMrn(NO_VISIT_MRN);
                    int status = cc.searchByMrn();
                    if (status == 0) return automationFailure("Search sent no patient lookup");
                    List<String> messages = cc.waitForAlert(3000);
                    String banner = cc.patientBanner(), opd = cc.opdNo();
                    int rows = cc.serviceRows().size();
                    String seen = "Lookup HTTP " + status + "; banner '" + trim(banner, 80) + "'; OPD/IPD No. '" + opd + "'; service rows " + rows
                            + "; messages " + messages;
                    if (banner.isEmpty()) return blockedData("MRN " + NO_VISIT_MRN + " not found on " + BASE + " | " + seen);
                    if (rows > 0 || !opd.isEmpty()) return blockedData("MRN " + NO_VISIT_MRN + " now has an open visit - pick a patient without one | " + seen);
                    boolean visitMessage = messages.stream().anyMatch(m -> m.toLowerCase().matches(".*(no (active |open )?visit|visit not found|no record|no service).*"));
                    if (visitMessage) return passed(seen);
                    if (messages.isEmpty()) return defect(seen + " - nothing tells the user why no services are shown");
                    return needsClarification("the patient banner loads but the message is " + messages + ", which does not say the patient has no "
                            + "visit - confirm the intended message | " + seen);
                });
    }

    private void searchChargeConversionInvalidMrn() {
        scenario("Search invalid MRN",
                "Fresh screen; MRN = " + INVALID_MRN + "; click Search",
                "'Patient Not Found'; no patient banner or services",
                () -> {
                    String[] notOpen = freshScreen();
                    if (notOpen != null) return notOpen;
                    cc.setMrn(INVALID_MRN);
                    int status = cc.searchByMrn();
                    if (status == 0) return automationFailure("Search sent no patient lookup");
                    List<String> messages = cc.waitForAlert(3000);
                    String banner = cc.patientBanner();
                    int rows = cc.serviceRows().size();
                    String seen = "Lookup HTTP " + status + "; messages " + messages + "; banner '" + trim(banner, 60) + "'; service rows " + rows;
                    if (!banner.isEmpty() || rows > 0) return defect(seen + " - patient data shown for an unknown MRN");
                    boolean notFound = messages.stream().anyMatch(m -> m.toLowerCase().contains("not found"));
                    if (notFound) return passed(seen);
                    if (messages.isEmpty()) return defect(seen + " - no message for an unknown MRN");
                    return defect(seen + " - message does not say the patient was not found (workbook expects 'Patient Not Found')");
                });
    }

    private void verifyChargeConversionServiceAmounts() {
        scenario("Verify charge conversion service amounts",
                "Search " + MRN + "; read service " + SERVICE + " by column header",
                "Total Amount = Unit Purchase Price x Quantity; Net Amount = Total - Discount (+ Tax when tax is exclusive); Self Amt. + Company Amt. = Net Amount",
                () -> {
                    String[] notReady = servicesLoaded();
                    if (notReady != null) return notReady;
                    Map<String, String> row = cc.serviceRows().stream().filter(r -> SERVICE.equalsIgnoreCase(r.getOrDefault("Service Code", "")))
                            .findFirst().orElse(null);
                    if (row == null) return blockedData("service " + SERVICE + " not listed for " + MRN + " | rows " + cc.serviceRows());
                    double price = amount(row.get("Unit Purchase Price")), qty = amount(row.get("Quantity")), total = amount(row.get("Total Amount")),
                            discount = amount(row.get("Discount Amount")), tax = amount(row.get("Tax Amount")), net = amount(row.get("Net Amount")),
                            self = amount(row.get("Self Amt.")), company = amount(row.get("Company Amt."));
                    if (Double.isNaN(price) || Double.isNaN(qty) || Double.isNaN(total) || Double.isNaN(net) || Double.isNaN(self) || Double.isNaN(company))
                        return automationFailure("amount columns not readable | row " + row);
                    discount = Double.isNaN(discount) ? 0 : discount;
                    tax = Double.isNaN(tax) ? 0 : tax;
                    boolean totalOk = Math.abs(price * qty - total) < 0.005;
                    boolean splitOk = Math.abs(self + company - net) < 0.005;
                    boolean netExclusive = Math.abs(total - discount + tax - net) < 0.005;
                    boolean netInclusive = Math.abs(total - discount - net) < 0.005;
                    String seen = String.format("%s: Price %.2f x Qty %.0f = %.2f vs Total %.2f (%s); Discount %.2f; Tax %.2f; Net %.2f; Self %.2f + Company %.2f = %.2f vs Net (%s)",
                            SERVICE, price, qty, price * qty, total, totalOk ? "ok" : "MISMATCH", discount, tax, net, self, company, self + company, splitOk ? "ok" : "MISMATCH");
                    if (!totalOk || !splitOk) return defect(seen);
                    if (netExclusive) return passed(seen + "; Net = Total - Discount + Tax");
                    if (netInclusive && tax > 0)
                        return needsClarification(seen + "; Net = Total - Discount, so Tax " + String.format("%.2f", tax)
                                + " appears to be included in the price (tax-inclusive). Confirm the intended pricing rule before treating this as a defect");
                    return defect(seen + "; Net matches neither Total - Discount + Tax nor Total - Discount");
                });
    }

    private void verifyServiceAmountsAreReadOnly() {
        scenario("Verify service amounts cannot be edited",
                "Search " + MRN + "; check whether Unit Purchase Price, Self Amt. and Company Amt. of " + SERVICE + " are editable (nothing is typed)",
                "Amount fields are read-only on Charge Conversion",
                () -> {
                    String[] notReady = servicesLoaded();
                    if (notReady != null) return notReady;
                    Map<String, String> row = cc.serviceRows().stream().filter(r -> SERVICE.equalsIgnoreCase(r.getOrDefault("Service Code", "")))
                            .findFirst().orElse(null);
                    if (row == null) return blockedData("service " + SERVICE + " not listed for " + MRN);
                    List<String> disabled = List.of(row.getOrDefault("__disabled", "").split("\\|"));
                    List<String> editable = new ArrayList<>();
                    for (String col : List.of("Unit Purchase Price", "Self Amt.", "Company Amt.")) if (!disabled.contains(col)) editable.add(col);
                    String seen = "Disabled inputs " + disabled;
                    return editable.isEmpty() ? passed(seen) : defect(seen + " - editable amount fields " + editable);
                });
    }

    private void verifySelectAllAndUntick() {
        scenario("Verify Select All and untick",
                "Search " + MRN + "; tick Select All; untick Select All",
                "Ticking selects every service row; unticking clears them all; no request is sent",
                () -> {
                    String[] notReady = servicesLoaded();
                    if (notReady != null) return notReady;
                    int writesBefore = cc.blockedWrites.size();
                    List<Boolean> afterTick, afterUntick;
                    try {
                        cc.setSelectAll(true);
                        afterTick = cc.serviceTicks();
                    } finally {
                        cc.setSelectAll(false);
                    }
                    afterUntick = cc.serviceTicks();
                    boolean wrote = cc.blockedWrites.size() > writesBefore;
                    boolean allTicked = !afterTick.isEmpty() && afterTick.stream().allMatch(Boolean::booleanValue);
                    boolean allClear = afterUntick.stream().noneMatch(Boolean::booleanValue);
                    String seen = "After tick " + afterTick + "; after untick " + afterUntick + "; write request attempted " + wrote;
                    if (wrote) return defect(seen + " - Select All tried to send a write request (aborted)");
                    if (afterTick.isEmpty()) return automationFailure("no service row checkboxes found | " + seen);
                    return allTicked && allClear ? passed(seen) : defect(seen);
                });
    }

    private void verifyConvertSponsorDropdown() {
        scenario("Verify Convert Sponsor dropdown without converting",
                "Search " + MRN + "; read the Convert Sponsor options (nothing is selected; Convert is NOT clicked)",
                "Convert Sponsor lists the sponsors the patient's charges can be converted to",
                () -> {
                    String[] notReady = servicesLoaded();
                    if (notReady != null) return notReady;
                    List<String> options = cc.convertSponsorOptions();
                    if (options == null) return automationFailure("Convert Sponsor dropdown not found");
                    List<String> sponsors = new ArrayList<>(options);
                    sponsors.removeIf(o -> o.isEmpty() || o.toLowerCase().contains("select"));
                    String current = cc.payorSelected();
                    String seen = "Options " + options + "; current Payor '" + current + "'; Convert not clicked";
                    if (sponsors.isEmpty()) return blockedData("no sponsor offered for " + MRN + " | " + seen);
                    if (sponsors.size() == 1 && sponsors.get(0).equalsIgnoreCase(current))
                        return needsClarification("the only sponsor offered is the patient's current Payor '" + current
                                + "' - confirm whether other sponsors should be listed for this patient | " + seen);
                    return passed(seen);
                });
    }

    private void conversionOperationsNotExecuted() {
        scenario("Charge conversion operations (not executed)",
                "Workbook cases that would change a patient's charges: convert selected services to another sponsor, Convert without a "
                        + "sponsor, Convert without selecting a service, verify the converted amounts, re-search after converting",
                "Not executed - these change the sponsor of billed charges",
                () -> new String[]{"SKIPPED", "SKIPPED - FINANCIAL SAFETY: Convert (FnChargeConvert) is never clicked by this read-only test; "
                        + "run these cases manually on test patients."});
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

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
