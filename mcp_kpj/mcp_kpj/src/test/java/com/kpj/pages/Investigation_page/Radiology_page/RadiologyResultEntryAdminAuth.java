package com.kpj.pages.Investigation_page.Radiology_page;

import com.microsoft.playwright.Page;

/**
 * Investigation &gt; Radiology &gt; <b>Result Entry Admin Auth</b> — Page Object.
 *
 * <p>It is the SAME form as Result Entry: the same models ({@code ResultEntry.*}), the same Search
 * ({@code fnSearchAllorderbookingdetails}), the same tabs ({@code Test} / {@code Report}) and the same
 * report button ({@code PrintPendingResultEntry}). Only the menu entry and the route differ, so
 * everything else is inherited rather than copied — the same pattern as
 * {@link RadiologyResultEntryAuth}.</p>
 *
 * <p>The exact route is not guessed with confidence — DevHIS routes on this module bear no fixed
 * relationship to the label (Result Entry Authentication is {@code #/ResultAuth}, not
 * {@code #/ResultEntryAuthentication}). So {@link #routeMatches} accepts the hash the MENU itself
 * navigated to as authoritative, with the guessed name only a bonus.</p>
 */
public class RadiologyResultEntryAdminAuth extends RadiologyResultEntry {

    public RadiologyResultEntryAdminAuth(Page page) { super(page); }

    /** Anchored to the WHOLE label: a loose match would open Result Entry or Result Entry Authentication. */
    @Override
    protected String menuLabelRegex() { return "^\\s*result\\s*entry\\s*admin\\s*auth\\s*$"; }

    /** Best-guess fallback route, following the {@code ResultAuth} naming of the Authentication screen. */
    @Override
    protected String routeHash() { return "/ResultAdminAuth"; }

    @Override
    protected boolean routeMatches(String route) {
        if (route.equals("resultadminauth")) return true;
        String menuRoute = lastRoute == null ? "" : lastRoute.toLowerCase().replace("#", "").replace("/", "").trim();
        return !menuRoute.isEmpty() && route.equals(menuRoute);
    }
}
