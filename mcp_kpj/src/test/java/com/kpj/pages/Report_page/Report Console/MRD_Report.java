package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.microsoft.playwright.Page;

/**
 * Report &gt; Report Console &gt; <b>MRD Report</b> — Page Object.
 *
 * <p>Everything the console does (open it, pick a report, add every filter with a value, Show Report, capture every page of the
 * report) is shared in {@link ReportConsole}; this class only names the category. Confirmed live (v2026.11.24.529): 14 reports in the
 * Reports dropdown - Consultant Analysis, Diagnosis Specific E-Notification Status Report, PER-PS101 BILL AMOUNT / REPORT, PER-PS102 /
 * PS201 / PS202 / PS203 REPORT, PS 203 Health Tourism Report, PS-203 Report Top 5 ICD and the four SMRP (PD101, PD301, RH101, RH301) registers.</p>
 */
public class MRD_Report extends ReportConsole {

    /** The category button on the right of the console. */
    public static final String CATEGORY = "MRD Report";

    public MRD_Report(Page page) { super(page); }

    /** Report → Report Console → MRD Report. True when the MRD reports are listed in the Reports dropdown. */
    public boolean open() {
        return navigateViaMenu() && openCategory(CATEGORY);
    }

    /** Open the MRD Report category again (after the console had to be re-opened). */
    public boolean reopen() {
        if (!onScreen()) navigateViaMenu();
        return openCategory(CATEGORY);
    }
}
