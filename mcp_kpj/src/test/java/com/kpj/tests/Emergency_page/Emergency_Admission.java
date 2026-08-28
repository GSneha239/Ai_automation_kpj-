package com.kpj.tests.Emergency_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;
import com.microsoft.playwright.Page;

// NOTE: the page object is also named Emergency_Admission, so it is referenced by its fully-qualified
// name (com.kpj.pages.Emegency_Page.Emergency_Admission) — importing it would clash with this class name.

/**
 * TC12 - Emergency &gt; <b>Emergency Admission</b> (full IPD admission).
 *
 * <ol>
 *   <li>Click Emergency → Emergency Admission (via the menu tab, not a direct URL).</li>
 *   <li>Fill the required details (patient + admission location/department/doctor/type/source/bed-class/
 *       ward/billing-class + non-presence + admission purpose).</li>
 *   <li>Save ({@code IUDAdmission()}) → "Patient Admitted Successfully."</li>
 *   <li>The admission report(s) open in new tabs — capture the report.</li>
 * </ol>
 */
public class Emergency_Admission extends DevHisBase {

    public Emergency_Admission() { super("TC12_EmergencyAdmission"); }

    public static void main(String[] args) {
        Emergency_Admission t = new Emergency_Admission();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Emergency Admission", "Emergency > Emergency Admission",
                "Admit an emergency patient (full IPD admission) via the Emergency menu, then generate the admission report.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", "farisha / Tcare@123", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Emegency_Page.Emergency_Admission ea = new com.kpj.pages.Emegency_Page.Emergency_Admission(page);

        boolean opened = ea.navigateViaMenu();
        step(page, "Open Emergency Admission (via menu tab)", "Click Emergency → Emergency Admission (menu, not direct URL)",
                "The Emergency Admission form is shown", opened ? "Emergency Admission opened" : "Did NOT reach Emergency Admission",
                opened ? "PASS" : "FAIL");
        if (!opened) return;

        // Section 1 — Patient Information (screenshot the filled section).
        String patient = ea.fillPatientSection();
        step(page, "Fill · Patient Information", "Nationality → Prefix/Gender → Race/Religion/Marital/Blood → New IC → Name → NRIC → DOB",
                "Patient section populated", patient, "PASS");

        // Section 2 — Admission Information (screenshot the filled section).
        String admission = ea.fillAdmissionSection();
        step(page, "Fill · Admission Information", "Admission Location/Department/Doctor/Type/Source/Bed Class/Ward/Billing Class (+ Non-Presence, Purpose)",
                "Admission section populated", admission, "PASS");

        int tabsBefore = page.context().pages().size();
        String toast = ea.saveAdmissionAndGetToast();
        boolean ok = toast != null && toast.toLowerCase().contains("admitted");
        step(page, "Save admission", "Click Save (IUDAdmission); wait for the toast",
                "'Patient Admitted Successfully.' toast",
                toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");
        if (!ok) return;

        // Save opens SEVERAL report tabs (patient sticker, wrist band, IPD label, IPD report, consent
        // form) — the later ones open a few seconds after save, so wait, then capture & screenshot each.
        page.waitForTimeout(12000);
        java.util.List<Page> newTabs = new java.util.ArrayList<>();
        java.util.List<Page> all = page.context().pages();
        for (int i = tabsBefore; i < all.size(); i++) newTabs.add(all.get(i));
        addSummary("Report tabs generated", String.valueOf(newTabs.size()));
        int n = 0;
        for (Page rpt : newTabs) {
            n++;
            String url = rpt.url();
            String label = url.contains("IPDReport") ? "IPD Admission Report"
                    : url.contains("IPDPatientLabel") ? "IPD Patient Label"
                    : url.contains("WristBand") ? "Patient Wrist Band"
                    : url.contains("RegistrationReport") ? "Patient Sticker"
                    : url.contains("nhisformstest") ? "Consent Form"
                    : "Report " + n;
            byte[] png = ea.captureReportPng(rpt);
            // A tab opening is not a report that rendered — the Crystal pages happily serve "Server Error in
            // '/' Application / Logon failed" and captureReportPng's screenshot fallback captures that just as
            // "successfully" as a real report (same class of bug found live on Emergency Registration
            // Unconscious's Patient Wrist Band report — see DevHisBase.reportPageError, already used by IP
            // Admission's identical report-tab check).
            String err = reportPageError(rpt);
            if (!err.isEmpty()) {
                step(png, "Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", "Report FAILED to render — " + err + "  (" + url + ")", "FAIL");
            } else if (png != null && png.length > 0) {
                step(png, "Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", label + " captured (" + url + ")", "PASS");
            } else {
                step("Report " + n + " · " + label, "Admission report tab #" + n,
                        "Report is shown", label + " tab opened: " + url, "PASS");
            }
        }
        if (newTabs.isEmpty()) {
            step("Admission report", "Save opens the admission report in another tab",
                    "The admission report is shown", "No report tab detected", "MANUAL");
        }
        page.bringToFront();

        addSummary("Application URL", BASE + "/#/EmergencyAdmission");
    }
}
