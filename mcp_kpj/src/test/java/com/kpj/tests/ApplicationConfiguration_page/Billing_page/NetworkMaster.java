package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named NetworkMaster — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Network Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Network Master</b>.</li>
 *   <li>Click <b>Add</b>.</li>
 *   <li>Select <b>Location</b>, <b>Pricing Policy</b>, <b>Payor</b>, <b>Network</b>.</li>
 *   <li>Click <b>Submit</b> → success toast (retry a different Pricing Policy if it already exists).</li>
 *   <li>Check the list table for the added row.</li>
 * </ol>
 */
public class NetworkMaster extends DevHisBase {

    public NetworkMaster() { super("ApplicationConfig_Billing_NetworkMaster"); }

    public static void main(String[] args) {
        NetworkMaster t = new NetworkMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Network Master", "Application Configuration > Billing > Network Master",
                "Add a Network Master: Add, select Location + Pricing Policy + Payor + Network, Submit (retry a different Pricing Policy on 'already exists'), then check the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.NetworkMaster nw =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.NetworkMaster(page);

        // 1) Navigate
        nw.navigateViaMenu();
        boolean onScreen = nw.onScreen();
        step(page, "Open Network Master screen", "Click Application Configuration -> Billing -> Network Master",
                "The Network Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Add
        boolean added = nw.clickAdd();
        step(page, "Click Add", "Click Add", "The Network Master add form opens",
                added ? "Add clicked (" + page.url() + ")" : "Add did NOT open", added ? "PASS" : "FAIL");
        if (!added) return;

        int tariffCount = nw.tariffOptionCount();
        if (tariffCount <= 0) tariffCount = 1;

        // 3) + 4) Fill cascade and Submit; retry a different Pricing Policy on "already exists".
        String fill = "", toast = "";
        boolean ok = false, exists = false;
        int attempts = Math.min(tariffCount, 8), used = 0;
        for (int ordinal = 1; ordinal <= attempts; ordinal++) {
            used = ordinal;
            fill = nw.fillCascade(ordinal);
            if (fill.contains("PricingPolicy=(exhausted)")) { used = ordinal - 1; break; }
            if (ordinal == 1) {
                boolean fillOk = fill.contains("Location=") && !fill.contains("Location=(no")
                        && !fill.contains("PricingPolicy=(no") && !fill.contains("Network=(no");
                step(page, "Select Location, Pricing Policy, Payor, Network",
                        "Select Location, then the cascading Pricing Policy, Payor and Network",
                        "All four dropdowns are selected", fill, fillOk ? "PASS" : "FAIL");
            }
            toast = nw.submitAndGetToast();
            String tl = toast == null ? "" : toast.toLowerCase();
            ok = tl.contains("saved") || tl.contains("success") || tl.contains("added") || tl.contains("updated");
            exists = tl.contains("exist") || tl.contains("already");
            if (ok || !exists) break;
            System.out.println("NetworkMaster: attempt " + ordinal + " exists (\"" + toast + "\") — retrying with a different Pricing Policy");
        }

        String actual = toast == null || toast.isEmpty() ? "No toast appeared"
                : (ok ? toast : (exists ? "Still 'already exists' after trying " + used + " Pricing Policy option(s): \"" + toast + "\""
                                        : "Save not confirmed — server returned: \"" + toast + "\""));
        step(page, "Click Submit & success toast (retry Pricing Policy on 'exists')",
                "Click Submit; if it says the combination already exists, pick a different Pricing Policy and Submit again",
                "'... saved successfully.' toast",
                (used > 1 && ok ? "(after " + used + " Pricing Policy attempts) " : "") + actual, ok ? "PASS" : "FAIL");

        // 5) Check the table
        if (ok) {
            String row = nw.findAddedInList();
            boolean found = row != null && !row.isEmpty();
            step(page, "Check the list table for the added row",
                    "Open the Network Master list; find the row just added (Location / Payor / Network / Pricing Policy)",
                    "The newly added Network Master row is shown in the table",
                    found ? "Found in table: " + row
                          : "Saved (toast confirmed) but the new row was not located — Location=" + nw.lastLocation
                                    + ", Payor=" + nw.lastPayor + ", Network=" + nw.lastNetwork + ", Pricing Policy=" + nw.lastPricingPolicy,
                    found ? "PASS" : "FAIL");
            addSummary("Added row", found ? row : "Not located in table");
        }

        addSummary("Pricing Policy attempts", String.valueOf(used));
        addSummary("Result", ok ? toast : "Not confirmed (\"" + toast + "\")");
    }
}
