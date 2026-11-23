package com.kpj.pages.ApplicationConfiguration_page.General_page;

import com.microsoft.playwright.Page;

/**
 * Application Configuration &gt; General &gt; <b>Drug Admin Route</b> — configuration screen Page Object.
 *
 * <p>Route {@code #/ROUTE}. It is the SAME generic common-master screen as {@link QualityIndicator} (Form Name select with the
 * whole list of masters, Code, Remark, Submit, Code | Remark | Status | Edit grid), reached through a different menu entry — only the
 * entry that is clicked and the stamps written into the form differ, so everything else is inherited.</p>
 *
 * <p>Not to be confused with <b>Route Master</b> ({@code #/RouteMaster}), which sits in the same menu group.</p>
 */
public class DrugAdminRoute extends QualityIndicator {

    public DrugAdminRoute(Page page) {
        super(page, "drug\\s*admin(istration)?\\s*route", "#/route", "DR", "Auto drug admin route ", "DrugAdminRoute");
    }

    /** {@code #/RouteMaster} also contains "#/route" — it is a different screen and must not count as this one. */
    @Override
    public boolean onScreen() {
        return super.onScreen() && !page.url().toLowerCase().contains("routemaster");
    }
}
