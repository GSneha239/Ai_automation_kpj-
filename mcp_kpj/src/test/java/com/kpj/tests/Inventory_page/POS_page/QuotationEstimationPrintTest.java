package com.kpj.tests.Inventory_page.POS_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.Inventory_page.POS_page.QuotationEstimation;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

import java.util.List;

/**
 * Inventory &gt; POS &gt; <b>Quotation Estimation</b> - the WRITE flow: enter the MRN (MRN search), Last Name, First Name, select the
 * Bill Template, Add Bill Template, <b>Submit</b> and <b>Print</b> (the report opens in a new tab).
 *
 * <p>&#9888; Submit saves a REAL quotation, and Print saves again before it prints (fnSaveBill(true)), so each run creates up to two
 * quotations. The read-only checks are in {@link QuotationEstimationTest}.</p>
 *
 * <p>Live finding (16/11/2026): do NOT click the OPD radio before the MRN search - the radio runs ClearIpdOpdData(), which sets
 * PatientDetails = null, and SearchPatientByMRNo then throws at PatientDetails.MRNo (names stay empty, Add Bill Template throws).
 * OPD is the default, so the flow leaves the radio alone. The search fills First Name with the full name and leaves Last Name empty.</p>
 *
 * <pre>mvn test "-Dtest=com.kpj.tests.Inventory_page.POS_page.QuotationEstimationPrintTest" [-Dmrn=1100339336]</pre>
 */
public class QuotationEstimationPrintTest extends DevHisBase {

    private static final String DEFAULT_MRN = "1100339336";
    private static final String TEMPLATE_NAME = System.getProperty("qe.billTemplate", "testing");
    private static final String TARIFF_NAME = System.getProperty("qe.tariff", "SELFPAY / CASH CUSTOMER");
    private static final String CLASS_NAME = System.getProperty("qe.class", "Out Patient");

    public QuotationEstimationPrintTest() { super("Inventory_POS_QuotationEstimationPrint"); }

    public static void main(String[] args) {
        QuotationEstimationPrintTest t = new QuotationEstimationPrintTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Inventory - POS - Quotation Estimation (Submit and Print)", "Inventory > POS > Quotation Estimation",
                "&#9888; Saves REAL quotations: enter the MRN (MRN search), Last Name, First Name, select the Bill Template, Add Bill Template, "
                        + "click Submit and Print; the report opens in a new tab.");
        String mrn = System.getProperty("mrn", DEFAULT_MRN);

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        QuotationEstimation qe = new QuotationEstimation(page);
        qe.allowWindowOpen = true;   // Print opens the report with window.open - the read-only hook would swallow it
        boolean open = qe.openAddForm();
        step(page, "Open Quotation Estimation > Add", "Inventory > POS > Quotation Estimation, click Add",
                "The Add Quotation Estimation form is shown", open ? "Opened " + page.url() : "Did NOT reach the form (" + page.url() + ")", open ? "PASS" : "FAIL");
        if (!open) { addSummary("Result", "FAILED - form not reached"); return; }

        // 1) MRN search (the OPD radio is left alone - see the class comment)
        qe.setNgInput("BillEstimation.MRNo", mrn);
        qe.clearCaptured();
        qe.clickNg("SearchPatientByMRNo();");
        page.waitForTimeout(3500);
        boolean found = Boolean.TRUE.equals(page.evaluate("(m) => new RegExp('MRN\\\\s*:\\\\s*'+m).test(document.body.innerText||'')", mrn));
        List<String> a1 = qe.alerts();
        String first0 = qe.inputValue(QuotationEstimation.FIRST_NAME);
        step(page, "Enter MRN and click Search (MRN search)", "Enter MRN " + mrn + " and click the MRN Search button (SearchPatientByMRNo)",
                "The patient is found (patient banner shows the MRN)",
                found ? "MRN " + mrn + " found; First Name box shows '" + first0 + "'" : "Patient banner does not show MRN " + mrn + " | messages " + a1,
                found ? "PASS" : "FAIL");
        if (!found) { addSummary("Result", "FAILED - patient not found for MRN " + mrn); return; }

