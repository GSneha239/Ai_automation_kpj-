package com.kpj.tests.Billing_page;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Billing &gt; Discount Manager/CR &gt; <b>Add</b> — READ-ONLY automated checks.
 *
 * <p>Source of truth: TestCases_Billing_DiscountManagerCR_v1.xlsx (Screen 2 - Add Discount). One JUnit test per scenario; each
 * reopens the screen (list &gt; Add) so none depends on another. Report: {@code test-output/reports/Billing_DiscountManagerAdd.html}.</p>
 *
 * <p><b>Read-only.</b> Values are entered, a bill is ticked and discounts are calculated on screen, but Save ({@code IUDBill()})
 * is never clicked; Clear and Back only reset / leave the form on the client. The {@code ReadOnlyScreen} guard aborts any write
 * request.</p>
 *
 * <p>Data (override with -D): {@code dma.mrn} 1100338591 (OPD; bills OPB000000413 AIA BHD. (EB) 1603.30 and OPB000000412
 * SELFPAY / CASH CUSTOMER 1055.50), {@code dma.bill} OPB000000412, {@code dma.invalidMrn} 9999999999.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Billing_page.DiscountManagerAdd" -Ddevhis.postrun=off
 * </pre>
 */
public class DiscountManagerAdd extends DiscountManagerTestBase<com.kpj.pages.Billing_page.DiscountManagerAdd> {

    private static final String MRN = System.getProperty("dma.mrn", "1100338591");
    private static final String BILL = System.getProperty("dma.bill", "OPB000000412");
    private static final String INVALID_MRN = System.getProperty("dma.invalidMrn", "9999999999");

    // The bill grid's first header cell is empty (the row checkboxes have no select-all), so it is not listed here.
    private static final List<String> BILL_COLUMNS = List.of("Date", "Bill No.", "MRN No.", "IPD No./OPD No.", "Patient Name",
            "Company Name", "Total Bill Amount", "Total Discount", "Tax", "Net Amount", "Self Pay", "Company Pay", "Self Balance", "Company Balance");
    private static final List<String> SERVICE_COLUMNS = List.of("Select checkbox", "Service Code", "Service Name", "Quantity", "Unit Purchase Price",
            "Total Amount", "Concession Percentage", "Discount Amount", "Tax", "Tax Amount", "Net Amount", "Self Amount", "Company Amount",
            "Hospital Share Amount", "Doctor Share Amount", "Share In Doctor Concession", "Doctor", "Previous Concession Amount", "Remark");

    public DiscountManagerAdd() { super("Billing_DiscountManagerAdd"); }

    @Override protected com.kpj.pages.Billing_page.DiscountManagerAdd createScreen(Page page) { return new com.kpj.pages.Billing_page.DiscountManagerAdd(page); }
    @Override protected String reportTitle() { return "Billing - Discount Manager/CR - Add (read-only)"; }
    @Override protected String reportNote() {
        return "Read-only checks from TestCases_Billing_DiscountManagerCR_v1.xlsx (Screen 2). Discounts are calculated on screen but "
                + "Save is never clicked; write calls are aborted in the browser. Patient " + MRN + ", bill " + BILL + ".";
    }
    @Override protected String dataSummary() { return "MRN " + MRN + " (OPD), bill " + BILL; }

    private String[] freshAdd() {
        screen.goHome();
        return screen.open() ? null : automationFailure("Discount Manager/CR > Add did not open (at " + page.url() + ")");
    }

    /** Fresh Add screen, Reg. Type OPD, patient {@link #MRN} found with bills listed; null = ready. */
    private String[] patientLoaded() {
        String[] notOpen = freshAdd();
        if (notOpen != null) return notOpen;
        screen.setAddRegType("0");
        screen.setMrn(MRN);
        int status = screen.findPatient();
        if (status == 0) return automationFailure("Find Patient sent no patient lookup");
        if (status != 200) return defect("patient lookup answered HTTP " + status);
        if (screen.patientBanner().isEmpty()) return dataMissing("MRN " + MRN + " not found on " + BASE);
        if (screen.billRows().isEmpty()) return dataMissing("no bills listed for " + MRN + " on " + BASE);
        return null;
    }

