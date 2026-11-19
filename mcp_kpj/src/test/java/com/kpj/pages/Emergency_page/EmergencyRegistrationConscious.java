package com.kpj.pages.Emergency_page;

import com.kpj.pages.Op_page.RegistrationPage;
import com.microsoft.playwright.Page;

/**
 * Emergency &gt; <b>Emergency Registration (Conscious)</b> — Page Object (route
 * {@code #/EmergencyRegistrationConsious}, the app's own spelling "Consious").
 *
 * <p>A conscious emergency patient gives their full details, so this screen is the SAME form as OP
 * Registration — identical {@code Registration.*}/{@code Visit.*} ng-models, the same element IDs
 * ({@code #txtFirstName}, {@code #PhotoData} …) and the same {@code IUDRegistration()} save that reaches the
 * "Do You Want To Save" confirm, the PDPA Consent modal and the RegistrationReport tabs. It therefore
 * <b>extends {@link RegistrationPage}</b> to inherit every section-fill / save / consent / report method, and
 * only overrides {@link #open(String)} to open the screen from the Emergency menu instead of {@code #/VisitScreen}.</p>
 */
public class EmergencyRegistrationConscious extends RegistrationPage {

    /** In-app hash route for Emergency Registration (Conscious) — note the app's misspelling. */
    public static final String ROUTE = "#/EmergencyRegistrationConsious";

    public EmergencyRegistrationConscious(Page page) { super(page); }

    /** How the screen was reached: "menu" (sidebar click) or "route" (hash fallback). */
    public String reachedVia = "";

    /**
     * Click <b>Emergency</b> → <b>Emergency Registration (Conscious)</b> in the sidebar. The link is looked up by its href INSIDE the
     * Emergency entry (the sibling "Emergency Registration (Unconscious)" has a similar name). Returns true when the registration
     * form (Save button + First Name) is up.
     */
    public boolean navigateViaMenu() {
        try { page.waitForFunction("() => window.angular", null, new Page.WaitForFunctionOptions().setTimeout(30000)); } catch (Exception ignore) { }
        for (int attempt = 1; attempt <= 3 && !onScreen(); attempt++) {
            expandMenuEntry(null, "^\\s*emergency\\s*$");
            Object tagged = page.evaluate("(h) => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); const vis=e=>!!e && !!(e.offsetWidth||e.offsetHeight||e.getClientRects().length);"
                    + " const top=[...document.querySelectorAll('a')].find(x=>/^\\s*emergency\\s*$/i.test(norm(x.textContent)) && vis(x));"
                    + " const root=top ? top.closest('li') : document;"
                    + " const a=[...root.querySelectorAll('a[href]')].filter(vis).find(x=>(x.getAttribute('href')||'').toLowerCase().replace(/\\/$/,'')===h);"
                    + " if(!a) return false; a.id='__erConLink'; return true; }", ROUTE.toLowerCase());
            if (Boolean.TRUE.equals(tagged)) {
                try { page.locator("#__erConLink").click(new com.microsoft.playwright.Locator.ClickOptions().setTimeout(6000)); }
                catch (Exception e) { System.out.println("EmergencyRegistrationConscious.nav: click failed - " + String.valueOf(e.getMessage()).split("\n")[0]); }
                page.evaluate("() => { const e=document.getElementById('__erConLink'); if(e) e.removeAttribute('id'); }");
                waitForAngular(2500);
            } else {
                page.waitForTimeout(1500);
            }
        }
        try {
            page.waitForFunction("() => window.angular && document.querySelector(\"button[ng-click='IUDRegistration();']\") && document.getElementById('txtFirstName')",
                    null, new Page.WaitForFunctionOptions().setTimeout(30000));
        } catch (Exception ignore) { System.out.println("EmergencyRegistrationConscious.nav: form not ready in time"); }
        waitForAngular(1500);
        return onScreen();
    }

    /** On this screen: the route AND its Save button, so a bounce to the dashboard cannot pass. */
    public boolean onScreen() {
        if (!page.url().toLowerCase().matches(".*#/emergencyregistrationconsious/?$")) return false;
        return Boolean.TRUE.equals(page.evaluate("() => !!document.querySelector(\"button[ng-click='IUDRegistration();']\")"));
    }

    /** Open Emergency Registration (Conscious) from the menu, then wait for the dropdown master-data; falls back to the hash route. */
    @Override
    public boolean open(String baseUrl) {
        if (navigateViaMenu()) {
            reachedVia = "menu";
            for (int i = 0; i < 4; i++) {
                try {
                    page.waitForFunction("() => { const e=[...document.querySelectorAll('select')].find(x=>x.getAttribute('ng-model')==='Registration.GenderID'); return e && e.options.length>1; }",
                            null, new Page.WaitForFunctionOptions().setTimeout(8000));
                    return true;
                } catch (com.microsoft.playwright.PlaywrightException ex) { waitForAngular(1500); }
            }
            return false;
        }
        reachedVia = "route";
        return openRoute(ROUTE);
    }
}
