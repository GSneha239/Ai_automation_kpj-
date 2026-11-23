package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

// The page object is also named Package — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Billing &gt; <b>Package</b>.
 *
 * <p>The steps are in {@link PackageFlow} (shared with Ancillary Services &gt; CRM &gt; Package, which opens the same screen):
 * List (Search, Add) → Checks (Submit empty, Service Name that already has a package, free Service Name, Add Services without a
 * tariff, Submit without services) → Save (add a service, Submit) → Edit + Back → Status (left inactive).</p>
 *
 * <p>&#9888; A run CREATES one package (code {@code PK#####}) and then makes it inactive.</p>
 */
public class Package extends PackageFlow {

    public Package() { super("ApplicationConfig_Billing_Package"); }

    public static void main(String[] args) {
        Package t = new Package();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String menuPath() { return "Application Configuration > Billing > Package"; }

    @Override
    protected boolean openScreen(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk) { return pk.navigateViaMenu(); }
}
