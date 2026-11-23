package com.kpj.tests.Ip;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is referenced by its fully-qualified name (com.kpj.pages.Ip.DischargePage).

/**
 * TC21 - IP &gt; <b>Discharge</b> (consolidated) — one login, then each Discharge action as its own section
 * (each re-opens the list + searches + selects a patient, so one failing section doesn't abort the rest):
 *
 * <ol>
 *   <li><b>Cancel Discharge</b> — select a discharged patient → Cancel Discharge
 *       ({@code GetBillGeneratedOrNot}) → "Discharge Cancel successfully." toast.</li>
 *   <li><b>Print</b> — select a discharged patient → Print → the report opens in a new tab → screenshot.</li>
 * </ol>
 *
 * <p>NOTE: Cancel Discharge is a state-changing action — it cancels the selected patient's discharge.</p>
 */
public class DischargeTest extends DevHisBase {

    public DischargeTest() { super("TC21_Discharge"); }

    public static void main(String[] args) {
        DischargeTest t = new DischargeTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Discharge", "IP > Discharge",
                "One run exercising the Discharge actions: Cancel Discharge and Print.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        sectionCancelDischarge();
        sectionPrint();

        addSummary("Application URL", BASE + "/" + com.kpj.pages.Ip.DischargePage.ROUTE);
    }

    /** Open Discharge, select the Discharge view + 1-month range (To = today) + Search. Returns the page
     *  (null if it didn't open). */
    private com.kpj.pages.Ip.DischargePage openAndSearch(String tag) {
        com.kpj.pages.Ip.DischargePage dis = new com.kpj.pages.Ip.DischargePage(page);
        boolean opened = dis.navigateViaMenu();
        step(page, tag + " · Open Discharge", "Click IP -> Discharge",
                "The Discharge list is shown", opened ? "Opened (#/DischargeList)" : "Did NOT reach the list",
                opened ? "PASS" : "FAIL");
        if (!opened) return null;
        String range = dis.searchDischargedOneMonthToToday();
        step(page, tag + " · Search (Discharge view, 1-month range, To = today)",
                "Select the 'Discharge' radio; set From = today-1month, To = today; click Search",
                "Discharged patients are listed", "Searched " + range, "PASS");
        return dis;
    }

    // ===== Section: Cancel Discharge =====================================
    private void sectionCancelDischarge() {
        com.kpj.pages.Ip.DischargePage dis = openAndSearch("Cancel Discharge");
        if (dis == null) return;

        String patient = dis.selectRandomPatient();
        step(page, "Cancel Discharge · Select a patient", "Select any discharged patient row (ui-grid API)",
                "One patient selected", patient == null ? "No discharged patient in range" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = dis.cancelDischargeAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("cancel") || toast.toLowerCase().contains("success"));
        step(page, "Cancel Discharge · Click & success toast", "Click 'Cancel Discharge' (GetBillGeneratedOrNot())",
                "'Discharge Cancel successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        addSummary("Cancel Discharge · Patient", patient);
        addSummary("Cancel Discharge · Result", ok ? toast : "Not confirmed");
    }

    // ===== Section: Print ================================================
    private void sectionPrint() {
        com.kpj.pages.Ip.DischargePage dis = openAndSearch("Print");
        if (dis == null) return;

        String patient = dis.selectRandomPatient();
        step(page, "Print · Select a patient", "Select any discharged patient row (ui-grid API)",
                "One patient selected", patient == null ? "No discharged patient in range" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        byte[] png = dis.clickPrintAndScreenshot();
        String url = dis.lastReportUrl;
        boolean gotReport = url != null && url.toLowerCase().contains("dischargegridreport");
        String actual = gotReport ? "Report opened in a new tab (PDF): " + url
                                  : (url == null || url.isEmpty() ? "No report URL captured" : "Report URL: " + url);
        if (png != null && png.length > 0) {
            step(png, "Print · Report opens in new tab & screenshot", "Click 'Print' (PrintGridReportList()); the discharge report opens in a new tab",
                    "The discharge report is shown", actual, gotReport ? "PASS" : "FAIL");
        } else {
            step(page, "Print · Report opens in new tab", "Click 'Print' (PrintGridReportList()); the discharge report (PDF) opens in a new tab",
                    "The discharge report opens in a new tab", actual, gotReport ? "PASS" : "FAIL");
        }

        addSummary("Print · Patient", patient);
        addSummary("Print · Report", gotReport ? url : "Not generated");
    }
}
