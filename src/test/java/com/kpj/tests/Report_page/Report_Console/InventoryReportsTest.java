package com.kpj.tests.Report_page.Report_Console;

import com.kpj.pages.Report_page.ReportConsolePage;
import org.junit.jupiter.api.Test;

/**
 * Report &gt; Report Console &gt; <b>Inventory Report</b> — every listed report, READ-ONLY.
 * IR-001 navigation, IR-002 page load/filters, IR-003 filter values, IR-004 Show Report, IR-005 report grid/data.
 * Report: test-output/reports/Inventory_Reports.html
 * Run: mvn test -Dtest=com.kpj.tests.Report_page.Report_Console.InventoryReportsTest -Ddevhis.postrun=off
 */
public class InventoryReportsTest extends ReportConsoleSuite {

    public InventoryReportsTest() {
        super("Inventory_Reports", "IR", ReportConsolePage.INVENTORY_BTN, "Inventory Report");
    }

    public static void main(String[] args) {
        InventoryReportsTest t = new InventoryReportsTest();
        try { t.run(); } finally { t.stop(); t.relabelReport(); }
    }

    @Test
    public void execute() {
        try { run(); } finally { stop(); relabelReport(); }
    }
}
