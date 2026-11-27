package com.kpj.tests.Billing_page;

import com.kpj.pages.Inventory_page.ReadOnlyScreen;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Discount Manager/CR — discount LIST screen, READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_Billing_DiscountManagerCR_v1.xlsx (Screen 1 - Discount List). One JUnit test per scenario; each
 * reopens the screen so none depends on another. Report: {@code test-output/reports/Billing_DiscountManagerList.html}.</p>
 *
 * <p><b>Read-only.</b> Only navigation, searching, patient lookup and reading the grid. A row's Approve tick, Print and
 * Attachment are never clicked; the {@code ReadOnlyScreen} guard aborts any write request.</p>
 *
 * <p><b>Regression tests for known defects</b> keep the expected result the business expects and report what the application
 * actually does: an empty search that keeps the previous rows with no "No Records Found" message, and Reg. Type defaulting
 * to IPD instead of OPD).</p>
 *
 * <p>Data (override with -D): {@code dm.from} 01/01/2026, {@code dm.to} 26/11/2026, {@code dm.mrn} 1000338059,
 * {@code dm.discountNo} CON-THKD-0000023, {@code dm.invalidMrn} 9999999999.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Billing_page.DiscountManagerList" -Ddevhis.postrun=off
 * </pre>
 */
public class DiscountManagerList extends DiscountManagerTestBase<com.kpj.pages.Billing_page.DiscountManagerList> {

    private static final String FROM = System.getProperty("dm.from", "01/01/2026");
    private static final String TO = System.getProperty("dm.to", "26/11/2026");
    private static final String MRN = System.getProperty("dm.mrn", "1000338059");
    private static final String DISCOUNT_NO = System.getProperty("dm.discountNo", "CON-THKD-0000023");
    private static final String INVALID_MRN = System.getProperty("dm.invalidMrn", "9999999999");

    private static final List<String> GRID_COLUMNS = List.of("MRN", "Patient Name", "Bill No.", "Discount", "Date", "Company Name",
            "Authorized By", "Discount Reason", "Total Bill Amount", "Total Discount", "Tax", "Net Amount", "Approve", "Approved By",
            "Approved Amount", "Attachment", "Print");

    public DiscountManagerList() { super("Billing_DiscountManagerList"); }

    @Override protected com.kpj.pages.Billing_page.DiscountManagerList createScreen(Page page) { return new com.kpj.pages.Billing_page.DiscountManagerList(page); }
    @Override protected String reportTitle() { return "Billing - Discount Manager/CR - List (read-only)"; }
    @Override protected String reportNote() {
        return "Read-only checks from TestCases_Billing_DiscountManagerCR_v1.xlsx (Screen 1). Write calls are aborted in the browser; "
                + "Approve / Print are never clicked. The empty-search and default Reg. Type checks keep the business expectation "
                + "and report the actual behaviour. Each row: scenario name, steps, expected, actual, status, screenshot.";
    }
    @Override protected String dataSummary() { return "range " + FROM + " - " + TO + ", MRN " + MRN + " (" + DISCOUNT_NO + ")"; }

    private String[] freshList() {
        return screen.reopen() ? null : automationFailure("Billing > Discount Manager/CR did not open (at " + page.url() + ")");
    }

    /** Fresh list, Reg. Type {@code regType}, {@link #FROM}-{@link #TO} searched; null = searched. */
    private String[] searchedWide(String regType) {
        String[] notOpen = freshList();
        if (notOpen != null) return notOpen;
        screen.setRegType(regType);
        screen.setDates(FROM, TO);
        int status = screen.search();
        if (status == 0) return automationFailure("Search sent no list request");
        if (status != 200) return defect("discount list answered HTTP " + status);
        return null;
    }

    private static String regName(String v) { return "0".equals(v) ? "OPD" : "1".equals(v) ? "IPD" : "'" + v + "'"; }

    // ---- scenarios -------------------------------------------------------------------------

