package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named ServiceConfiguration — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Service Configuration</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Service Configuration</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select a <b>Service</b>, tick the <b>Select</b> checkbox and choose a <b>Processing Location</b>.</li>
 *   <li>Click <b>Submit</b> → success toast (retry a different Service if it already exists).</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class ServiceConfiguration extends DevHisBase {

    public ServiceConfiguration() { super("ApplicationConfig_Billing_ServiceConfiguration"); }

    public static void main(String[] args) {
        ServiceConfiguration t = new ServiceConfiguration();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Service Configuration", "Application Configuration > Billing > Service Configuration",
                "Add a Service Configuration: Add, select a Service, tick the Select checkbox + choose a Processing Location, Submit (retry a different Service on 'already exists'), then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.ServiceConfiguration sc =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.ServiceConfiguration(page);

        // 1) Navigate
        sc.navigateViaMenu();
        boolean onScreen = sc.onScreen();
        step(page, "Open Service Configuration screen", "Click Application Configuration -> Billing -> Service Configuration",
                "The Service Configuration screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = sc.clickAdd();
        step(page, "Click Add", "Click Add", "The Service Configuration add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        int services = sc.serviceCount();
        if (services <= 0) services = 1;

        // 3) + 4) Select Service, tick checkbox + Processing Location, Submit; retry a different Service on "exists".
        // Try EVERY available Service, not just the first 10 — this environment has many already configured
        // ("Service Already Exist!" on all of the first 10 tried live), so capping the retry short of the full
        // list gives up before a genuinely unconfigured Service is ever reached.
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int attempts = services, used = 0;
        for (int ordinal = 1; ordinal <= attempts; ordinal++) {
            used = ordinal;
            fill = sc.selectServiceAndConfigure(ordinal);
            if (fill.contains("Service=(exhausted)")) { used = ordinal - 1; break; }
            if (ordinal == 1) {
                boolean fillOk = !fill.contains("Service=(") && !fill.contains("ProcessingLocation=(no");
                step(page, "Select Service, tick Select, choose Processing Location",
                        "Select a Service; tick the Select checkbox; choose a Processing Location",
                        "Service selected, checkbox ticked, Processing Location chosen", fill, fillOk ? "PASS" : "FAIL");
            }
            toast = sc.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("ServiceConfiguration: attempt " + ordinal + " exists (\"" + toast + "\") — retrying with a different Service");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after trying " + used + " Service(s): \"" + toast + "\""
                                        : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast (retry Service on 'exists')",
                "Click Submit (fnIUDServices); if the config already exists, pick a different Service and Submit again",
                "'... saved successfully.' toast",
                (used > 1 && ok ? "(after " + used + " Service attempts) " : "") + actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = sc.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Service Configuration list; find the row just added (by Service)",
                    "The newly added Service Configuration row is shown in the table",
                    found ? "Found in table: " + row : "Saved (toast confirmed) but the new row was not located — Service=" + sc.lastService,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Service", sc.lastService);
        addSummary("Processing Location", sc.lastLocation);
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
