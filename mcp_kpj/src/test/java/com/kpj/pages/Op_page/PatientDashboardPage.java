package com.kpj.pages.Op_page;

import com.kpj.pages.BasePage;
import com.microsoft.playwright.Page;

/**
 * OP &gt; <b>Patient Dashboard</b> ({@code #/PatientDashboard}) — the KPI/analytics landing screen shown right
 * after login (see {@code DevHisBase.DASHBOARD} / {@code LoginPage.login()}, which already navigates here).
 *
 * <p>This page object only verifies the dashboard actually rendered — not just that the URL hash matched, since
 * an Angular route can hold the right hash while showing a stale or blank view. "Rendered" is confirmed by one of
 * its own KPI widget labels ("TOTAL NO. OF APPOINTMENTS", "TOTAL NO. OF REGISTERED PATIENTS", "PATIENT JOURNEY")
 * being visible on screen.</p>
 */
public class PatientDashboardPage extends BasePage {

    public PatientDashboardPage(Page page) { super(page); }

    public static final String ROUTE = "#/PatientDashboard";

    /** Navigate here directly (login already lands here, but this lets the page object be used standalone). */
    public boolean navigateTo(String baseUrl) {
        if (!page.url().toLowerCase().contains("patientdashboard")) {
            try { page.evaluate("() => { window.location.hash = '#/PatientDashboard'; }"); } catch (Exception ignore) { }
            waitForAngular(1500);
        }
        try {
            page.waitForFunction("() => /patientdashboard/i.test(location.href)", null,
                    new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PatientDashboardPage.navigateTo: URL never showed PatientDashboard"); }
        waitForAngular(1000);
        return onScreen();
    }

    /** True once the URL is on the right route AND at least one real dashboard widget has rendered. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().contains("patientdashboard")) return false;
        try {
            page.waitForFunction("() => { const t=(document.body.innerText||''); return /total\\s*no\\.?\\s*of\\s*appointments/i.test(t) || /total\\s*no\\.?\\s*of\\s*registered\\s*patients/i.test(t) || /patient\\s*journey/i.test(t); }",
                    null, new Page.WaitForFunctionOptions().setTimeout(15000));
        } catch (Exception ignore) { System.out.println("PatientDashboardPage.onScreen: no recognizable KPI widget appeared in time"); }
        return Boolean.TRUE.equals(page.evaluate("() => { const t=(document.body.innerText||''); return /total\\s*no\\.?\\s*of\\s*appointments/i.test(t) || /total\\s*no\\.?\\s*of\\s*registered\\s*patients/i.test(t) || /patient\\s*journey/i.test(t); }"));
    }
}
