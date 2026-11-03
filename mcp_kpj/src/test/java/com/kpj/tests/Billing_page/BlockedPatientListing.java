package com.kpj.tests.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// The page object is also named BlockedPatientListing — referenced by its fully-qualified name.

/**
 * Billing &gt; <b>Blocked Patient Listing</b> ({@code #/BlockedPatientListing}).
 *
 * <ol>
 *   <li>Login (shared {@link LoginPage}, on an OPD counter so the menu exists); open <b>Billing</b> → <b>Blocked Patient Listing</b>.</li>
 *   <li>Enter the <b>From Date</b> and <b>To Date</b> — a three-month range ending today; click <b>Search</b> (not in the written steps: the list is only refreshed by Search).</li>
 *   <li>Check the records displayed in <b>Blocked Patient Listing Detail</b>, then open <b>Block/Unblock History</b> and check its records.</li>
 * </ol>
 *
 * <p>Read-only: nothing is blocked or unblocked. The records are the ones the environment holds — three months back from today. When none were blocked in that window the checks fail saying so
 * (data, not a defect); pin another range with {@code -Dbpl.months=} (months back, default 3).</p>
 */
public class BlockedPatientListing extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public BlockedPatientListing() { super("Billing_BlockedPatientListing"); }

    public static void main(String[] args) {
        BlockedPatientListing t = new BlockedPatientListing();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Billing - Blocked Patient Listing", "Billing > Blocked Patient Listing",
                "Read-only: enter a three-month date range, Search, check the records in Blocked Patient Listing Detail, open Block/Unblock History and check its records.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        int months = Integer.getInteger("bpl.months", 3);
        String to = LocalDate.now().format(DMY), from = LocalDate.now().minusMonths(months).format(DMY);

        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")",
                "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Billing_page.BlockedPatientListing bp = new com.kpj.pages.Billing_page.BlockedPatientListing(page);

        // 1) Navigate
        boolean on = bp.navigateViaMenu();
        step(page, "Open Blocked Patient Listing screen", "Click Billing -> Blocked Patient Listing", "The Blocked Patient Listing screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED — Blocked Patient Listing not reached"); return; }

        // 2) Dates
        String dates = bp.enterDateRange(from, to);
        boolean datesOk = bp.ok();
        step(page, "Enter from date and to date", "Enter the From Date " + from + " and the To Date " + to + " (a " + months + "-month range)", "Both dates are entered",
                datesOk ? "From Date " + bp.lastFrom + ", To Date " + bp.lastTo + " (" + dates + ")" : "FAILED — " + bp.problems, datesOk ? "PASS" : "FAIL");
        if (!datesOk) { addSummary("Result", "FAILED — dates: " + bp.problems); return; }

        // 3) Search (not in the written steps)
        int n = bp.clickSearch();
        boolean searched = bp.lastSearchStatus > 0 && bp.lastSearchStatus < 400;
        step(page, "Click search", "Click Search — not in the written steps: the list is only refreshed by Search", "The search is answered",
                searched ? "Search answered HTTP " + bp.lastSearchStatus : "FAILED — " + bp.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED — Search: " + bp.problems); return; }

        // 4) Blocked Patient Listing Detail
        List<String> heads = bp.headers(com.kpj.pages.Billing_page.BlockedPatientListing.DETAIL);
        List<String> outside = bp.rowsOutsideRange(com.kpj.pages.Billing_page.BlockedPatientListing.DETAIL, from, to);
        boolean shown = n > 0 && heads.stream().anyMatch(h -> h.equalsIgnoreCase("MRN No.")) && outside.isEmpty();
        step(page, "Verify records are displayed in Blocked Patient Listing Detail", "Read Blocked Patient Listing Detail after Search",
                "The blocked patients are listed, each blocked inside the date range",
                shown ? n + " record(s) listed, all blocked between " + from + " and " + to + "; columns " + heads + "; first: " + bp.sample(com.kpj.pages.Billing_page.BlockedPatientListing.DETAIL)
                      : n == 0 ? "FAILED — Blocked Patient Listing Detail shows 0 records for " + from + " - " + to + " (no patient was blocked in that window on this environment)"
                               : "FAILED — " + n + " record(s) listed but " + (outside.isEmpty() ? "the MRN No. column is missing (columns " + heads + ")" : "these lie outside " + from + " - " + to + ": " + outside),
                shown ? "PASS" : "FAIL");
        addSummary("Blocked Patient Listing Detail", n + " record(s), " + from + " - " + to);

        // 5) Block/Unblock History
        int h = bp.openHistory();
        boolean historyOpen = bp.historyShown();
        step(page, "Open Block/Unblock History", "Click the Block/Unblock History tab", "The history tab opens and its search is answered",
                historyOpen && bp.lastHistoryStatus > 0 && bp.lastHistoryStatus < 400 ? "History tab open (HTTP " + bp.lastHistoryStatus + ")" : "FAILED — " + (bp.ok() ? "the history grid is not on screen" : bp.problems),
                historyOpen && bp.lastHistoryStatus > 0 && bp.lastHistoryStatus < 400 ? "PASS" : "FAIL");

        // 6) its records (and that every blocked patient of the Detail tab has a history entry)
        List<String> hHeads = bp.headers(com.kpj.pages.Billing_page.BlockedPatientListing.HISTORY);
        List<String> hOutside = bp.rowsOutsideRange(com.kpj.pages.Billing_page.BlockedPatientListing.HISTORY, from, to);
        List<String> historyMrns = bp.mrns(com.kpj.pages.Billing_page.BlockedPatientListing.HISTORY);
        List<String> missing = new ArrayList<>();
        for (String m : bp.mrns(com.kpj.pages.Billing_page.BlockedPatientListing.DETAIL)) if (!historyMrns.contains(m)) missing.add(m);
        boolean hShown = h > 0 && hHeads.stream().anyMatch(x -> x.equalsIgnoreCase("MRN No.")) && hOutside.isEmpty() && missing.isEmpty();
        step(page, "Verify records are displayed in Block/Unblock History", "Read the Block/Unblock History records",
                "The block/unblock events are listed inside the date range, and every blocked patient of the Detail tab has an entry",
                hShown ? h + " record(s) listed, all dated between " + from + " and " + to + "; columns " + hHeads + "; statuses " + bp.historyStatuses() + "; every one of the "
                        + bp.mrns(com.kpj.pages.Billing_page.BlockedPatientListing.DETAIL).size() + " MRN(s) of Blocked Patient Listing Detail has a history entry; first: " + bp.sample(com.kpj.pages.Billing_page.BlockedPatientListing.HISTORY)
                       : h == 0 ? "FAILED — Block/Unblock History shows 0 records for " + from + " - " + to
                                : "FAILED — " + h + " record(s) listed but " + (!hOutside.isEmpty() ? "these lie outside " + from + " - " + to + ": " + hOutside
                                    : !missing.isEmpty() ? "blocked patient(s) of Blocked Patient Listing Detail with no history entry: " + missing : "the MRN No. column is missing (columns " + hHeads + ")"),
                hShown ? "PASS" : "FAIL");
        addSummary("Block/Unblock History", h + " record(s), " + from + " - " + to);
    }
}
