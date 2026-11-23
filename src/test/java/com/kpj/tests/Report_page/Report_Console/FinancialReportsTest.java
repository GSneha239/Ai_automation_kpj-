package com.kpj.tests.Report_page.Report_Console;

import com.kpj.pages.Report_page.ReportConsolePage;
import org.junit.jupiter.api.Test;

/**
 * Report &gt; Report Console &gt; <b>Financial Report</b> — every listed report, READ-ONLY.
 * FR-001 navigation, FR-002 page load/filters, FR-003 filter values, FR-004 Show Report, FR-005 report grid/data.
 * Report: test-output/reports/Financial_Reports.html
 * Run: mvn test -Dtest=com.kpj.tests.Report_page.Report_Console.FinancialReportsTest -Ddevhis.postrun=off
 */
public class FinancialReportsTest extends ReportConsoleSuite {

    public FinancialReportsTest() {
        super("Financial_Reports", "FR", ReportConsolePage.FINANCIAL_BTN, "Financial Report");
    }

    public static void main(String[] args) {
        FinancialReportsTest t = new FinancialReportsTest();
        try { t.run(); } finally { t.stop(); t.relabelReport(); }
    }

    @Test
    public void execute() {
        try { run(); } finally { stop(); relabelReport(); }
    }
}
