package com.kpj.pages.Investigation_page.Radiology_page;

import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Radiology &gt; <b>Result Entry Authentication</b> — Page Object.
 *
 * <p>It is the SAME form as Result Entry: the same models ({@code ResultEntry.*}), the same Search
 * ({@code fnSearchAllorderbookingdetails}) and the same report button
 * ({@code PrintPendingResultEntry}), with a radiologist and diagnosis filter added. Only the menu entry
 * and the route differ, so everything else is inherited rather than copied.</p>
 */
public class RadiologyResultEntryAuth extends RadiologyResultEntry {

    public RadiologyResultEntryAuth(Page page) { super(page); }

    @Override
    protected String menuLabelRegex() { return "^\\s*result\\s*entry\\s*authentication\\s*$"; }

    @Override
    protected String routeHash() { return "/ResultAuth"; }

    @Override
    protected boolean routeMatches(String route) { return route.equals("resultauth"); }
}
