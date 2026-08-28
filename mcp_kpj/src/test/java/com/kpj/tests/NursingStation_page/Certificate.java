package com.kpj.tests.NursingStation_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// The page object is also named Certificate — referenced by its fully-qualified name.

/**
 * Nursing Station &gt; <b>Certificate</b>.
 *
 * <ol>
 *   <li>Open <b>Nursing Station</b> → <b>Certificate</b>; click <b>Add</b>.</li>
 *   <li>Enter MRN + search; select Certificate Template, Department, Doctor; tick Authenticate.</li>
 *   <li>Click <b>Save</b>; the certificate report opens in a new tab.</li>
 * </ol>
 */
public class Certificate extends DevHisBase {

    public Certificate() { super("NursingStation_Certificate"); }

    public static void main(String[] args) {
        Certificate t = new Certificate();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Nursing Station - Certificate", "Nursing Station > Certificate",
                "Add a Certificate: enter MRN + search, select Certificate Template/Department/Doctor, tick Authenticate, Save; the report opens in a new tab.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.NursingStation_page.Certificate ct =
                new com.kpj.pages.NursingStation_page.Certificate(page);

        boolean onScreen = ct.navigateTo(BASE);
        step(page, "Open Certificate screen", "Nursing Station -> Certificate",
                "The Certificate screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        boolean added = ct.clickTopAdd();
        step(page, "Click Add", "Click Add (AddCertificate) to open the form",
                "The add form is shown", added ? "Add form opened" : "Add form did not open",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED — add form not opened"); return; }

        String[] candidates = { "100000273", "100000025", "100000034", "100000990", "100001062" };
        String mrn = "";
        for (String c : candidates) { if (ct.enterMrnAndSearch(c)) { mrn = c; break; } }
        step(page, "Enter MRN & Search", "Type the MRN and click search (SearchPatientByMRNo)",
                "The patient is loaded by MRN",
                mrn.isEmpty() ? "No MRN loaded a patient" : "Loaded patient MRN " + mrn,
                mrn.isEmpty() ? "FAIL" : "PASS");
        if (mrn.isEmpty()) { addSummary("Result", "No patient loaded by MRN"); return; }

        String fill = ct.fillFields();
        boolean fillOk = !fill.contains("Template=(no") && !fill.contains("Department=(no") && !fill.contains("Doctor=(no");
        step(page, "Certificate Template, Department, Doctor, Authenticate",
                "Select Certificate Template, Department, Doctor; tick Authenticate",
                "The certificate fields are filled", fill, fillOk ? "PASS" : "FAIL");

        Page report = ct.saveAndOpenReport();
        boolean reportOpened = report != null && !report.isClosed();
        String actual = reportOpened
                ? "Certificate report opened in a new tab: " + report.url() + (ct.lastToast.isEmpty() ? "" : " | toast: " + ct.lastToast)
                : "No report tab opened" + (ct.lastToast.isEmpty() ? "" : " — server said: \"" + ct.lastToast + "\"");
        step(reportOpened ? report : page, "Click Save & report (new tab)",
                "Click Save (IUDDischargeSummaryDetail); the certificate report opens in a new tab",
                "The certificate report opens in a new tab", actual, reportOpened ? "PASS" : "FAIL");

        try { if (report != null && report != page && !report.isClosed()) report.close(); } catch (Exception ignore) { }
        try { page.bringToFront(); } catch (Exception ignore) { }

        addSummary("Patient MRN", ct.lastMrn);
        addSummary("Template / Dept / Doctor", ct.lastTemplate + " / " + ct.lastDept + " / " + ct.lastDoctor);
        addSummary("Result", reportOpened ? "Certificate report opened" : (ct.lastToast.isEmpty() ? "Not confirmed" : ct.lastToast));
    }
}