        // 2) Last Name, First Name
        String name = first0 == null ? "" : first0.trim();
        String first = name.isEmpty() ? "QA" : name.split("\\s+")[0];
        String last = name.contains(" ") ? name.substring(name.lastIndexOf(' ') + 1) : "Quotation";
        qe.setNgInput(QuotationEstimation.LAST_NAME, last);
        qe.setNgInput(QuotationEstimation.FIRST_NAME, first);
        String lastNow = qe.inputValue(QuotationEstimation.LAST_NAME), firstNow = qe.inputValue(QuotationEstimation.FIRST_NAME);
        boolean namesOk = last.equals(lastNow) && first.equals(firstNow);
        step(page, "Enter Last Name and First Name", "Enter Last Name '" + last + "' and First Name '" + first + "'",
                "Both names are entered", "Last Name='" + lastNow + "', First Name='" + firstNow + "'", namesOk ? "PASS" : "FAIL");

        // Date: the MRN search replaces the whole form object with the patient record and BLANKS the Date box (it showed today before),
        // and Submit then fails with 'Cannot insert the value NULL into column date'. Enter today's date and say so.
        String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String dateBefore = qe.inputValue("BillEstimation.date");
        boolean wasBlank = dateBefore == null || dateBefore.trim().isEmpty();
        if (wasBlank) qe.setNgInput("BillEstimation.date", today);
        String dateNow = qe.inputValue("BillEstimation.date");
        step(page, "Check the Date", "Look at the Date box after the MRN search" + (wasBlank ? " and enter today's date" : ""),
                "The Date box holds today's date",
                (wasBlank ? "App defect: the MRN search blanked the Date box; entered " + today + " -> " : "Date = ") + "'" + dateNow + "'",
                today.equals(dateNow) ? "PASS" : "FAIL");
        if (wasBlank) addSummary("Suspected app defect", "MRN search blanks the Date box (Submit then fails: NULL into column 'date')");

        // 3) Payor / Pricing Policy / Class / Bill Template
        qe.pickOption("BillEstimation.companyid", "SELFPAY");
        page.waitForTimeout(900);
        String tariff = qe.pickOption(QuotationEstimation.TARIFF, TARIFF_NAME);
        page.waitForTimeout(1200);
        String cls = qe.pickOption(QuotationEstimation.CLASS, CLASS_NAME);
        String tpl = qe.pickOption(QuotationEstimation.TEMPLATE, TEMPLATE_NAME);
        boolean selOk = !tariff.isEmpty() && !cls.isEmpty() && !tpl.isEmpty();
        step(page, "Select Bill Template", "Select Pricing Policy '" + TARIFF_NAME + "', Class '" + CLASS_NAME + "' and Bill Template '" + TEMPLATE_NAME + "'",
                "The Bill Template is selected", "Pricing Policy=" + tariff + " | Class=" + cls + " | Bill Template=" + tpl, selOk ? "PASS" : "FAIL");
        if (!selOk) { addSummary("Result", "FAILED - a selection was not available"); return; }