    /** Patient loaded and {@link #BILL} ticked with its services listed; null = ready. */
    private String[] billSelected() {
        String[] notReady = patientLoaded();
        if (notReady != null) return notReady;
        if (screen.billRow(BILL).isEmpty()) return dataMissing("bill " + BILL + " not listed for " + MRN);
        if (!screen.selectBill(BILL)) return automationFailure("services did not load after ticking " + BILL);
        return null;
    }

    private static List<String> missing(List<String> expected, List<String> actual) {
        List<String> m = new ArrayList<>(expected);
        if (actual != null) m.removeAll(actual);
        return m;
    }

    // ---- scenarios -------------------------------------------------------------------------

    @Test @Order(1) @DisplayName("Open the Add Discount screen")
    void openAddDiscountScreen() {
        scenario("Open the Add Discount screen",
                "Billing > Discount Manager/CR; click Add",
                "Add screen #/add-concessionManager opens with no error; header 'Discount Manager/CR'",
                () -> {
                    int failuresBefore = screen.apiFailures.size();
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    String header = screen.headerText(), error = screen.visibleErrorText() + reportPageError(page);
                    List<String> apiErrors = new ArrayList<>(screen.apiFailures.subList(failuresBefore, screen.apiFailures.size()));
                    apiErrors.removeIf(f -> !f.contains("/api/"));
                    String seen = "Opened " + page.url() + "; header '" + header + "'";
                    if (!error.isEmpty() || !apiErrors.isEmpty()) return defect(seen + "; error '" + error + "'; failed API calls " + apiErrors);
                    return header.toLowerCase().startsWith("discount manager/cr") ? passed(seen) : defect(seen + " - header is not 'Discount Manager/CR'");
                });
    }

    @Test @Order(2) @DisplayName("Verify screen layout and grid columns")
    void verifyLayoutAndGridColumns() {
        scenario("Verify screen layout and grid columns",
                "Fresh Add screen; check every control (Save for presence only); read the bill grid headers; load "
                        + MRN + " / " + BILL + " and read the services grid headers",
                "Controls " + com.kpj.pages.Billing_page.DiscountManagerAdd.LAYOUT.keySet() + " shown; bill grid " + BILL_COLUMNS
                        + "; services grid " + SERVICE_COLUMNS,
                () -> {
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    Map<String, Boolean> present = screen.presence(com.kpj.pages.Billing_page.DiscountManagerAdd.LAYOUT);
                    List<String> missingCtrls = new ArrayList<>();
                    present.forEach((k, v) -> { if (!v) missingCtrls.add(k); });
                    List<String> billHeaders = screen.tableHeaders(com.kpj.pages.Billing_page.DiscountManagerAdd.BILL_GRID);
                    String[] notSelected = billSelected();
                    if (notSelected != null) return notSelected;
                    List<String> serviceHeaders = screen.tableHeaders(com.kpj.pages.Billing_page.DiscountManagerAdd.SERVICE_GRID);
                    List<String> missBill = missing(BILL_COLUMNS, billHeaders), missSvc = missing(SERVICE_COLUMNS, serviceHeaders);
                    String seen = "Missing controls " + missingCtrls + "; bill headers " + billHeaders + "; service headers " + serviceHeaders;
                    if (billHeaders == null || serviceHeaders == null) return automationFailure("grid not found | " + seen);
                    if (!missingCtrls.isEmpty() || !missBill.isEmpty() || !missSvc.isEmpty())
                        return defect("missing controls " + missingCtrls + ", bill columns " + missBill + ", service columns " + missSvc + " | " + seen);
                    return passed(seen);
                });
    }

