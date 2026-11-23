package com.kpj.tests.Report_page.Report_Console;

/**
 * Report &gt; Report Console &gt; <b>Miscellaneous Reports</b> — every Miscellaneous report in ONE test report, one test step per
 * report (report {@code Report_ReportConsole_MiscellaneousReports}). The steps are in {@link ReportCategoryFlow}.
 */
public class Miscellaneous_Reports extends ReportCategoryFlow {

    public Miscellaneous_Reports() { super("Report_ReportConsole_MiscellaneousReports"); }

    public static void main(String[] args) {
        Miscellaneous_Reports t = new Miscellaneous_Reports();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String category() { return com.kpj.pages.Report_page.Report_Console.Miscellaneous_Reports.CATEGORY; }

    @Override
    protected String idPrefix() { return "MISC"; }
}
