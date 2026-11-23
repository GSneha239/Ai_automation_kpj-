package com.kpj.tests.ApplicationConfiguration_page.Patient_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named CampDetails — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Patient &gt; <b>Camp Details</b>.
 *
 * <ol>
 *   <li>Open <b>Application Configuration</b> → <b>Patient</b> → <b>Camp Details</b>.</li>
 *   <li>Click <b>Add</b> ({@code AddCamp}) → {@code #/add-camp}.</li>
 *   <li>Fill the <b>Camp Details</b> tab (Camp Type*, Code*, Remark*, dates, Valid Days, Pricing Policy*, Reason,
 *       City, Area).</li>
 *   <li>Open the <b>Camp Service</b> tab and add a service to the grid.</li>
 *   <li>Click <b>Save</b> ({@code fnIUDCampDetails}); wait for the success toast. If the toast says the code /
 *       remark already exists, change the details and Save again.</li>
 * </ol>
 */
public class CampDetails extends DevHisBase {

    /** How many times to re-enter fresh Code/Remark when the server says they already exist. */
    private static final int MAX_ATTEMPTS = 40;

    public CampDetails() { super("ApplicationConfig_Patient_CampDetails"); }

    public static void main(String[] args) {
        CampDetails t = new CampDetails();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Patient - Camp Details",
                "Application Configuration > Patient > Camp Details",
                "Add a Camp: Add, fill the Camp Details tab and the Camp Service tab, Save; wait for the success toast.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Patient_page.CampDetails cd =
                new com.kpj.pages.ApplicationConfiguration_page.Patient_page.CampDetails(page);

        boolean on = cd.navigateViaMenu();
        step(page, "Open Camp Details screen", "Application Configuration -> Patient -> Camp Details",
                "The Camp Details list screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        boolean added = cd.clickAdd();
        step(page, "Click Add", "Click Add (AddCamp) -> #/add-camp", "The Camp Details add form is shown",
                added ? "Add form opened (" + page.url() + ")" : "Add form did not open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        if (!added) { addSummary("Result", "FAILED - add form not opened"); return; }

        String details = cd.fillCampDetails(0);
        boolean detOk = details.contains("Code=CMP") && !details.contains("CampType=(no") && !details.contains("PricingPolicy=(no");
        step(page, "Fill camp details",
                "Camp Type*, Code*, Remark*, From/To Date, Valid Days, Pricing Policy*, Reason, City, Area",
                "All camp detail fields are filled", details, detOk ? "PASS" : "FAIL");

        boolean tabOpen = cd.openCampServiceTab();
        String service = tabOpen ? cd.enterServiceNameAndPressEnter("Consultation Fees")
                                 : "(Camp Service tab did not open)";
        boolean svcOk = tabOpen && !service.startsWith("(");
        step(page, "Fill camp service", "Open the Camp Service tab, type a value in Service Name and press Enter",
                "The Service Name value is entered", service, svcOk ? "PASS" : "FAIL");

        // Save — retry with a fresh Code/Remark (switching back to the Camp Details tab first) on "already exists".
        String toast = "";
        boolean ok = false, exists = false;
        int used = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            used++;
            toast = cd.saveAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;   // saved, or a different (non-retryable) message
            System.out.println("CampDetails: attempt " + used + " already exists — changing Code/Remark and retrying");
            cd.openCampDetailsTab();
            details = cd.fillCampDetails(attempt + 1);
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast
                      : (exists ? "Still 'already exists' after " + used + " attempt(s) with different details: \"" + toast + "\""
                                : "Save not confirmed - server returned: \"" + toast + "\""));
        String stepActual = (used > 1 && ok ? "(saved on attempt " + used + ") " : "") + actual;
        step(page, "Click Save & success toast", "Click Save (fnIUDCampDetails); on 'already exists' change the details and Save again",
                "A '... saved successfully' success toast is shown", stepActual, ok ? "PASS" : "FAIL");

        addSummary("Camp Code", cd.lastCode);
        addSummary("Camp Remark", cd.lastRemark);
        addSummary("Camp Service", cd.lastService.isEmpty() ? "(none added)" : cd.lastService);
        addSummary("Attempts", String.valueOf(used));
        addSummary("Route", "#/CampDetails -> #/add-camp");
        addSummary("Result", ok ? toast : (toast == null || toast.isEmpty() ? "Not confirmed" : toast));
    }
}