    @Test @Order(3) @DisplayName("Verify default field values")
    void verifyDefaultFieldValues() {
        scenario("Verify default field values",
                "Fresh Add screen; read the defaults (nothing entered)",
                "As in the workbook: Reg. Type = IPD, Bill Wise selected, Amount unticked, Discount Amount disabled, Concession Percentage "
                        + "enabled, Exclude Dr.Amount ticked, Cash Counter = -Select-",
                () -> {
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    String reg = screen.addRegTypeChecked(), on = screen.concessionOnChecked();
                    boolean amount = screen.isChecked(com.kpj.pages.Billing_page.DiscountManagerAdd.AMOUNT_CHECKBOX),
                            amountDisabled = screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.DISCOUNT_AMOUNT),
                            pctDisabled = screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.PERCENTAGE),
                            exclude = screen.isChecked(com.kpj.pages.Billing_page.DiscountManagerAdd.EXCLUDE_DR);
                    String counter = screen.selectedText(com.kpj.pages.Billing_page.DiscountManagerAdd.CASH_COUNTER);
                    String seen = "Reg. Type " + ("0".equals(reg) ? "OPD" : "1".equals(reg) ? "IPD" : reg) + "; " + ("1".equals(on) ? "Bill Wise" : "Service Wise")
                            + "; Amount " + amount + "; Discount Amount disabled " + amountDisabled + "; Percentage disabled " + pctDisabled
                            + "; Exclude Dr.Amount " + exclude + "; Cash Counter '" + counter + "'";
                    List<String> wrong = new ArrayList<>();
                    if (!"1".equals(reg)) wrong.add("Reg. Type is not IPD");
                    if (!"1".equals(on)) wrong.add("Bill Wise not selected");
                    if (amount) wrong.add("Amount ticked");
                    if (!amountDisabled) wrong.add("Discount Amount editable");
                    if (pctDisabled) wrong.add("Concession Percentage disabled");
                    if (!exclude) wrong.add("Exclude Dr.Amount unticked");
                    if (!counter.toLowerCase().contains("select")) wrong.add("Cash Counter preselected");
                    String note = "1".equals(reg) ? " | Add also defaults to IPD, as the list screen does; the workbook records IPD here - please confirm the intended default." : "";
                    return wrong.isEmpty() ? passed(seen + note) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(4) @DisplayName("Patient lookup with empty MRN")
    void patientLookupWithEmptyMrn() {
        scenario("Patient lookup with empty MRN",
                "Fresh Add screen; MRN empty; click Find Patient",
                "Warning 'Please Enter MRN No.'",
                () -> {
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    screen.findPatientExpectingValidation();
                    List<String> messages = screen.waitForAlert(4000);
                    String seen = "Messages " + messages;
                    return messages.stream().anyMatch(m -> m.toLowerCase().contains("please enter mrn")) ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(5) @DisplayName("Patient lookup with invalid MRN")
    void patientLookupWithInvalidMrn() {
        scenario("Patient lookup with invalid MRN",
                "Fresh Add screen; Reg. Type OPD; MRN = " + INVALID_MRN + "; click Find Patient",
                "Message 'Patient Not Found'; no banner and no bills",
                () -> {
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    screen.setAddRegType("0");
                    screen.setMrn(INVALID_MRN);
                    int status = screen.findPatient();
                    if (status == 0) return automationFailure("Find Patient sent no patient lookup");
                    List<String> messages = screen.waitForAlert(3000);
                    String banner = screen.patientBanner();
                    int bills = screen.billRows().size();
                    String seen = "Lookup HTTP " + status + "; messages " + messages + "; banner '" + trim(banner, 60) + "'; bills " + bills;
                    if (!banner.isEmpty() || bills > 0) return defect(seen + " - patient data shown for an unknown MRN");
                    if (messages.stream().anyMatch(m -> m.toLowerCase().contains("not found"))) return passed(seen);
                    return defect(seen + " - " + (messages.isEmpty() ? "no message tells the user the MRN was not found" : "message does not say 'Patient Not Found'"));
                });
    }

    @Test @Order(6) @DisplayName("Search OPD patient 1100338591 and verify bills")
    void searchOpdPatientAndVerifyBills() {
        scenario("Search OPD patient " + MRN + " and verify bills",
                "Fresh Add screen; Reg. Type OPD; MRN = " + MRN + "; click Find Patient; read the bill grid",
                "Banner shows MRN " + MRN + "; bills OPB000000413 - AIA BHD. (EB) - 1603.30 and OPB000000412 - SELFPAY / CASH CUSTOMER - 1055.50",
                () -> {
                    String[] notReady = patientLoaded();
                    if (notReady != null) return notReady;
                    String banner = screen.patientBanner();
                    String[][] expected = {{"OPB000000413", "AIA BHD. (EB)", "1603.30"}, {"OPB000000412", "SELFPAY / CASH CUSTOMER", "1055.50"}};
                    List<String> wrong = new ArrayList<>(), shown = new ArrayList<>();
                    for (String[] e : expected) {
                        Map<String, String> row = screen.billRow(e[0]);
                        if (row.isEmpty()) return dataMissing("bill " + e[0] + " not listed for " + MRN + " | bills " + screen.billRows());
                        shown.add(e[0] + " " + row.get("Company Name") + " " + row.get("Total Bill Amount"));
                        if (!e[1].equalsIgnoreCase(row.getOrDefault("Company Name", ""))) wrong.add(e[0] + " Company '" + row.get("Company Name") + "'");
                        if (!sameValue(row.getOrDefault("Total Bill Amount", ""), e[2])) wrong.add(e[0] + " Total '" + row.get("Total Bill Amount") + "'");
                    }
                    String seen = "Banner '" + trim(banner, 100) + "'; bills " + shown;
                    if (!banner.contains(MRN)) wrong.add("banner does not show MRN " + MRN);
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(7) @DisplayName("Select a bill and verify previous discount and services grids")
    void selectBillAndVerifyGrids() {
        scenario("Select bill " + BILL + " and verify previous discount and services grids",
                "Load " + MRN + "; tick bill " + BILL + "; read the previous discount grid, the services grid and the discount fields",
                "Previous discount grid shown (no earlier discount for this bill); services listed and all ticked; Concession Percentage / "
                        + "Discount Amount 0; Authorized By and Reason reset",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    List<Map<String, String>> previous = screen.previousDiscountRows();
                    List<Map<String, String>> services = screen.serviceRows();
                    long ticked = services.stream().filter(s -> "true".equals(s.get("Select checkbox"))).count();
                    double servicesTotal = services.stream().mapToDouble(s -> { double v = amount(s.get("Total Amount")); return Double.isNaN(v) ? 0 : v; }).sum();
                    double billTotal = amount(screen.billRow(BILL).get("Total Bill Amount"));
                    String pct = screen.percentage(), amt = screen.discountAmount();
                    String auth = screen.selectedText(com.kpj.pages.Billing_page.DiscountManagerAdd.AUTHORIZED_BY),
                            reason = screen.selectedText(com.kpj.pages.Billing_page.DiscountManagerAdd.REASON);
                    String seen = String.format("Previous discount rows %s; services %d (ticked %d); services total %.2f vs bill total %.2f; percentage '%s', amount '%s'; Authorized By '%s', Reason '%s'",
                            previous == null ? "grid not found" : String.valueOf(previous.size()), services.size(), ticked, servicesTotal, billTotal, pct, amt, auth, reason);
                    if (Math.abs(servicesTotal - billTotal) > 0.005)
                        seen += " | please confirm: the services listed do not add up to the selected bill's total";
                    if (previous == null) return automationFailure("previous discount grid not found | " + seen);
                    List<String> wrong = new ArrayList<>();
                    if (services.isEmpty()) wrong.add("no services listed");
                    if (ticked != services.size()) wrong.add("not every service is ticked");
                    if (amount(pct) != 0 && !pct.isEmpty()) wrong.add("percentage not reset");
                    if (!amt.isEmpty() && amount(amt) != 0) wrong.add("discount amount not reset");
                    if (!auth.toLowerCase().contains("select")) wrong.add("Authorized By not reset");
                    if (!reason.toLowerCase().contains("select")) wrong.add("Reason not reset");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(8) @DisplayName("Verify 10% bill-wise concession calculation")
    void verifyTenPercentConcession() {
        scenario("Verify 10% concession calculation",
                "Load " + MRN + "; tick " + BILL + "; Bill Wise, Amount unticked; type Concession Percentage 10 (Tab); read every service (Save NOT clicked)",
                "Each service without a doctor share: Concession % 10, Discount = 10% of Total rounded to the nearest 0.05, Net = Total - Discount; "
                        + "header Discount Amount = sum of the service discounts",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    screen.typePercentage("10");
                    List<Map<String, String>> services = screen.serviceRows();
                    List<String> wrong = new ArrayList<>(), doctorShare = new ArrayList<>();
                    double sum = 0;
                    for (Map<String, String> s : services) {
                        String code = s.getOrDefault("Service Code", "?");
                        double total = amount(s.get("Total Amount")), pct = amount(s.get("Concession Percentage")), disc = amount(s.get("Discount Amount")),
                                net = amount(s.get("Net Amount")), dr = amount(s.get("Doctor Share Amount"));
                        if (Double.isNaN(total) || Double.isNaN(disc) || Double.isNaN(net)) return automationFailure("service amounts not readable | " + s);
                        sum += disc;
                        if (!Double.isNaN(dr) && dr > 0) { doctorShare.add(String.format("%s total %.2f doctor %.2f disc %.2f net %.2f", code, total, dr, disc, net)); continue; }
                        double expDisc = roundToNearest005(total * 0.10);
                        if (Double.isNaN(pct) || Math.abs(pct - 10) > 0.005) wrong.add(code + " % '" + s.get("Concession Percentage") + "'");
                        if (Math.abs(disc - expDisc) > 0.005) wrong.add(String.format("%s discount %.2f (expected %.2f)", code, disc, expDisc));
                        if (Math.abs(total - disc - net) > 0.005) wrong.add(String.format("%s net %.2f (expected %.2f)", code, net, total - disc));
                    }
                    double header = amount(screen.discountAmount());
                    String seen = String.format("%d services; sum of discounts %.2f; header Discount Amount '%s'", services.size(), sum, screen.discountAmount())
                            + (doctorShare.isEmpty() ? "" : "; please confirm the rule for services with a doctor share (not checked against 10%): " + doctorShare)
                            + "; Save not clicked";
                    if (services.isEmpty()) return automationFailure("no services after typing 10%");
                    if (Double.isNaN(header) || Math.abs(header - sum) > 0.005) wrong.add("header Discount Amount does not equal the sum of service discounts");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(9) @DisplayName("Validate concession percentage greater than 100")
    void validatePercentageAbove100() {
        scenario("Validate percentage greater than 100",
                "Load " + MRN + "; tick " + BILL + "; type Concession Percentage 150 (Tab)",
                "Warning 'Please Enter Valid Concession Percentage'; percentage reset to 0; no discount applied",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    screen.typePercentage("150");
                    List<String> messages = screen.waitForAlert(3000);
                    String pct = screen.percentage(), amt = screen.discountAmount();
                    double discounts = screen.serviceRows().stream().mapToDouble(s -> { double v = amount(s.get("Discount Amount")); return Double.isNaN(v) ? 0 : v; }).sum();
                    String seen = "Messages " + messages + "; percentage '" + pct + "'; Discount Amount '" + amt + "'; sum of service discounts " + String.format("%.2f", discounts);
                    boolean warned = messages.stream().anyMatch(m -> m.toLowerCase().contains("valid concession percentage"));
                    boolean reset = pct.isEmpty() || amount(pct) == 0;
                    if (!warned) return defect(seen + " - no validation message for 150%");
                    if (!reset || discounts > 0.005) return defect(seen + " - 150% was not reset / a discount is still applied");
                    return passed(seen);
                });
    }

    @Test @Order(10) @DisplayName("Verify Amount checkbox behaviour")
    void verifyAmountCheckboxBehaviour() {
        scenario("Verify Amount checkbox behaviour",
                "Load " + MRN + "; tick " + BILL + "; tick Amount; read the two discount fields; untick Amount; read them again",
                "Amount ticked: Discount Amount editable, Concession Percentage disabled. Amount unticked: Percentage editable, Discount Amount disabled",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    screen.setAmountMode(true);
                    boolean amtEditOn = !screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.DISCOUNT_AMOUNT),
                            pctDisabledOn = screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.PERCENTAGE);
                    screen.setAmountMode(false);
                    boolean amtDisabledOff = screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.DISCOUNT_AMOUNT),
                            pctEditOff = !screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.PERCENTAGE);
                    String seen = "Ticked: amount editable " + amtEditOn + ", percentage disabled " + pctDisabledOn
                            + " | unticked: amount disabled " + amtDisabledOff + ", percentage editable " + pctEditOff;
                    return amtEditOn && pctDisabledOn && amtDisabledOff && pctEditOff ? passed(seen) : defect(seen);
                });
    }

    @Test @Order(11) @DisplayName("Verify Bill Wise and Service Wise behaviour")
    void verifyBillWiseAndServiceWise() {
        scenario("Verify Bill Wise and Service Wise behaviour",
                "Load " + MRN + "; tick " + BILL + "; select Service Wise; read services and header percentage; select Bill Wise; read services",
                "Service Wise: all services unticked, service percentages 0, header Concession Percentage disabled. Bill Wise: all services ticked again",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    screen.setConcessionOn("0");
                    List<Map<String, String>> sw = screen.serviceRows();
                    long swTicked = sw.stream().filter(s -> "true".equals(s.get("Select checkbox"))).count();
                    boolean swZero = sw.stream().allMatch(s -> { double v = amount(s.get("Concession Percentage")); return Double.isNaN(v) || v == 0; });
                    boolean headerDisabled = screen.isDisabled(com.kpj.pages.Billing_page.DiscountManagerAdd.PERCENTAGE);
                    screen.setConcessionOn("1");
                    List<Map<String, String>> bw = screen.serviceRows();
                    long bwTicked = bw.stream().filter(s -> "true".equals(s.get("Select checkbox"))).count();
                    String seen = "Service Wise: " + swTicked + "/" + sw.size() + " ticked, percentages 0 " + swZero + ", header percentage disabled " + headerDisabled
                            + " | Bill Wise: " + bwTicked + "/" + bw.size() + " ticked";
                    List<String> wrong = new ArrayList<>();
                    if (swTicked != 0) wrong.add("Service Wise left services ticked");
                    if (!swZero) wrong.add("Service Wise kept percentages");
                    if (!headerDisabled) wrong.add("header percentage still editable in Service Wise");
                    if (bwTicked != bw.size() || bw.isEmpty()) wrong.add("Bill Wise did not tick every service");
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(12) @DisplayName("Verify Clear resets the form")
    void verifyClearResetsForm() {
        scenario("Verify Clear button resets the form",
                "Load " + MRN + "; tick " + BILL + "; type Concession Percentage 10; click Clear; read the form",
                "MRN empty, banner gone, bill and services grids empty, Reg. Type back to IPD, Concession Percentage and Discount Amount reset",
                () -> {
                    String[] notReady = billSelected();
                    if (notReady != null) return notReady;
                    screen.typePercentage("10");
                    screen.clickClear();
                    String mrn = screen.inputValue(com.kpj.pages.Billing_page.DiscountManagerAdd.MRN), pct = screen.percentage(), amt = screen.discountAmount(),
                            reg = screen.addRegTypeChecked(), banner = screen.patientBanner();
                    int bills = screen.billRows().size(), services = screen.serviceRows().size();
                    String seen = "MRN '" + (mrn == null ? "" : mrn) + "'; banner '" + trim(banner, 40) + "'; bills " + bills + "; services " + services
                            + "; Reg. Type " + ("1".equals(reg) ? "IPD" : "0".equals(reg) ? "OPD" : reg) + "; percentage '" + pct + "'; Discount Amount '" + amt + "'";
                    List<String> wrong = new ArrayList<>();
                    if (mrn != null && !mrn.isBlank()) wrong.add("MRN not cleared");
                    if (!banner.isEmpty()) wrong.add("banner still shown");
                    if (bills > 0 || services > 0) wrong.add("grids not cleared");
                    if (!"1".equals(reg)) wrong.add("Reg. Type not reset to IPD");
                    if (!pct.isEmpty() && amount(pct) != 0) wrong.add("Concession Percentage still shows " + pct);
                    if (!amt.isEmpty() && amount(amt) != 0) wrong.add("Discount Amount still shows " + amt);
                    return wrong.isEmpty() ? passed(seen) : defect(String.join("; ", wrong) + " | " + seen);
                });
    }

    @Test @Order(13) @DisplayName("Verify Back navigation")
    void verifyBackNavigation() {
        scenario("Verify Back navigation",
                "Fresh Add screen; click Back",
                "Returns to the discount list #/ConcessionManagerList",
                () -> {
                    String[] notOpen = freshAdd();
                    if (notOpen != null) return notOpen;
                    boolean back = screen.clickBack();
                    String seen = "After Back: " + page.url();
                    return back ? passed(seen) : defect(seen + " - the list was not shown");
                });
    }

    @Test @Order(14) @DisplayName("Confirm no Save or write request was sent")
    void confirmNoWriteRequestWasSent() {
        scenario("Confirm no Save or write request was sent",
                "After all scenarios, read the write guard's log",
                "No Save / Approve / IUD request was attempted by this test",
                () -> screen.blockedWrites.isEmpty() ? passed("No write request attempted; " + screen.apiLog.size() + " read calls made")
                        : automationFailure("write requests were attempted (and aborted): " + screen.blockedWrites));
    }
}
