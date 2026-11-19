package com.kpj.tests.AncillaryServices_page.CRM_page;

/**
 * Ancillary Services &gt; CRM &gt; <b>Package</b>.
 *
 * <p>The CRM menu opens the SAME screen ({@code #/Package}) as Application Configuration &gt; Billing &gt; Package, so this test
 * REUSES that script — the same page object and the same steps ({@code Billing_page/PackageFlow}) — and only opens the screen
 * through the CRM menu and writes its own report ({@code AncillaryServices_CRM_Package}).</p>
 *
 * <p>Steps: List (Search, Add) → Checks (Submit empty, Service Name that already has a package, free Service Name, Add Services
 * without a tariff, Submit without services) → Save (add a service, Submit) → Edit + Back → Status (left inactive).</p>
 *
 * <p>&#9888; A run CREATES one package (code {@code PK#####}) and then makes it inactive.</p>
 */
public class Package extends com.kpj.tests.ApplicationConfiguration_page.Billing_page.PackageFlow {

    public Package() { super("AncillaryServices_CRM_Package"); }

    public static void main(String[] args) {
        Package t = new Package();
        try { t.run(); } finally { t.stop(); }
    }

    @Override
    protected String menuPath() { return "Ancillary Services > CRM > Package"; }

    @Override
    protected boolean openScreen(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk) { return pk.navigateViaCrmMenu(); }
}
