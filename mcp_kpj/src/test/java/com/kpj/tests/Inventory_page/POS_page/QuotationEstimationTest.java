package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Inventory_page.POS_page.QuotationEstimation;

import java.util.List;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Inventory &gt; POS &gt; <b>Quotation Estimation</b> — READ-ONLY automated checks (QE-01 … QE-18).
 *
 * <p>Built from the manual execution of 13/11/2026 (Quotation_Estimation_Manual_Report.html).</p>
 *
 * <p><b>Safety:</b> on this screen both Submit AND Print post {@code api/BillEstimation/IUD}
 * (INSERTBILLESTIMATION) - Print saves the quotation before printing. Those buttons are only clicked after
 * {@code guardVerified()} proves the browser-side write guard is aborting IUD calls; otherwise the case is
 * SKIPPED. A blocked write is reported as what it is (the button tried to save). Expected results describe correct
 * behaviour - the item-lookup 400, the TypeErrors and the save-on-print are expected to FAIL.</p>
 *
 * <pre>
 *   mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.QuotationEstimationTest" -Ddevhis.postrun=off
 *   report: test-output/reports/Inventory_POS_QuotationEstimation.html
 * </pre>
 */
public class QuotationEstimationTest extends DevHisBase {

    private static final String TEMPLATE_NAME = System.getProperty("qe.billTemplate", "testing");
    private static final String TARIFF_NAME = System.getProperty("qe.tariff", "SELFPAY / CASH CUSTOMER");
    private static final String CLASS_NAME = System.getProperty("qe.class", "Out Patient");
    private int pass, fail, skipped, blocked;

    public QuotationEstimationTest() { super("Inventory_POS_QuotationEstimation"); }

