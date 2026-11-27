package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.microsoft.playwright.Page;

/**
 * Report &gt; Report Console &gt; <b>OT Reports</b> — Page Object.
 *
 * <p>Everything the console does (open it, pick a report, add every filter with a value, Show Report, capture every page of the
 * report) is shared in {@link ReportConsole}; this class only names the category.</p>
 */
public class OT_Reports extends ReportConsole {

    /** The category button on the right of the console. */
    public static final String CATEGORY = "OT Reports";

    public OT_Reports(Page page) { super(page); }

    /** Report → Report Console → OT Reports. True when the OT reports are listed in the Reports dropdown. */
    public boolean open() {
        return navigateViaMenu() && openCategory(CATEGORY);
    }

    /** Open the OT Reports category again (after the console had to be re-opened). */
    public boolean reopen() {
        if (!onScreen()) navigateViaMenu();
        return openCategory(CATEGORY);
    }
}
