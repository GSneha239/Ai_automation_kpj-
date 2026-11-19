package com.kpj.tests.Finance_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

// The page object is also named SapIntegration — referenced by its fully-qualified name.

/**
 * Finance &gt; <b>Sap Integration</b> ({@code #/sapintegration}) — the day's SAP journal lines and their error lists.
 * <ol>
 *   <li><b>Filters</b> — From/To Date default to today, To Date is locked and follows From Date.</li>
 *   <li><b>Save</b> — clicked on an EMPTY grid only (before any search), so a working Save could not post real data.</li>
 *   <li><b>Search</b> — today's Detail List loads with the SAP columns; the Store filter is sent to the server.</li>
 *   <li><b>Error Log</b> — the Cr-Dr Mismatch and Blank GL Code/Posting Key grids load.</li>
 *   <li><b>Clear</b> — resets the dates and empties the grids.</li>
 * </ol>
 * Creates no data.
 */
public class SapIntegration extends DevHisBase {

    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public SapIntegration() { super("Finance_SapIntegration"); }

    public static void main(String[] args) {
        SapIntegration t = new SapIntegration();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Finance - Sap Integration", "Finance > Sap Integration",
                "Date filters, Search (Detail List + Store filter), Error Log grids, Save (on an empty grid only) and Clear. Creates no data.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.Finance_page.SapIntegration sap = new com.kpj.pages.Finance_page.SapIntegration(page);
        boolean opened = sap.navigateViaMenu();
        step(page, "Open Sap Integration", "Finance > Sap Integration", "The Sap Integration screen opens",
                opened ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", opened ? "PASS" : "FAIL");
        if (!opened) { addSummary("Result", "FAILED - screen not reached"); return; }

        String today = LocalDate.now().format(DMY);
        sectionFilters(sap, today);
        sectionSave(sap);
        sectionSearch(sap, today);
        sectionErrorLog(sap);
        sectionClear(sap, today);

        addSummary("Application URL", BASE + "/#/sapintegration");
        addSummary("Search date", today);
    }

    // ===== Section: Filters =============================================
    private void sectionFilters(com.kpj.pages.Finance_page.SapIntegration sap, String today) {
        String loc = sap.ensureLocation();
        String[] f = sap.filters();
        boolean defaults = today.equals(f[1]) && today.equals(f[2]) && "true".equals(f[3]);
        step(page, "Filters · Default dates", "Look at Location, From Date and To Date",
                "From Date and To Date are today; To Date is locked",
                "Location '" + loc + "' | From " + f[1] + " | To " + f[2] + " | To Date locked=" + f[3], defaults ? "PASS" : "FAIL");

        String yesterday = LocalDate.now().minusDays(1).format(DMY);
        String[] moved = sap.setFromDate(yesterday);
        boolean follows = yesterday.equals(moved[0]) && yesterday.equals(moved[1]);
        sap.setFromDate(today);
        step(page, "Filters · To Date follows From Date", "Change From Date to " + yesterday + " (then back to today)",
                "To Date changes to the same day (one day at a time)",
                "From " + moved[0] + " -> To " + moved[1], follows ? "PASS" : "FAIL");
    }

    // ===== Section: Save ================================================
    private void sectionSave(com.kpj.pages.Finance_page.SapIntegration sap) {
        int rows = sap.gridRows("detail");
        String said = sap.clickSave();
        boolean broken = said.contains("console error");
        step(page, "Save · Save with an empty grid", "Click Save before any search (grid has " + Math.max(rows, 0) + " rows - nothing real can be posted)",
                "Save answers with a message (saved / nothing to save) and raises no error",
                said.isEmpty() ? "No message and no error" : said,
                broken ? "FAIL" : (said.isEmpty() ? "FAIL" : "PASS"));
    }

    // ===== Section: Search ==============================================
    private void sectionSearch(com.kpj.pages.Finance_page.SapIntegration sap, String today) {
        String body = sap.search();
        int rows = sap.gridRows("detail");
        int total = sap.totalRecords();
        step(page, "Search · Search today", "From Date " + today + "; click Search",
                "The Sap Integration Detail List loads that day's SAP lines",
                body == null ? "Search did NOT answer" : rows + " row(s) on this page, " + total + " in total" + (rows > 0 ? " - first: " + sap.firstDetailRow() : " (no SAP lines today)"),
                body != null ? "PASS" : "FAIL");

        java.util.List<String> cols = sap.detailColumns();
        boolean colsOk = cols.size() >= 20;
        step(page, "Search · Detail List columns", "Look at the Detail List grid columns", "The SAP columns are shown (Doc Group No, dates, GL Code, amounts, Cr/Dr …)",
                cols.size() + " columns" + (cols.isEmpty() ? "" : ": " + String.join(", ", cols.subList(0, Math.min(8, cols.size()))) + " …"),
                colsOk ? "PASS" : "FAIL");

        String store = sap.chooseFirstStore();
        if (store.isEmpty()) {
            step(page, "Search · Store filter", "Choose a Store and Search", "The Store is sent with the search", "Store list is empty - nothing to check", "PASS");
        } else {
            String sent = sap.search();
            boolean storeSent = sent != null && sent.toLowerCase().matches(".*\"store(id)?\"\\s*:.*");
            step(page, "Search · Store filter", "Choose Store '" + store + "' and click Search",
                    "The chosen Store is sent with the search (it narrows the list)",
                    sent == null ? "Search did NOT answer" : (storeSent ? "Store is sent: " + sent : "Store is NOT sent - the search sends only " + sent),
                    storeSent ? "PASS" : "FAIL");
        }
    }

    // ===== Section: Error Log ===========================================
    private void sectionErrorLog(com.kpj.pages.Finance_page.SapIntegration sap) {
        sap.openTab("Error Log");
        boolean h1 = sap.headingShown("Cr-Dr Mismatch");
        boolean h2 = sap.headingShown("Blank GL Code/Posting Key");
        int mismatch = sap.gridRows("mismatch");
        int blank = sap.gridRows("blankgl");
        step(page, "Error Log · Error grids", "Click the Error Log tab",
                "The Cr-Dr Mismatch and Blank GL Code/Posting Key grids are shown with the day's errors",
                "Cr-Dr Mismatch shown=" + h1 + " (" + mismatch + " row(s)) | Blank GL Code/Posting Key shown=" + h2 + " (" + blank + " row(s))",
                h1 && h2 && mismatch >= 0 && blank >= 0 ? "PASS" : "FAIL");
        sap.openTab("Sap Integration Detail List");
    }

    // ===== Section: Clear ===============================================
    private void sectionClear(com.kpj.pages.Finance_page.SapIntegration sap, String today) {
        String yesterday = LocalDate.now().minusDays(1).format(DMY);
        sap.setFromDate(yesterday);
        sap.search();
        int before = sap.gridRows("detail");
        String said = sap.clickClear();
        String[] f = sap.filters();
        int after = sap.gridRows("detail");
        boolean cleared = today.equals(f[1]) && after <= 0;
        step(page, "Clear · Clear the screen", "Search " + yesterday + " (" + before + " row(s)), then click Clear",
                "From/To Date go back to today and the grids are emptied",
                "After Clear: From " + f[1] + " | To " + f[2] + " | Detail List " + after + " row(s)" + (said.isEmpty() ? "" : " | " + said),
                cleared ? "PASS" : "FAIL");
    }
}
