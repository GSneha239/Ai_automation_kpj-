package com.kpj.tests.Report_page.Report_Console;

/**
 * Report &gt; Report Console &gt; <b>OT Reports</b> — every OT report in ONE test report (report
 * {@code Report_ReportConsole_OTReports}); each report is a section of "&lt;report&gt; - &lt;action&gt;" steps: select the report,
 * select EVERY filter in the Filter dropdown, <b>Add</b> it, <b>Add Value</b> and choose its value, then <b>Show Report</b> - the
 * report opens in a new tab and a screenshot of it (all pages) is taken; next report, until all are covered. The steps are in
 * {@link ReportCategoryFlow}.
 *
 * <p>Dates: two months before today → today. When a report says "No Data Available" it is run once more with new random values
 * and the dates 01/01/2020 → today (override with {@code -Dreport.retryFrom=dd/MM/yyyy}); still no data = FAIL. Run one report only
 * with {@code -Dreport.pin="<report name>"}. Read-only.</p>
 */
public class OT_Reports extends ReportCategoryFlow {

    public OT_Reports() { super("Report_ReportConsole_OTReports"); }

    public static void main(String[] args) {
        OT_Reports t = new OT_Reports();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String category() { return com.kpj.pages.Report_page.Report_Console.OT_Reports.CATEGORY; }

    @Override
    protected String idPrefix() { return "OT"; }

    @Override
    protected java.time.LocalDate retryFrom() {
        return java.time.LocalDate.parse(System.getProperty("report.retryFrom", "01/01/2020"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
