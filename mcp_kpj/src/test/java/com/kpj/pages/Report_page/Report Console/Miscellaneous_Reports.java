package com.kpj.pages.Report_page.Report_Console;   // folder: Report_page/Report Console (team structure)

import com.microsoft.playwright.Page;

/**
 * Report &gt; Report Console &gt; <b>Miscellaneous Reports</b> — Page Object.
 *
 * <p>Everything the console does is shared in {@link ReportConsole}; this class only names the category.</p>
 */
public class Miscellaneous_Reports extends ReportConsole {

    /** The category button on the right of the console. */
    public static final String CATEGORY = "Miscellaneous Reports";

    public Miscellaneous_Reports(Page page) { super(page); }

    /** Report → Report Console → Miscellaneous Reports. True when its reports are listed in the Reports dropdown. */
    public boolean open() {
        return navigateViaMenu() && openCategory(CATEGORY);
    }
}
