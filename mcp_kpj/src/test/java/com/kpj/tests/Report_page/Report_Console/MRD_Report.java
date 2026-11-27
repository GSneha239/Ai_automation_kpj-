package com.kpj.tests.Report_page.Report_Console;

/**
 * Report &gt; Report Console &gt; <b>MRD Report</b> — every MRD report in ONE test report (report
 * {@code Report_ReportConsole_MRDReport}); each report is a section of "&lt;report&gt; - &lt;action&gt;" steps: select the report
 * (Consultant Analysis first), select EVERY filter in the Filter dropdown, <b>Add</b> it, <b>Add Value</b> and choose its value, then
 * <b>Show Report</b> - the report opens in a new tab and a screenshot of it (all pages) is taken; next report, until all are covered.
 * The steps are in {@link ReportCategoryFlow}.
 *
 * <p>Dates: two months before today → today. When a report says "No Data Available" it is run once more with new random values
 * and the dates 01/01/2020 → today (override with {@code -Dreport.retryFrom=dd/MM/yyyy}); still no data = FAIL. Run one report only
 * with {@code -Dreport.pin="Consultant Analysis"}. Read-only.</p>
 */
public class MRD_Report extends ReportCategoryFlow {

    public MRD_Report() { super("Report_ReportConsole_MRDReport"); }

    public static void main(String[] args) {
        MRD_Report t = new MRD_Report();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String category() { return com.kpj.pages.Report_page.Report_Console.MRD_Report.CATEGORY; }

    @Override
    protected String idPrefix() { return "MRD"; }

    @Override
    protected java.time.LocalDate retryFrom() {
        return java.time.LocalDate.parse(System.getProperty("report.retryFrom", "01/01/2020"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