    @Test @Order(1) @DisplayName("Open Billing > Discount Manager/CR")
    void openDiscountManagerList() {
        scenario("Open Billing > Discount Manager/CR",
                "Billing > Discount Manager/CR (sidebar); read the header and any error",
                "Discount list opens with no error; header 'Discount Manager/CR'",
                () -> {
                    int failuresBefore = screen.apiFailures.size();
                    if (!screen.reopen()) return automationFailure("Billing > Discount Manager/CR did not open (at " + page.url() + ")");
                    String header = screen.headerText(), error = screen.visibleErrorText() + reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(screen.apiFailures.subList(failuresBefore, screen.apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Opened " + page.url() + "; header '" + header + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    return header.toLowerCase().startsWith("discount manager/cr") ? passed(seen + "; no application error")
                            : defect(seen + " - header is not 'Discount Manager/CR'");
                });
    }

    @Test @Order(2) @DisplayName("Verify search fields and grid columns")
    void verifySearchFieldsAndGridColumns() {
        scenario("Verify search fields and grid columns",
                "Fresh list; check every search control and button; read the grid headers and the two dropdowns",
                "From Date, To Date, MRN, Find Patient, Search Patient, Reg. Type, Dis. Authorized By, Discount Reason, Search, Page No., "
                        + "<<, >>, Print, Add shown; grid columns " + GRID_COLUMNS + "; both dropdowns have values",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = screen.presence(com.kpj.pages.Billing_page.DiscountManagerList.SEARCH_PANEL);
                    List<String> missing = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missing.add(k); });
                    List<String> headers = screen.tableHeaders(com.kpj.pages.Billing_page.DiscountManagerList.LIST_GRID);
                    if (headers == null) return automationFailure("discount grid not found");
                    List<String> missingCols = new ArrayList<>(GRID_COLUMNS);
                    missingCols.removeAll(headers);
                    int authorisers = screen.options(com.kpj.pages.Billing_page.DiscountManagerList.AUTHORIZED_BY).size();
                    int reasons = screen.options(com.kpj.pages.Billing_page.DiscountManagerList.REASON).size();
                    String seen = "Missing controls " + missing + "; headers " + headers + "; Authorized By options " + authorisers
                            + "; Discount Reason options " + reasons;
                    if (!missing.isEmpty() || !missingCols.isEmpty())
                        return defect("missing controls " + missing + ", missing columns " + missingCols + " | " + seen);
                    if (authorisers == 0 || reasons == 0) return dataMissing("Authorized By / Discount Reason not configured | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(3) @DisplayName("Verify default search values")
    void verifyDefaultSearchValues() {
        scenario("Verify default search values",
                "Fresh list; read From Date, To Date, MRN, Dis. Authorized By, Discount Reason",
                "From Date = To Date = today; MRN empty; Dis. Authorized By and Discount Reason = --Select--",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    String today = ReadOnlyScreen.today();
                    String from = screen.inputValue(com.kpj.pages.Billing_page.DiscountManagerList.FROM),
                            to = screen.inputValue(com.kpj.pages.Billing_page.DiscountManagerList.TO),
                            mrn = screen.inputValue(com.kpj.pages.Billing_page.DiscountManagerList.MRN),
                            auth = screen.selectedText(com.kpj.pages.Billing_page.DiscountManagerList.AUTHORIZED_BY),
                            reason = screen.selectedText(com.kpj.pages.Billing_page.DiscountManagerList.REASON);
                    String seen = "From '" + from + "', To '" + to + "' (today " + today + "); MRN '" + (mrn == null ? "" : mrn)
                            + "'; Authorized By '" + auth + "'; Reason '" + reason + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!today.equals(from) || !today.equals(to)) wrong.add("dates are not today");
                    if (mrn != null && !mrn.isBlank()) wrong.add("MRN not empty");
                    if (!auth.toLowerCase().contains("select")) wrong.add("Authorized By preselected");
                    if (!reason.toLowerCase().contains("select")) wrong.add("Reason preselected");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(4) @DisplayName("Verify default Registration Type is OPD")
    void defect02DefaultRegistrationTypeIsOpd() {
        scenario("Verify default Registration Type is OPD",
                "Fresh list; read which Reg. Type radio is selected (nothing clicked)",
                "Reg. Type defaults to OPD (consistent with the other Billing screens)",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    String reg = screen.regTypeChecked();
                    String seen = "Reg. Type selected by default: " + regName(reg);
                    return "0".equals(reg) ? passed(seen + " - the earlier IPD default is fixed")
                            : defect(seen + " (expected OPD)");
                });
    }

    @Test @Order(5) @DisplayName("Search discounts using a wide date range")
    void searchDiscountsWithWideDateRange() {
        scenario("Search discounts using a wide date range",
                "Reg. Type = OPD; From " + FROM + "; To " + TO + "; click Search",
                "Discounts in the range are listed, each dated inside the range, including " + DISCOUNT_NO,
                () -> {
                    String[] notReady = searchedWide("0");
                    if (notReady != null) return notReady;
                    List<Map<String, String>> rows = screen.listRows();
                    if (rows.isEmpty()) return dataMissing("no OPD discount between " + FROM + " and " + TO + " on " + BASE);
                    List<String> numbers = new ArrayList<>();
                    rows.forEach(r -> numbers.add(r.getOrDefault("Discount", "")));
                    String seen = rows.size() + " rows: " + numbers + "; messages " + screen.alerts();
                    if (!numbers.contains(DISCOUNT_NO)) return dataMissing(DISCOUNT_NO + " not listed | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(6) @DisplayName("Filter OPD and IPD discount records")
    void filterOpdAndIpdRecords() {
        scenario("Filter OPD and IPD records",
                "From " + FROM + " To " + TO + "; search with Reg. Type = OPD, then with Reg. Type = IPD; compare the discount numbers",
                "Each search lists its own records: the OPD and IPD results do not share any discount",
                () -> {
                    String[] notReady = searchedWide("0");
                    if (notReady != null) return notReady;
                    List<String> opd = new ArrayList<>();
                    screen.listRows().forEach(r -> opd.add(r.getOrDefault("Discount", "")));
                    screen.setRegType("1");
                    int status = screen.search();
                    if (status != 200) return status == 0 ? automationFailure("IPD Search sent no request") : defect("IPD search answered HTTP " + status);
                    List<String> ipd = new ArrayList<>();
                    screen.listRows().forEach(r -> ipd.add(r.getOrDefault("Discount", "")));
                    List<String> both = new ArrayList<>(opd);
                    both.retainAll(ipd);
                    String seen = "OPD " + opd.size() + " " + opd + "; IPD " + ipd.size() + " " + ipd;
                    if (opd.isEmpty() && ipd.isEmpty()) return dataMissing("no discounts in the range | " + seen);
                    if (!both.isEmpty() && !opd.isEmpty())
                        return defect("the same discounts are listed for OPD and IPD " + both + (opd.equals(ipd) ? " - the IPD search kept the OPD rows (empty search does not clear the grid)" : "") + " | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(7) @DisplayName("Verify empty search clears previous results")
    void defect01EmptySearchClearsPreviousResults() {
        scenario("Verify empty search clears previous results",
                "1) Reg. Type OPD, search " + FROM + " - " + TO + "; 2) confirm records appear; 3) set From/To Date = today; "
                        + "4) click Search; 5) check the grid; 6) check the message",
                "Previous records are cleared and a 'No Records Found' message is shown",
                () -> {
                    String[] notReady = searchedWide("0");
                    if (notReady != null) return notReady;
                    List<Map<String, String>> before = screen.listRows();
                    if (before.isEmpty()) return dataMissing("step 2: the wide search returned no records, so the regression cannot be shown");
                    String today = ReadOnlyScreen.today();
                    screen.setDates(today, today);
                    int status = screen.search();
                    if (status == 0) return automationFailure("step 4: Search sent no list request");
                    List<String> messages = screen.waitForAlert(3000);
                    List<Map<String, String>> after = screen.listRows();
                    boolean todayHasData = after.stream().anyMatch(r -> today.equals(toDdMmYyyy(r.getOrDefault("Date", ""))));
                    if (todayHasData) return dataMissing("discounts exist for today (" + today + "), so an empty search cannot be produced");
                    boolean cleared = after.isEmpty();
                    boolean noRecords = messages.stream().anyMatch(m -> m.toLowerCase().contains("no record"));
                    String seen = "Before: " + before.size() + " rows; after searching " + today + " (HTTP " + status + "): " + after.size()
                            + " rows " + (after.isEmpty() ? "" : "(first " + after.get(0).getOrDefault("Discount", "") + " dated "
                            + after.get(0).getOrDefault("Date", "") + ")") + "; messages " + messages;
                    if (cleared && noRecords) return passed(seen + " - previous rows were cleared");
                    List<String> wrong = new ArrayList<>();
                    if (!cleared) wrong.add("the previous " + after.size() + " rows are still shown");
                    if (!noRecords) wrong.add("no 'No Records Found' message");
                    return defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(8) @DisplayName("Patient lookup with empty MRN")
    void patientLookupWithEmptyMrn() {
        scenario("Patient lookup with empty MRN",
                "Fresh list; MRN empty; click Find Patient",
                "Warning 'Please Enter MRN No.'",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    screen.findPatientExpectingValidation();
                    List<String> messages = screen.waitForAlert(4000);
                    String seen = "Messages " + messages;
                    return messages.stream().anyMatch(m -> m.toLowerCase().contains("please enter mrn")) ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(9) @DisplayName("Patient lookup with invalid MRN")
    void patientLookupWithInvalidMrn() {
        scenario("Patient lookup with invalid MRN",
                "Fresh list; MRN = " + INVALID_MRN + "; click Find Patient",
                "Message 'Patient Not Found'; no patient banner",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    screen.setMrn(INVALID_MRN);
                    int status = screen.findPatient();
                    if (status == 0) return automationFailure("Find Patient sent no patient lookup");
                    List<String> messages = screen.waitForAlert(3000);
                    String banner = screen.patientBanner();
                    String seen = "Lookup HTTP " + status + "; messages " + messages + "; banner '" + trim(banner, 60) + "'";
                    if (!banner.isEmpty()) return defect(seen + " - a banner is shown for an unknown MRN");
                    if (messages.stream().anyMatch(m -> m.toLowerCase().contains("not found"))) return passed(seen);
                    return defect(seen + " - " + (messages.isEmpty() ? "no message tells the user the MRN was not found" : "message does not say 'Patient Not Found'"));
                });
    }

    @Test @Order(10) @DisplayName("Patient lookup with valid MRN 1000338059")
    void patientLookupWithValidMrn() {
        scenario("Patient lookup with valid MRN " + MRN,
                "Reg. Type OPD; From " + FROM + " To " + TO + "; MRN = " + MRN + "; click Find Patient",
                "Patient banner loads with MRN " + MRN + "; the grid lists only this patient's discounts, including " + DISCOUNT_NO,
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    screen.setRegType("0");
                    screen.setDates(FROM, TO);
                    screen.setMrn(MRN);
                    int status = screen.findPatient();
                    if (status == 0) return automationFailure("Find Patient sent no patient lookup");
                    if (status != 200) return defect("patient lookup answered HTTP " + status);
                    String banner = screen.patientBanner();
                    if (banner.isEmpty()) return dataMissing("MRN " + MRN + " not found on " + BASE);
                    List<Map<String, String>> rows = screen.listRows();
                    List<String> others = new ArrayList<>();
                    List<String> numbers = new ArrayList<>();
                    rows.forEach(r -> { numbers.add(r.getOrDefault("Discount", "")); if (!MRN.equals(r.getOrDefault("MRN", ""))) others.add(r.getOrDefault("MRN", "")); });
                    String seen = "Banner '" + trim(banner, 120) + "'; rows " + numbers;
                    if (!banner.contains(MRN)) return defect(seen + " - banner does not show MRN " + MRN);
                    if (!others.isEmpty()) return defect(seen + " - rows of other patients listed " + others);
                    if (!numbers.contains(DISCOUNT_NO)) return dataMissing(DISCOUNT_NO + " not listed for " + MRN + " | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(11) @DisplayName("Verify grid values and Net Amount calculations")
    void verifyGridValuesAndNetAmount() {
        scenario("Verify grid values and Net Amount calculations",
                "Reg. Type OPD; search " + FROM + " - " + TO + "; read " + DISCOUNT_NO + " by column header; check Net Amount of every row",
                DISCOUNT_NO + ": Bill No. OPB-THKD-0000060, 12-Jan-26, SELFPAY / CASH CUSTOMER, Total 13602.9, Total Discount 222, Tax 0, "
                        + "Net 13380.9, Approved By Employee 1303, Approved Amount 222; every row: Net Amount = Total Bill Amount - Total Discount (+ Tax when tax is exclusive)",
                () -> {
                    String[] notReady = searchedWide("0");
                    if (notReady != null) return notReady;
                    List<Map<String, String>> rows = screen.listRows();
                    Map<String, String> row = rows.stream().filter(r -> DISCOUNT_NO.equals(r.get("Discount"))).findFirst().orElse(null);
                    if (row == null) return dataMissing(DISCOUNT_NO + " not listed in " + FROM + " - " + TO);
                    String[][] expected = {{"MRN", MRN}, {"Bill No.", "OPB-THKD-0000060"}, {"Date", "12-Jan-26"}, {"Company Name", "SELFPAY / CASH CUSTOMER"},
                            {"Total Bill Amount", "13602.9"}, {"Total Discount", "222"}, {"Tax", "0"}, {"Net Amount", "13380.9"},
                            {"Approved By", "Employee 1303"}, {"Approved Amount", "222"}};
                    List<String> wrong = new ArrayList<>();
                    for (String[] e : expected) {
                        String actual = row.get(e[0]);
                        if (actual == null) return automationFailure("column '" + e[0] + "' not found | row " + row);
                        if (!sameValue(actual, e[1])) wrong.add(e[0] + " '" + actual + "' (expected '" + e[1] + "')");
                    }
                    List<String> netWrong = new ArrayList<>(), taxIncluded = new ArrayList<>();
                    for (Map<String, String> r : rows) {
                        double total = amount(r.get("Total Bill Amount")), disc = amount(r.get("Total Discount")), tax = amount(r.get("Tax")), net = amount(r.get("Net Amount"));
                        if (Double.isNaN(total) || Double.isNaN(net)) return automationFailure("amounts not readable | row " + r);
                        disc = Double.isNaN(disc) ? 0 : disc;
                        tax = Double.isNaN(tax) ? 0 : tax;
                        boolean exclusive = Math.abs(total - disc + tax - net) < 0.005, inclusive = Math.abs(total - disc - net) < 0.005;
                        String no = r.getOrDefault("Discount", "?");
                        if (!exclusive && !inclusive) netWrong.add(String.format("%s: %.2f - %.2f (+%.2f) != %.2f", no, total, disc, tax, net));
                        else if (!exclusive && tax > 0) taxIncluded.add(no);
                    }
                    String seen = DISCOUNT_NO + " " + row + "; Net checked on " + rows.size() + " rows"
                            + (taxIncluded.isEmpty() ? "" : "; please confirm the pricing rule - Net = Total - Discount, so tax looks included in the price, for " + taxIncluded);
                    if (!wrong.isEmpty() || !netWrong.isEmpty())
                        return defect((wrong.isEmpty() ? "" : "values " + wrong + "; ") + (netWrong.isEmpty() ? "" : "Net Amount wrong " + netWrong) + " | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(12) @DisplayName("Verify Add button navigation")
    void verifyAddButtonNavigation() {
        scenario("Verify Add button navigation",
                "Fresh list; click Add; check the screen (nothing is entered or saved); go back",
                "Add discount screen #/add-concessionManager opens with MRN, Find Patient and Save shown",
                () -> {
                    String[] notOpen = freshList();
                    if (notOpen != null) return notOpen;
                    boolean opened = screen.clickAdd();
                    String url = page.url();
                    boolean save = screen.isVisible("[ng-click='IUDBill()']"), mrn = screen.isVisible("[ng-model='ConcessionManager1.MRNO']");
                    String seen = "After Add: " + url + "; MRN shown " + mrn + "; Save shown " + save + " (not clicked)";
                    screen.goHome();
                    if (!opened) return defect(seen + " - Add did not open #/add-concessionManager");
                    return mrn && save ? passed(seen) : defect(seen + " - Add screen controls missing");
                });
    }

    @Test @Order(13) @DisplayName("Confirm no write request was sent")
    void confirmNoWriteRequestWasSent() {
        scenario("Confirm no write request was sent",
                "After all scenarios, read the write guard's log",
                "No Save / Approve / IUD request was attempted by this test",
                () -> screen.blockedWrites.isEmpty() ? passed("No write request attempted; " + screen.apiLog.size() + " read calls made")
                        : automationFailure("write requests were attempted (and aborted): " + screen.blockedWrites));
    }

    /** "12-Jan-26" -&gt; "12/01/2026"; other formats returned unchanged. */
    private static String toDdMmYyyy(String d) {
        try {
            return java.time.LocalDate.parse(d.trim(), java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yy", java.util.Locale.ENGLISH))
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) { return d; }
    }
}
