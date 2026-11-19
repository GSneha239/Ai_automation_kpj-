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

        // The list can hold reservations whose patient is already ADMITTED (the Pre-admission test admits the patients it reserves): the application refuses to unreserve those with "The patient is already
        // admitted!". They are not what this test is about, so such a row is skipped and the next one tried (any OTHER refusal stops the search and fails the step). The skipped rows are named in the report.
        final java.util.List<String> skipped = new java.util.ArrayList<>();
        String patient = null, toast = "", lastPatient = null;
        boolean ok = false;
        byte[] selectShot = null;
        int usedRow = -1;
        for (int idx = 0; idx < rows && !ok; idx++) {
            String p = un.selectReservedBed(idx);
            if (p == null) { skipped.add("row " + (idx + 1) + " could not be ticked"); continue; }
            lastPatient = p;
            byte[] shot = null;
            try { shot = page.screenshot(new com.microsoft.playwright.Page.ScreenshotOptions().setTimeout(40000)); } catch (Exception e) { shot = null; }
            String bed = un.lastBed, from = un.lastFromDate;
            String t = un.clickUnreserveAndGetToast();
            String tl = t == null ? "" : t.toLowerCase();
            boolean good = tl.contains("unreserv") || tl.contains("success") || tl.contains("saved");
            toast = t == null ? "" : t;
            if (good) { ok = true; patient = p; selectShot = shot; usedRow = idx; break; }
            if (tl.contains("already admitted")) { skipped.add("row " + (idx + 1) + " " + p + " (" + bed + ", from " + from + ") refused: \"" + toast.replaceAll("^.*KPJ Portal", "").trim() + "\""); continue; }
            patient = p;                      // some other answer (or none): stop here and report it
            break;
        }
        if (patient == null) patient = lastPatient;
        if (patient == null) { step(page, "Select a reserved bed", "Tick the SELECT checkbox of a row in the list", "A reserved bed is selected", "No row could be ticked" + (skipped.isEmpty() ? "" : " (" + skipped + ")"), "FAIL"); return; }

        String selActual = "Selected: " + patient + " (" + un.lastBed + ", from " + un.lastFromDate + ")"
                + (skipped.isEmpty() ? "" : ". Rows skipped first because the application refused them: " + skipped);
        if (selectShot != null) step(selectShot, "Select a reserved bed", "Tick the SELECT checkbox of a row in the list (a row of an already admitted patient is skipped)", "A reserved bed is selected", selActual, "PASS");
        else step(page, "Select a reserved bed", "Tick the SELECT checkbox of a row in the list (a row of an already admitted patient is skipped)", "A reserved bed is selected", selActual, "PASS");

        step(page, "Click Unreserve", "Click Unreserve (SaveUnreserve())",
                "'... Unreserved Successfully.' toast", toast.isEmpty() ? "No success toast appeared" : toast, ok ? "PASS" : "FAIL");

        boolean listed = un.verifyInUnreservedList(patient);
        step(page, "Verify in List of Unreserved Beds", "Check the List of Unreserved Beds table for " + patient + " / bed " + un.lastBed + " / from " + un.lastFromDate,
                "The unreserved bed is listed for the patient", listed ? patient + " (" + un.lastBed + ", from " + un.lastFromDate + ") found in List of Unreserved Beds" : patient + " (" + un.lastBed + ", from " + un.lastFromDate + ") NOT found in the list",
                listed ? "PASS" : "FAIL");
        addSummary("Reserved beds listed", String.valueOf(rows));
        if (!skipped.isEmpty()) addSummary("Skipped (already admitted)", String.valueOf(skipped));
        addSummary("Unreserved", ok ? patient + " (" + un.lastBed + ", from " + un.lastFromDate + ")" : "(none)");
        addSummary("Result", (ok ? toast : "not confirmed") + (listed ? " | listed as unreserved" : " | not listed"));
    }
}
