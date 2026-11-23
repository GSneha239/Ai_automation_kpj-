package com.kpj.tests.Report_page.Report_Console;

/**
 * Report &gt; Report Console &gt; <b>MIS Report</b> — every MIS report in ONE test report, one test step per report
 * (report {@code Report_ReportConsole_MISReport}). The steps are in {@link ReportCategoryFlow}.
 */
public class MIS_Report extends ReportCategoryFlow {

    public MIS_Report() { super("Report_ReportConsole_MISReport"); }

    public static void main(String[] args) {
        MIS_Report t = new MIS_Report();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String category() { return com.kpj.pages.Report_page.Report_Console.MIS_Report.CATEGORY; }

    @Override
    protected String idPrefix() { return "MIS"; }
}