    public static void main(String[] args) {
        QuotationEstimationTest t = new QuotationEstimationTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private interface Check { String[] run() throws Exception; }

    private void tc(String id, String name, String desc, String expected, Check c) {
        String status, actual;
        try {
            String[] r = c.run();
            status = r[0];
            actual = r[1];
        } catch (Throwable e) {
            status = "FAIL";
            actual = "Exception: " + String.valueOf(e.getMessage()).split("\\R")[0];
        }
        step(page, id + " " + name, desc, expected, actual, status);
        switch (status) {
            case "PASS": pass++; break;
            case "SKIPPED": skipped++; break;
            case "BLOCKED": blocked++; break;
            default: fail++;
        }
    }

    private static String[] r(boolean ok, String actual) { return new String[]{ok ? "PASS" : "FAIL", actual}; }

    /** New External-Patient quotation with the bill template's services added. Returns "" or a reason it could not be prepared. */
    private String prepareExternalWithTemplate(QuotationEstimation qe) {
        if (!qe.openAddForm()) return "Add form did not open";
        qe.clickRegTypeRadio("2");
        if (qe.pickOption(QuotationEstimation.TEMPLATE, TEMPLATE_NAME).isEmpty()) return "Bill Template '" + TEMPLATE_NAME + "' not available";
        if (qe.pickOption(QuotationEstimation.TARIFF, TARIFF_NAME).isEmpty()) return "Pricing Policy '" + TARIFF_NAME + "' not available";
        page.waitForTimeout(1200);   // Class list follows the Pricing Policy
        if (qe.pickOption(QuotationEstimation.CLASS, CLASS_NAME).isEmpty()) return "Class '" + CLASS_NAME + "' not available";
        qe.clickAddBillTemplate();
        return qe.serviceRows().isEmpty() ? "Template added no services (" + qe.alerts() + ")" : "";
    }

    @Override
    protected void body() {
        meta("Inventory - POS - Quotation Estimation (read-only)",
                "Inventory > POS > Quotation Estimation",
                "Read-only checks derived from the 13/11/2026 manual execution. Submit and Print post api/BillEstimation/IUD; "
                        + "the browser-side guard aborts it, so no quotation is created.");

        QuotationEstimation qe = new QuotationEstimation(page);
        qe.installGuards();
        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login (existing LoginPage)", "Authenticated", "Logged in at " + page.url(), "PASS");

        tc("QE-01", "Page navigation", "Inventory > POS > Quotation Estimation (sidebar)",
                "List screen #/QuotationEstimation opens (first load can take ~40 s)",
                () -> { boolean ok = qe.openList(); return r(ok, ok ? "Opened " + page.url() : "Screen did not open; at " + page.url()); });

        tc("QE-02", "Page title / header", "Read the header", "Header contains 'QUOTATION ESTIMATION'",
                () -> {
                    qe.openList();
                    assertThat(page.locator(".page_title").first()).containsText("QUOTATION ESTIMATION",
                            new com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions().setIgnoreCase(true).setTimeout(15000));
                    return r(true, "Header: " + qe.headerText() + " | grid columns " + qe.uiGridHeaders("gridOptions"));
                });

        tc("QE-03", "List search / empty state", "Search 01/01/2020 to today", "API 200; grid shows saved quotations (or empty)",
                () -> {
                    qe.openList();
                    qe.setListDates("01/01/2020", QuotationEstimation.today());
                    int s = qe.searchList();
                    return r(s == 200, "API " + s + ", rows=" + qe.uiGridRows("gridOptions"));
                });

        tc("QE-04", "Add form / visible input fields", "Click Add",
                "#/add-QuotationEstimation with MRN, Reg. Type, Name*, Last Name*, Payor, Pricing Policy, Class, Store, Bill Template, Submit, Print",
                () -> {
                    boolean ok = qe.openAddForm();
                    boolean fields = qe.isVisible("[ng-model='BillEstimation.MRNo']") && qe.isVisible("[ng-model='BillEstimation.firstName']")
                            && qe.isVisible("[ng-model='BillEstimation.lastName']") && qe.optionCount("BillEstimation.companyid") > 1
                            && qe.optionCount(QuotationEstimation.TARIFF) > 1 && qe.optionCount("BillEstimation.storeid") > 1
                            && qe.isVisible("[ng-click='IUDBillEstimation()']") && qe.isVisible("[ng-click='PrintBillEstimation()']");
                    return r(ok && fields, "At " + page.url() + " | Payor options=" + qe.optionCount("BillEstimation.companyid")
                            + ", Pricing Policy=" + qe.optionCount(QuotationEstimation.TARIFF) + ", Bill Templates=" + (qe.optionCount(QuotationEstimation.TEMPLATE) - 1));
                });

        tc("QE-05", "Reg. Type label 'External Patient.'", "Click the 'External Patient.' text",
                "External Patient (value 2) is selected",
                () -> {
                    qe.openAddForm();
                    qe.clickExternalPatientLabel();
                    String v = qe.regType();
                    return r("2".equals(v), "Reg. Type model = " + v + ("1".equals(v) ? " (IPD selected - IPD and External Patient radios share id 'radios-1')" : ""));
                });

        tc("QE-06", "External Patient item row", "Click the External Patient radio itself",
                "Item Code / Item Name / Quantity / Unit Price row appears",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("2");
                    return r("2".equals(qe.regType()) && qe.itemRowVisible(), "Reg. Type=" + qe.regType() + ", item row visible=" + qe.itemRowVisible());
                });

        tc("QE-07", "Item lookup by Item Name", "External Patient, type 'Cepr' in Item Name",
                "Item API answers 200 and suggestions appear",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("2");
                    String[] res = qe.typeItemLookup("#txtServiceName", "Cepr", 8000);
                    if (res == null) return r(false, "No item lookup request was sent");
                    return r("200".equals(res[0]) && qe.suggestionCount() > 0, "HTTP " + res[0] + " - " + res[1] + " | suggestions=" + qe.suggestionCount());
                });