        // 4) Add Bill Template
        qe.clickAddBillTemplate();
        List<String[]> rows = qe.serviceRows();
        boolean added = !rows.isEmpty() && QuotationEstimation.money(qe.total()) > 0;
        step(page, "Click Add Bill Template", "Click Add Bill Template", "The template's services are listed and the Total Bill Amount is filled",
                added ? rows.size() + " service line(s), first " + String.join(" | ", rows.get(0)) + " | Total Bill Amount " + qe.total()
                      : "No service added (total '" + qe.total() + "') | messages " + qe.alerts(), added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - bill template added no services"); return; }

        // 5) Submit - the save call's own answer is recorded next to the toast
        final java.util.List<String> iud = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        page.onResponse(rs -> {
            if (rs.url().toLowerCase().contains("billestimation/iud")) {
                String b = "";
                try { b = rs.text(); } catch (Exception ignore) { }
                String req = "";
                try { req = String.valueOf(rs.request().postData()); } catch (Exception ignore) { }
                iud.add("HTTP " + rs.status() + " " + (b.length() > 300 ? b.substring(0, 300) + "..." : b)
                        + " || request " + (req.length() > 700 ? req.substring(0, 700) + "..." : req));
            }
        });
        qe.clearCaptured();
        qe.clickSubmit();
        List<String> sub = qe.waitForAlert(8000);
        String subText = sub.isEmpty() ? "" : String.join(" | ", sub);
        boolean subOk = subText.toLowerCase().matches(".*(success|saved|added).*") && !subText.toLowerCase().matches(".*(please|fail|error|invalid).*");
        step(page, "Click Submit", "Click Submit", "The quotation is saved (success toast)",
                (sub.isEmpty() ? "No message appeared" : subText) + (iud.isEmpty() ? " | no save request was sent" : " | save call: " + iud.get(iud.size() - 1)),
                subOk ? "PASS" : "FAIL");
        addSummary("Submit", sub.isEmpty() ? "No message" : subText);

        // 6) Print - saves and opens the report in a NEW tab. After Submit the form may be cleared, so rebuild it when it is.
        if (qe.serviceRows().isEmpty()) {
            System.out.println("QuotationEstimationPrint: the form was cleared by Submit - preparing a fresh one for Print");
            if (qe.openAddForm()) {
                qe.setNgInput("BillEstimation.MRNo", mrn);
                qe.clickNg("SearchPatientByMRNo();");
                page.waitForTimeout(3500);
                qe.setNgInput(QuotationEstimation.LAST_NAME, last);
                qe.setNgInput(QuotationEstimation.FIRST_NAME, first);
                qe.setNgInput("BillEstimation.date", today);   // blanked by the MRN search again
                qe.pickOption("BillEstimation.companyid", "SELFPAY");
                page.waitForTimeout(900);
                qe.pickOption(QuotationEstimation.TARIFF, TARIFF_NAME);
                page.waitForTimeout(1200);
                qe.pickOption(QuotationEstimation.CLASS, CLASS_NAME);
                qe.pickOption(QuotationEstimation.TEMPLATE, TEMPLATE_NAME);
                qe.clickAddBillTemplate();
            }
        }
        qe.clearCaptured();
        Page report = null;
        try {
            report = page.context().waitForPage(new BrowserContext.WaitForPageOptions().setTimeout(25000), () -> qe.clickNg("PrintBillEstimation()"));
        } catch (Exception e) {
            System.out.println("QuotationEstimationPrint: no new tab - " + String.valueOf(e.getMessage()).split("\n")[0]);
        }
        List<String> pr = qe.alerts();
        if (report != null) {
            try { report.waitForLoadState(com.microsoft.playwright.options.LoadState.LOAD, new Page.WaitForLoadStateOptions().setTimeout(30000)); } catch (Exception ignore) { }
            report.waitForTimeout(4000);
            String body = "";
            try { body = String.valueOf(report.evaluate("() => (document.body && document.body.innerText || '').replace(/\\s+/g,' ').trim().slice(0,300)")); } catch (Exception ignore) { }
            boolean bad = body.toLowerCase().matches(".*(error|exception|rowset|certificate|not found|500).*");
            step(report, "Click Print - the report opens in a new tab", "Click Print", "The quotation is saved and the report opens in a NEW tab",
                    "New tab " + report.url() + " | " + (body.isEmpty() ? "(PDF / viewer, no text)" : "text: " + body) + (pr.isEmpty() ? "" : " | messages " + pr),
                    bad ? "FAIL" : "PASS");
            addSummary("Report tab", report.url());
        } else {
            step(page, "Click Print - the report opens in a new tab", "Click Print", "The quotation is saved and the report opens in a NEW tab",
                    "No new tab opened | messages " + (pr.isEmpty() ? "(none)" : pr), "FAIL");
        }
        addSummary("MRN", mrn);
        addSummary("Result", subOk && report != null ? "Saved and report opened" : "See failed steps");
    }
}
