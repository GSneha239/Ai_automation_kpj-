package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.microsoft.playwright.Page;

/**
 * Report &gt; Report Console &gt; <b>MIS Report</b> — Page Object.
 *
 * <p>Everything the console does (open it, pick a report, add every filter with a value, Show Report, capture every page of the
 * report) is shared in {@link ReportConsole}; this class only names the category, so the other categories (Financial_Report,
 * Miscellaneous_Reports, …) can be added the same way.</p>
 */
public class MIS_Report extends ReportConsole {

    /** The category button on the right of the console. */
    public static final String CATEGORY = "MIS Report";

    public MIS_Report(Page page) { super(page); }

    /** Report → Report Console → MIS Report. True when the MIS reports are listed in the Reports dropdown. */
    public boolean open() {
        return navigateViaMenu() && openCategory(CATEGORY);
    }

    /** Open the MIS Report category again (after the console had to be re-opened). */
    public boolean reopen() {
        if (!onScreen()) navigateViaMenu();
        return openCategory(CATEGORY);
    }
}
