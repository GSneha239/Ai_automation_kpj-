package com.kpj.tests.Op_Page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.kpj.pages.Op_page.PatientDashboardPage;

/**
 * OP &gt; <b>Patient Dashboard</b> — smoke check only: verify the dashboard opens after login, and capture a
 * screenshot of it. No data entry, no interaction beyond confirming the page rendered.
 */
public class PatientDashboardTest extends DevHisBase {

    public PatientDashboardTest() { super("OP_PatientDashboard"); }

    public static void main(String[] args) {
        PatientDashboardTest t = new PatientDashboardTest();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("OP - Patient Dashboard", "OP > Patient Dashboard",
                "Log in and verify the Patient Dashboard opens; capture a screenshot of it.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        PatientDashboardPage dashboard = new PatientDashboardPage(page);
        boolean opened = dashboard.onScreen();
        step(page, "Open Patient Dashboard", "Login lands on OP > Patient Dashboard (#/PatientDashboard)",
                "The Patient Dashboard page is shown (KPI widgets rendered)",
                opened ? "Patient Dashboard opened - " + page.url() : "Dashboard did NOT open - the app is showing: " + page.url(),
                opened ? "PASS" : "FAIL");

        addSummary("Application URL", BASE + "/#/PatientDashboard");
        addSummary("Result", opened ? "Patient Dashboard opens correctly" : "FAILED - Patient Dashboard did not render");
    }
}
