package com.kpj.tests.Report_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

// The page object is also named PatientDataExtraction — referenced by its fully-qualified name.

/**
 * Report &gt; <b>Patient Data Extraction</b> ({@code #/PatientAnalysisReport}).
 *
 * <ol>
 *   <li>Login (on an OPD counter so the sidebar menu exists); open <b>Report</b> &rarr; <b>Patient Data Extraction</b>.</li>
 *   <li>Enter the date range - the <b>Visit Date</b> box is ticked and From is 1 month before To (To is the date the screen opens with, today).</li>
 *   <li>Click <b>Search</b>.</li>
 *   <li>The data is displayed in the results table: verify it lists patients and take a screenshot of the data.</li>
 * </ol>
 *
 * <p>Read-only: nothing is saved (the CRM test of the same screen does the Save Data part). The results table of this screen is titled
 * <b>Clinical Filter</b> - the title is read back into the report; "List Of Item Enquiry" is the grid of the Patient Extraction Report screen.</p>
 */
public class PatientDataExtraction extends DevHisBase {

    /** DevHIS only builds the nav menu for an outpatient counter. Override with {@code -Dcounter=}. */
    public static final String COUNTER_FOR_MENU = "OPD-B-01";

    public PatientDataExtraction() { super("Report_PatientDataExtraction"); }

    public static void main(String[] args) {
        PatientDataExtraction t = new PatientDataExtraction();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Report - Patient Data Extraction", "Report > Patient Data Extraction",
                "Open Report > Patient Data Extraction, enter a 1 month Visit Date range, click Search, verify the data in the results table and take a screenshot of it. Read-only.");

        String counter = System.getProperty("counter", COUNTER_FOR_MENU);
        new LoginPage(page).login(BASE, USER, PASS, counter);
        step("Login", USER + " login (counter " + counter + ")", "Authenticated; Patient Dashboard with the navigation menu", "Logged in", "PASS");

        com.kpj.pages.Report_page.PatientDataExtraction pd = new com.kpj.pages.Report_page.PatientDataExtraction(page);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // 1) Navigate
        boolean on = pd.navigateViaMenu();
        step(page, "Open Patient Data Extraction", "Click Report -> Patient Data Extraction", "The Patient Data Extraction screen is shown",
                on ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")", on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - Patient Data Extraction not reached"); return; }

        // 2) Date range: Visit Date ticked, From 1 month before To
        LocalDate to = pd.defaultToDate();
        LocalDate from = to.minusMonths(1);
        boolean ticked = pd.tickVisitDate();
        boolean dated = ticked && pd.enterVisitFrom(from);
        step(page, "Enter date range - 1 month", "Tick Visit Date and enter From " + from.format(dmy) + " (1 month before To " + to.format(dmy) + ")",
                "From is 1 month before To", dated
                        ? "PASSES because Visit Date is ticked, From \"" + pd.lastFrom + "\" was entered through the calendar and To is \"" + pd.lastTo + "\"; the boxes and the screen's model hold them"
                        : "FAILS because " + pd.problems, dated ? "PASS" : "FAIL");
        if (!dated) { addSummary("Result", "FAILED - date range: " + pd.problems); return; }

        // 3) Search
        boolean searched = pd.clickSearch();
        List<String[]> rows = pd.rows();
        step(page, "Click Search", "Click Search", "The server is asked for the patients of the date range",
                searched ? "PASSES because Search was clicked and the table shows " + rows.size() + " row(s) on this page, pager \"" + pd.lastPager + "\""
                         : "FAILS because " + pd.problems, searched ? "PASS" : "FAIL");
        if (!searched) { addSummary("Result", "FAILED - search: " + pd.problems); return; }

        // 4) the data + screenshot (the grid is scrolled to the top of the window first, so the picture shows the data)
        pd.scrollGridIntoView();
        String title = pd.tableTitle();
        boolean hasData = !rows.isEmpty();
        step(page, "Verify the data in the table and take a screenshot", "Look at the results table after Search (screenshot attached)", "The data is displayed in the table",
                hasData ? "PASSES because the table" + (title.isEmpty() ? "" : " \"" + title + "\"") + " lists " + rows.size() + " record(s) on this page (pager \"" + pd.lastPager + "\"; columns " + pd.headings() + "): "
                        + rows.stream().limit(3).map(r -> r[0] + " / " + r[1] + " / " + r[2]).toList() + (rows.size() > 3 ? " ..." : "")
                        : "FAILS because the table is BLANK for " + pd.lastFrom + " - " + pd.lastTo,
                hasData ? "PASS" : "FAIL");

        addSummary("Date range (Visit Date)", pd.lastFrom + " - " + pd.lastTo + " (1 month)");
        addSummary("Table", (title.isEmpty() ? "(title not found)" : title) + " - " + pd.lastPager + "; columns " + pd.headings());
        addSummary("Result", hasData ? "The table lists " + rows.size() + " record(s) on this page for the 1 month range" : "FAILED - the table is blank");
    }
}
