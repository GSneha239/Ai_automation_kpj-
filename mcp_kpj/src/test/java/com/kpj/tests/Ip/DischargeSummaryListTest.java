package com.kpj.tests.Ip;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is referenced by its fully-qualified name (com.kpj.pages.Ip.DischargeSummaryListPage).

/**
 * TC22 - IP &gt; <b>Discharge Summary List</b> — ReferralLetter/Medical Report template.
 *
 * <ol>
 *   <li>Open Discharge Summary List (IP menu → Discharge Summary List).</li>
 *   <li>Search a 1-month range (To = today) and select a patient.</li>
 *   <li>Click <b>ReferralLetter/Medical Report</b> → the discharge-template editor opens.</li>
 *   <li>Tick a template-field checkbox → Submit ({@code fnIUDDischargetemplate}) →
 *       "Template updated successfully." toast.</li>
 * </ol>
 */
public class DischargeSummaryListTest extends DevHisBase {

    public DischargeSummaryListTest() { super("TC22_DischargeSummaryList"); }

    public static void main(String[] args) {
        DischargeSummaryListTest t = new DischargeSummaryListTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Discharge Summary List", "IP > Discharge Summary List",
                "Open Discharge Summary List, search a 1-month range ending today, select a patient, "
                        + "ReferralLetter/Medical Report -> tick a field -> Submit -> success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionReferralLetter();
        sectionMedicalCertificate();
        sectionDeath();
        sectionPrint();
        sectionNew();

        addSummary("Application URL", BASE + "/" + com.kpj.pages.Ip.DischargeSummaryListPage.ROUTE);
    }

