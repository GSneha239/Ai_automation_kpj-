package com.kpj.tests.Ip.BedManagement_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

// The page object is also named Unreservation — referenced by its fully-qualified name.

/**
 * IP &gt; Bed Management &gt; <b>Unreservation</b> ({@code #/UnReservation}).
 *
 * <ol>
 *   <li>Open Unreservation, search with no MRN filter to list every CURRENTLY reserved bed.</li>
 *   <li>Select the first reserved bed in the <b>List of Reserved Beds</b> table — whichever one is already
 *       there, not one this test just created.</li>
 *   <li><b>Unreserve</b> ({@code SaveUnreserve()}) → verify it appears in the <b>List of Unreserved Beds</b> table.</li>
 * </ol>
 *
 * <p>Matches the M6 Bed Planning FSD (§B. Unreservation): "Go to IP &gt; Bed Management &gt; UnReservation; adjust
 * the filters as necessary, search the patient using the MRN field, click Search; [select the reserved bed] …".
 * No setup reservation is made here — any bed already sitting in the reserved-beds list is a valid candidate to
 * unreserve, and reserving one immediately before unreserving it risks the list not yet reflecting the reservation
 * that was just made.</p>
 */
public class Unreservation extends DevHisBase {

    public Unreservation() { super("Unreservation"); }

    public static void main(String[] args) {
        Unreservation t = new Unreservation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("IP - Bed Management - Unreservation", "IP > Bed Management > Unreservation",
                "Unreservation: search with no MRN filter to list every reserved bed -> select the first one -> Unreserve -> verify in List of Unreserved Beds.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Ip.BedManagement_page.Unreservation un =
                new com.kpj.pages.Ip.BedManagement_page.Unreservation(page);
        boolean opened = un.navigateViaMenu() || page.url().toLowerCase().contains("unreservation");
        step(page, "Open Unreservation", "IP > Bed Management > Unreservation",
                "The Unreservation (#/UnReservation) screen is shown", opened ? "Opened " + page.url() : "Did NOT reach the screen",
                page.url().toLowerCase().contains("unreservation") ? "PASS" : "FAIL");

        // No MRN filter — list whatever is ALREADY reserved rather than reserving something new first.
        int rows = un.enterMrnAndSearch("");
        step(page, "Search (no MRN filter)", "Click Search (fnSearch()) with no MRN filter",
                "Every currently reserved bed shows in the List of Reserved Beds table",
                rows + " row(s) in List of Reserved Beds", rows > 0 ? "PASS" : "FAIL");
        if (rows == 0) { addSummary("Result", "No reserved beds found to unreserve"); return; }

        String patient = un.selectFirstReservedBed();
        step(page, "Select a reserved bed", "Tick the SELECT checkbox of the first row in the list",
                "A reserved bed is selected", patient == null ? "No row selected" : "Selected: " + patient,
                patient == null ? "FAIL" : "PASS");
        if (patient == null) return;

        String toast = un.clickUnreserveAndGetToast();
        boolean ok = toast != null && (toast.toLowerCase().contains("unreserv") || toast.toLowerCase().contains("success") || toast.toLowerCase().contains("saved"));
        step(page, "Click Unreserve", "Click Unreserve (SaveUnreserve())",
                "'... Unreserved Successfully.' toast", toast == null || toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        boolean listed = un.verifyInUnreservedList(patient);
        step(page, "Verify in List of Unreserved Beds", "Check the List of Unreserved Beds table for " + patient,
                "The unreserved bed is listed for the patient", listed ? patient + " found in List of Unreserved Beds" : patient + " NOT found in the list",
                listed ? "PASS" : "FAIL");
        addSummary("Result", (ok ? toast : "not confirmed") + (listed ? " | listed as unreserved" : " | not listed"));
    }
}
