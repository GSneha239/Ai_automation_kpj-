package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.microsoft.playwright.Page;

/**
 * Report &gt; Report Console &gt; <b>Patient Report</b> — Page Object.
 *
 * <p>Everything the console does is shared in {@link ReportConsole} (it also fills this category's own popups: number boxes,
 * Record Status, Date of Birth, the Doctor list's Payable Type); this class only names the category.</p>
 */
public class Patient_Report extends ReportConsole {

    /** The category button on the right of the console. */
    public static final String CATEGORY = "Patient Report";

    public Patient_Report(Page page) { super(page); }

    /** Report → Report Console → Patient Report. True when its reports are listed in the Reports dropdown. */
    public boolean open() {
        return navigateViaMenu() && openCategory(CATEGORY);
    }
}