    /** Open Discharge Summary List, search a 1-month range (To = today), and select a patient. Returns the page
     *  with a patient selected — or null if it didn't open / no patient. */
    private com.kpj.pages.Ip.DischargeSummaryListPage openSearchSelect(String tag) {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = new com.kpj.pages.Ip.DischargeSummaryListPage(page);
        boolean opened = ds.navigateViaMenu();
        step(page, tag + " · Open Discharge Summary List", "Click IP -> Discharge Summary List",
                "The Discharge Summary List is shown", opened ? "Opened (#/DischargeSummaryList)" : "Did NOT reach the list",
                opened ? "PASS" : "FAIL");
        if (!opened) return null;
        String range = ds.searchOneMonthToToday();
        step(page, tag + " · Search (1-month range, To = today)", "Set From = today-1month, To = today; click Search",
                "Discharge summaries are listed", "Searched " + range, "PASS");
        String patient = ds.selectRandomPatient();
        step(page, tag + " · Select a patient", "Select any patient row (ui-grid API)",
                "One patient selected", patient == null ? "No patient in the list" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        return patient == null ? null : ds;
    }

    // ===== Section: ReferralLetter/Medical Report ========================
    private void sectionReferralLetter() {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = openSearchSelect("ReferralLetter");
        if (ds == null) return;

        boolean editor = ds.clickReferralLetterMedicalReport();
        step(page, "ReferralLetter · Open template editor", "Click 'ReferralLetter/Medical Report'; the discharge-template editor opens",
                "The template editor (#/edit-DischargeTemplateMaster) is shown",
                editor ? "Template editor opened" : "Editor did NOT open", editor ? "PASS" : "FAIL");
        if (!editor) return;

        String toast = ds.checkFieldAndSubmit();
        boolean ok = toast != null && (toast.toLowerCase().contains("template")
                || toast.toLowerCase().contains("updated") || toast.toLowerCase().contains("success"));
        step(page, "ReferralLetter · Tick a field & Submit", "Tick a template-field checkbox; Submit (fnIUDDischargetemplate())",
                "'Template updated successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        addSummary("ReferralLetter · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Medical Certificate (MC) =============================
    private void sectionMedicalCertificate() {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = openSearchSelect("Medical Certificate");
        if (ds == null) return;

        boolean opened = ds.clickMedicalCertificate();
        step(page, "Medical Certificate · Open add-Certificate", "Click 'Medical Certificate (MC)' (fnSetCertificateSession())",
                "The add-Certificate page is shown", opened ? "add-Certificate opened" : "Did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = ds.fillCertificateAndSave();
        // This action shows NO success toast — the certificate REPORT (certificateTemplate.aspx) opening is the
        // success signal. Judge Save by the report, not a toast.
        byte[] png = ds.lastReportPng;
        String url = ds.lastReportUrl;
        boolean gotReport = url != null && url.toLowerCase().contains("certificatetemplate");
        step(page, "Medical Certificate · Template/Dept/Doctor/Authenticate + text + Save",
                "Select Certificate Template + Department + Doctor; tick Authenticate; type text; Save (IUDDischargeSummaryDetail())",
                "The certificate report is generated (no success toast for this action)",
                gotReport ? ("Report generated" + (toast == null || toast.isEmpty() ? "" : " (msg: " + toast + ")"))
                          : (toast == null || toast.isEmpty() ? "No report generated" : "No report — " + toast),
                gotReport ? "PASS" : "FAIL");

        String actual = gotReport ? "Certificate report opened in a new tab (PDF): " + url
                                  : (url == null || url.isEmpty() ? "No report URL captured" : "Report URL: " + url);
        if (png != null && png.length > 0) {
            step(png, "Medical Certificate · Report opens in new tab & screenshot", "The certificate report opens in a new tab after Save",
                    "The certificate report is shown", actual, gotReport ? "PASS" : "MANUAL");
        } else {
            step(page, "Medical Certificate · Report opens in new tab", "The certificate report (PDF) opens in a new tab after Save",
                    "The certificate report opens in a new tab", actual, gotReport ? "PASS" : "MANUAL");
        }

        addSummary("Medical Certificate · Result", gotReport ? "Report generated" : "Not generated");
        addSummary("Medical Certificate · Report", gotReport ? url : "Not generated");
    }

    // ===== Section: Death (certificate) ==================================
    private void sectionDeath() {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = openSearchSelect("Death");
        if (ds == null) return;

        boolean opened = ds.clickDeath();
        step(page, "Death · Open add-Certificate", "Click 'Death' (fnSetCertificateSession())",
                "The add-Certificate page is shown", opened ? "add-Certificate opened" : "Did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = ds.fillCertificateAndSave();
        // No success toast for this action — the certificate REPORT (certificateTemplate.aspx) opening is the success signal.
        byte[] png = ds.lastReportPng;
        String url = ds.lastReportUrl;
        boolean gotReport = url != null && url.toLowerCase().contains("certificatetemplate");
        step(page, "Death · Template/Dept/Doctor/Authenticate + text + Save",
                "Select Certificate Template + Department + Doctor; tick Authenticate; type text; Save (IUDDischargeSummaryDetail())",
                "The certificate report is generated (no success toast for this action)",
                gotReport ? ("Report generated" + (toast == null || toast.isEmpty() ? "" : " (msg: " + toast + ")"))
                          : (toast == null || toast.isEmpty() ? "No report generated" : "No report — " + toast),
                gotReport ? "PASS" : "FAIL");

        String actual = gotReport ? "Certificate report opened in a new tab (PDF): " + url
                                  : (url == null || url.isEmpty() ? "No report URL captured" : "Report URL: " + url);
        if (png != null && png.length > 0) {
            step(png, "Death · Report opens in new tab & screenshot", "The certificate report opens in a new tab after Save",
                    "The certificate report is shown", actual, gotReport ? "PASS" : "MANUAL");
        } else {
            step(page, "Death · Report opens in new tab", "The certificate report (PDF) opens in a new tab after Save",
                    "The certificate report opens in a new tab", actual, gotReport ? "PASS" : "MANUAL");
        }

        addSummary("Death · Result", gotReport ? "Report generated" : "Not generated");
        addSummary("Death · Report", gotReport ? url : "Not generated");
    }

    // ===== Section: Print ================================================
    private void sectionPrint() {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = openSearchSelect("Print");
        if (ds == null) return;

        byte[] png = ds.clickPrintAndScreenshot();
        String url = ds.lastReportUrl;
        boolean gotReport = url != null && url.toLowerCase().contains("dischargegridreport");
        String actual = gotReport ? "Report opened in a new tab (PDF): " + url
                                  : (url == null || url.isEmpty() ? "No report URL captured" : "Report URL: " + url);
        if (png != null && png.length > 0) {
            step(png, "Print · Report opens in new tab & screenshot", "Click 'Print' (PrintGridReportList()); the discharge-grid report opens in a new tab",
                    "The discharge-grid report is shown", actual, gotReport ? "PASS" : "FAIL");
        } else {
            step(page, "Print · Report opens in new tab", "Click 'Print' (PrintGridReportList()); the discharge-grid report (PDF) opens in a new tab",
                    "The report opens in a new tab", actual, gotReport ? "PASS" : "FAIL");
        }
        addSummary("Print · Report", gotReport ? url : "Not generated");
    }

    // ===== Section: New (create a Discharge Summary) =====================
    private void sectionNew() {
        com.kpj.pages.Ip.DischargeSummaryListPage ds = openSearchSelect("New");
        if (ds == null) return;

        String mrn = ds.getGridMrn();
        boolean opened = ds.clickNew();
        step(page, "New · Open form", "Click 'New' (AddDischarge())",
                "The New Discharge Summary form is shown", opened ? "Form opened (#/DischargeSummary)" : "Did NOT open",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        String toast = ds.fillNewDischargeAndSave(mrn);
        // New Discharge Summary shows NO success toast — success = the discharge-summary REPORT prints (via 'Print').
        byte[] png = ds.clickNewPrintAndScreenshot();
        String url = ds.lastReportUrl;
        boolean gotReport = url != null && url.toLowerCase().contains("dischargesummary.aspx");
        step(page, "New · Enter MRN, template, reason, follow-up date & Save",
                "Enter MRN (" + mrn + ") + search; select Discharge Template; type reason; set Follow-up date; Save (IUDDischargeSummaryDetail())",
                "The discharge summary is saved (no toast; success = the report prints)",
                gotReport ? ("Saved — report generated" + (toast == null || toast.isEmpty() ? "" : " (msg: " + toast + ")"))
                          : (toast == null || toast.isEmpty() ? "Saved but no report generated" : "No report — " + toast),
                gotReport ? "PASS" : "FAIL");

        String actual = gotReport ? "Report opened in a new tab (PDF): " + url
                                  : (url == null || url.isEmpty() ? "No report URL captured" : "Report URL: " + url);
        if (png != null && png.length > 0) {
            step(png, "New · Print report opens in new tab & screenshot", "Click 'Print' (printReport()); the discharge-summary report opens in a new tab",
                    "The discharge-summary report is shown", actual, gotReport ? "PASS" : "MANUAL");
        } else {
            step(page, "New · Print report opens in new tab", "Click 'Print' (printReport()); the discharge-summary report (PDF) opens in a new tab",
                    "The report opens in a new tab", actual, gotReport ? "PASS" : "MANUAL");
        }

        addSummary("New · MRN", mrn);
        addSummary("New · Result", gotReport ? "Report generated" : "Not generated");
        addSummary("New · Report", gotReport ? url : "Not generated");
    }
}
