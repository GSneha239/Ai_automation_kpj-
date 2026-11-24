package com.kpj.tests.Report_page.Report_Console;

/**
 * Report &gt; Report Console &gt; <b>Patient Report</b> — every Patient report in ONE test report (report
 * {@code Report_ReportConsole_PatientReport}); each report is a section of "&lt;report&gt; - &lt;action&gt;" steps. The steps are in
 * {@link ReportCategoryFlow}.
 *
 * <p>Dates: two months before today → today. When a report says "No Data Available" it is run once more with new random values
 * and the dates 01/01/2020 → today (override with {@code -Dreport.retryFrom=dd/MM/yyyy}); still no data = FAIL.</p>
 */
public class Patient_Report extends ReportCategoryFlow {

    public Patient_Report() { super("Report_ReportConsole_PatientReport"); }

    public static void main(String[] args) {
        Patient_Report t = new Patient_Report();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String category() { return com.kpj.pages.Report_page.Report_Console.Patient_Report.CATEGORY; }

    @Override
    protected String idPrefix() { return "PAT"; }

    @Override
    protected java.time.LocalDate retryFrom() {
        return java.time.LocalDate.parse(System.getProperty("report.retryFrom", "01/01/2020"), java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }
}