        tc("QE-08", "Item lookup by Item Code", "External Patient, type '10' in Item Code",
                "Item API is called and answers 200; no script error",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("2");
                    int errBefore = qe.consoleErrors.size();
                    String[] res = qe.typeItemLookup("#txtItemCode", "10", 6000);
                    List<String> newErr = qe.consoleErrors.subList(errBefore, qe.consoleErrors.size());
                    if (res == null) return r(false, "No item lookup request was sent | new console errors: " + newErr);
                    return r("200".equals(res[0]) && newErr.isEmpty(), "HTTP " + res[0] + " | new console errors: " + newErr);
                });

        tc("QE-09", "Quantity and rate fields (item add)", "Add a looked-up item with Quantity and Unit Price",
                "Item line added with amount = quantity x price",
                () -> new String[]{"BLOCKED", "Depends on the item lookup (QE-07 / QE-08), which returns no items on this build"});

        tc("QE-10", "Bill Template validations", "Add Bill Template with no template, then no tariff, then no class",
                "'Please Select Bill Template', 'Please Select At least One Tariff', 'Please Select Class'",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("2");
                    qe.clickAddBillTemplate();
                    List<String> a1 = qe.alerts();
                    qe.pickOption(QuotationEstimation.TEMPLATE, TEMPLATE_NAME);
                    qe.pickOptionAt(QuotationEstimation.TARIFF, 0);
                    qe.clickAddBillTemplate();
                    List<String> a2 = qe.alerts();
                    qe.pickOption(QuotationEstimation.TARIFF, TARIFF_NAME);
                    qe.pickOptionAt(QuotationEstimation.CLASS, 0);
                    qe.clickAddBillTemplate();
                    List<String> a3 = qe.alerts();
                    boolean ok = String.valueOf(a1).toLowerCase().contains("bill template") && String.valueOf(a2).toLowerCase().contains("tariff")
                            && String.valueOf(a3).toLowerCase().contains("class");
                    return r(ok, "No template: " + a1 + " | no tariff: " + a2 + " | no class: " + a3);
                });

        tc("QE-11", "Add item via Bill Template", "External Patient, template '" + TEMPLATE_NAME + "', " + TARIFF_NAME + ", " + CLASS_NAME,
                "Template services added to the grid",
                () -> {
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    List<String[]> rows = qe.serviceRows();
                    return r(!rows.isEmpty(), rows.size() + " line(s): " + String.join(" | ", rows.get(0)) + " | total " + qe.total());
                });

        tc("QE-12", "Calculation: line and total", "Check every line and the Total Bill Amount",
                "Line total = quantity x unit price; Total Bill Amount = sum of lines",
                () -> {
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    double sum = 0;
                    StringBuilder sb = new StringBuilder();
                    boolean lines = true;
                    for (String[] row : qe.serviceRows()) {
                        double q = QuotationEstimation.money(row[2]), p = QuotationEstimation.money(row[3]), t = QuotationEstimation.money(row[4]);
                        lines &= Math.abs(q * p - t) < 0.005;
                        sum += t;
                        sb.append(row[0]).append(": ").append(row[2]).append(" x ").append(row[3]).append(" = ").append(row[4]).append("; ");
                    }
                    double total = QuotationEstimation.money(qe.total());
                    return r(lines && Math.abs(total - sum) < 0.005, sb + "sum " + String.format("%.2f", sum) + " vs Total Bill Amount " + qe.total());
                });

        tc("QE-13", "Duplicate template add", "Add the same Bill Template twice",
                "The service is not duplicated (or a warning is shown)",
                () -> {
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    int before = qe.serviceRows().size();
                    qe.clickAddBillTemplate();
                    int after = qe.serviceRows().size();
                    List<String> a = qe.alerts();
                    return r(after == before || !a.isEmpty(), "Lines " + before + " -> " + after + ", total " + qe.total() + ", messages " + a);
                });

        tc("QE-14", "Add / remove item", "Remove every service line",
                "Lines are removed and the Total Bill Amount returns to 0 / empty",
                () -> {
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    String start = qe.total();
                    while (!qe.serviceRows().isEmpty()) qe.removeServiceRow(0);
                    String end = qe.total();
                    return r(QuotationEstimation.money(end) == 0, "Total " + start + " -> " + (end.isEmpty() ? "(empty)" : end) + " with 0 lines");
                });

        tc("QE-15", "Mandatory field validation on Submit", "External Patient with a service, Name* and Last Name* empty, click Submit",
                "Validation message for Name / Last Name; NO save request",
                () -> {
                    if (!qe.guardVerified()) return new String[]{"SKIPPED", "Write guard could not be verified - Submit not clicked"};
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    int before = qe.blockedWrites.size();
                    qe.clickSubmit();
                    List<String> a = qe.waitForAlert(2000);
                    boolean wrote = qe.blockedWrites.size() > before;
                    return r(!wrote && !a.isEmpty(), "Messages: " + (a.isEmpty() ? "(none)" : a) + " | save request attempted: " + wrote
                            + (wrote ? " -> " + qe.blockedWrites.get(qe.blockedWrites.size() - 1) + " (aborted by the read-only guard)" : ""));
                });

        tc("QE-16", "Print an unsaved quotation", "External Patient with a service, click Print (not saved)",
                "Print previews / prints without saving, or asks to save first - no silent save request",
                () -> {
                    if (!qe.guardVerified()) return new String[]{"SKIPPED", "Write guard could not be verified - Print not clicked"};
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    qe.setNgInput(QuotationEstimation.FIRST_NAME, "QA");
                    qe.setNgInput(QuotationEstimation.LAST_NAME, "ReadOnly");
                    int before = qe.blockedWrites.size();
                    qe.clickPrint();
                    boolean wrote = qe.blockedWrites.size() > before;
                    List<String> opened = qe.openedUrls();
                    return r(!wrote, wrote ? "Print sent a SAVE request (aborted by the guard, so no report could open): "
                            + qe.blockedWrites.get(qe.blockedWrites.size() - 1)
                            : "No save request; report URLs " + opened + " messages " + qe.alerts());
                });

        tc("QE-17", "Customer / patient lookup", "OPD: MRN search blank, unknown MRN, then patient-search popup",
                "Blank -> 'Please Enter MRN'; unknown MRN -> a not-found message",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("0");
                    qe.clearCaptured();
                    qe.clickNg("SearchPatientByMRNo();");
                    List<String> blank = qe.waitForAlert(2000);
                    qe.setNgInput("BillEstimation.MRNo", "ZZ999999");
                    qe.clearCaptured();
                    qe.clickNg("SearchPatientByMRNo();");
                    List<String> unknown = qe.waitForAlert(3000);
                    qe.clickNg("openPopupScreen();");
                    page.waitForTimeout(2500);
                    int popup = qe.openModals();
                    qe.dismissModals();
                    boolean ok = String.valueOf(blank).toLowerCase().contains("mrn") && !unknown.isEmpty() && popup > 0;
                    return r(ok, "Blank MRN: " + blank + " | unknown MRN: " + (unknown.isEmpty() ? "(no message)" : unknown) + " | patient popup opened: " + (popup > 0));
                });

        tc("QE-18", "OPD buttons without a patient", "OPD, no patient, click Add Bill Template",
                "'Please Enter MRN' message; no script error",
                () -> {
                    qe.openAddForm();
                    qe.clickRegTypeRadio("0");
                    int errBefore = qe.consoleErrors.size();
                    qe.clickAddBillTemplate();
                    List<String> a = qe.alerts();
                    List<String> newErr = qe.consoleErrors.subList(errBefore, qe.consoleErrors.size());
                    return r(!a.isEmpty() && newErr.isEmpty(), "Messages: " + (a.isEmpty() ? "(none)" : a) + " | new console errors: " + newErr);
                });

        tc("QE-19", "Save a quotation (Submit)", "Would create a quotation", "(write operation - not executed)",
                () -> new String[]{"SKIPPED", "Read-only run: saving a quotation modifies data. Covered as MANUAL."});

        tc("QE-20", "Cancel and Back", "Cancel clears the form; Back returns to the list",
                "Form fields and grid cleared; Back opens #/QuotationEstimation",
                () -> {
                    String why = prepareExternalWithTemplate(qe);
                    if (!why.isEmpty()) return new String[]{"BLOCKED", why};
                    qe.clickCancel();
                    boolean cleared = qe.serviceRows().isEmpty();
                    qe.clickBack();
                    return r(cleared && qe.isOnRoute(QuotationEstimation.LIST_ROUTE), "Grid cleared=" + cleared + ", after Back at " + page.url());
                });

        tc("QE-21", "API requests and console errors", "Everything logged during this run",
                "No HTTP >= 400 and no console / page errors",
                () -> r(qe.apiFailures.isEmpty() && qe.consoleErrors.isEmpty(),
                        "HTTP>=400: " + qe.apiFailures + " | console: " + qe.consoleErrors + " | API calls logged: " + qe.apiLog.size()));

        addSummary("Mode", "READ-ONLY (write calls aborted in the browser)");
        addSummary("Test cases", (pass + fail + skipped + blocked) + " - PASS " + pass + " / FAIL " + fail + " / SKIPPED " + skipped + " / BLOCKED " + blocked);
        addSummary("API calls logged", String.valueOf(qe.apiLog.size()));
        addSummary("Failed API responses", qe.apiFailures.isEmpty() ? "none" : String.join(" ; ", qe.apiFailures));
        addSummary("Console errors", qe.consoleErrors.isEmpty() ? "none" : String.join(" ; ", qe.consoleErrors));
        addSummary("Blocked write calls (would have saved)", qe.blockedWrites.isEmpty() ? "none" : String.join(" ; ", qe.blockedWrites));
    }
}
