package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

/**
 * The <b>Package</b> screen test flow ({@code #/Package}) shared by every menu that opens it — Application Configuration &gt;
 * Billing &gt; Package ({@link Package}) and Ancillary Services &gt; CRM &gt; Package. Each menu has its own small test class (its own
 * report); the steps live here once. Abstract, so JUnit runs only the concrete tests.
 *
 * <ol>
 *   <li><b>List</b> — open the screen, click Search → the packages are listed.</li>
 *   <li><b>Add</b> — the Add Package form opens.</li>
 *   <li><b>Checks</b> — Submit empty → "Please enter Code"; pick a Service Name that already has a package → "Package Already
 *       Present" (expected); pick a free Service Name → its Applicable Tariffs load; Add Services with no tariff ticked →
 *       refused; Submit with no services → refused.</li>
 *   <li><b>Save</b> — tick a tariff, Add Services (popup: search by Name, tick a service, Add), Submit → "Package added
 *       successfully", back on the list and the package is listed.</li>
 *   <li><b>Edit</b> — the new package loads into the form; Back returns to the list.</li>
 *   <li><b>Status</b> — untick its Status → "Status updated successfully" (the test package is left inactive).</li>
 * </ol>
 *
 * <p>&#9888; A run CREATES one package (code {@code PK#####}) on a Service Name that has no package yet, and then makes it
 * inactive. Each run uses up one free Service Name.</p>
 */
public abstract class PackageFlow extends DevHisBase {

    /** Popup search text for the service added to the package. */
    private static final String SERVICE_SEARCH = "consult";
    /** Preferred Applicable Tariff (used by the existing packages). */
    private static final String TARIFF = "SELFPAY";

    protected PackageFlow(String reportId) { super(reportId); }

    /** Menu path shown in the report, e.g. "Application Configuration > Billing > Package". */
    protected abstract String menuPath();

    /** Open the Package list through this test's menu. */
    protected abstract boolean openScreen(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk);


    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta(menuPath().replace(" > ", " - "), menuPath(),
                "&#9888; Lists packages, checks the form's mandatory / duplicate checks, adds a package (Service Name, tariff, one "
                        + "service), then Edit, Back and leaves the package inactive via its Status tick.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package(page);

        boolean on = openScreen(pk);
        step(page, "Open Package screen", menuPath().replace(">", "->"), "The Package list screen is shown",
                on ? "Opened " + pk.currentScreen() : "WRONG PAGE - the app opened: " + pk.currentScreen(), on ? "PASS" : "FAIL");
        if (!on) { addSummary("Result", "FAILED - screen not reached"); return; }

        if (!sectionList(pk)) return;
        String service = sectionChecks(pk);
        if (service == null) return;
        String code = sectionSave(pk, service);
        if (code == null) return;
        sectionEdit(pk, code);
        sectionStatus(pk, code);

        addSummary("Route", "#/Package -> #/add-Package");
    }

    // ===== Section: List ================================================
    private boolean sectionList(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk) {
        int rows = pk.searchList();
        step(page, "List · Search", "Click Search (no filter)", "The packages are listed",
                rows + " package(s) listed", rows > 0 ? "PASS" : "FAIL");

        boolean added = pk.clickAdd();
        step(page, "List · Add", "Click Add", "The Add Package form opens",
                added ? "Opened " + page.url() + " (form values: " + String.join(" | ", pk.formValues()) + ")" : "Did NOT open (" + page.url() + ")",
                added ? "PASS" : "FAIL");
        return added;
    }

    // ===== Section: Checks ==============================================
    private String sectionChecks(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk) {
        String empty = pk.submit();
        step(page, "Checks · Submit empty", "Click Submit with nothing entered", "Refused: \"Please enter Code\"",
                empty.isEmpty() ? "No message" : empty, empty.toLowerCase().contains("code") ? "PASS" : "FAIL");

        String taken = pk.scopeJson("fnIUDPackage", "(s.servicename||[])[0].text").replaceAll("^\"|\"$", "");
        java.util.List<String> free = pk.freeServiceNames(3);
        if (!free.isEmpty() && taken.equals(free.get(0))) taken = "";
        if (!taken.isEmpty()) {
            String said = pk.chooseServiceName(taken);
            boolean present = said.toLowerCase().contains("already present");
            step(page, "Checks · Service Name that already has a package", "Pick Service Name \"" + taken + "\"",
                    "\"Package Already Present\" (expected - one package per service)", said.isEmpty() ? "No message" : said, present ? "PASS" : "FAIL");
        }

        if (free.isEmpty()) {
            step(page, "Checks · Free Service Name", "Look for a Service Name with no package", "At least one is free",
                    "Every Service Name already has a package - nothing can be added", "FAIL");
            addSummary("Result", "FAILED - no free Service Name");
            return null;
        }
        String service = null; String said = "";
        for (String f : free) {
            said = pk.chooseServiceName(f);
            if (pk.tickableTariffs() > 0) { service = f; break; }
        }
        java.util.List<String> tariffs = pk.tariffs();
        step(page, "Checks · Free Service Name loads its tariffs", "Pick Service Name \"" + (service == null ? free.get(0) : service) + "\"",
                "No warning; the Applicable Tariffs are listed and can be ticked",
                (said.isEmpty() ? "" : said + " | ") + tariffs.size() + " tariff(s), " + pk.tickableTariffs() + " can be ticked",
                service != null && !said.toLowerCase().contains("already") ? "PASS" : "FAIL");
        if (service == null) { addSummary("Result", "FAILED - no Service Name with a tariff that can be ticked"); return null; }

        String noTariff = pk.openAddServices();
        pk.closePopup();
        step(page, "Checks · Add Services with no tariff ticked", "Services tab -> Add Services, before ticking a tariff",
                "Refused: \"Please select tariff\"", noTariff.isEmpty() ? "No message - the popup opened" : noTariff,
                noTariff.toLowerCase().contains("tariff") ? "PASS" : "FAIL");

        String stamp = String.format("%05d", Math.abs(System.nanoTime() % 100000));
        pk.fillCodeAndDescription("PK" + stamp, "Auto Package " + stamp);
        String tariff = pk.tickTariff(TARIFF);
        addSummary("Tariff", tariff);
        String noSvc = pk.submit();
        step(page, "Checks · Submit with no services", "Code PK" + stamp + ", Description, Service Name and tariff \"" + tariff + "\" set; no services; Submit",
                "Refused: \"Please add services\"", noSvc.isEmpty() ? "No message" : noSvc,
                noSvc.toLowerCase().contains("service") ? "PASS" : "FAIL");
        return service;
    }

    // ===== Section: Save ================================================
    private String sectionSave(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk, String service) {
        String opened = pk.openAddServices();
        int found = pk.popupOpen() ? pk.searchServices(SERVICE_SEARCH) : 0;
        String ticked = found > 0 ? pk.tickFirstService() : "";
        String tickMsg = pk.messages();
        String addMsg = ticked.isEmpty() ? "" : pk.addTickedServices();
        pk.closePopup();
        int added = pk.addedServiceCount();
        step(page, "Save · Add a service", "Services tab -> Add Services; Name \"" + SERVICE_SEARCH + "\" -> Search; tick a service; Add",
                "The service is added to the package with its class rates",
                (opened.isEmpty() ? "" : opened + " | ") + found + " found | ticked: " + (ticked.isEmpty() ? "none" : ticked)
                        + (tickMsg.isEmpty() ? "" : " (" + tickMsg + ")") + (addMsg.isEmpty() ? "" : " | " + addMsg) + " | added: " + pk.addedServices(),
                added > 0 ? "PASS" : "FAIL");
        if (added <= 0) { addSummary("Result", "FAILED - no service added"); return null; }

        String code = pk.lastCode, desc = pk.lastDescription;
        String[] f = pk.formValues();
        String said = pk.submit();
        boolean saved = said.toLowerCase().matches(".*(added|saved|success).*");
        boolean onList = saved && pk.onList();
        if (onList) pk.searchList();
        String row = saved ? pk.listRecord(code) : null;
        step(page, "Save · Submit", "Code " + code + ", Description \"" + desc + "\", Service Name \"" + service + "\", Validity " + f[2] + " - " + f[3] + "; Submit",
                "\"Package added successfully\", back on the list and the package is listed",
                (said.isEmpty() ? "No message" : said) + " | on list=" + onList + " | listed: " + (row == null ? "NO" : row),
                saved && row != null ? "PASS" : "FAIL");
        addSummary("Package", code + " / " + desc + " / " + service);
        if (!saved) { addSummary("Result", "FAILED - not saved: " + said); return null; }
        if (!onList) pk.back();
        return code;
    }

    // ===== Section: Edit ================================================
    private void sectionEdit(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk, String code) {
        String[] loaded = pk.editRecord(code);
        boolean ok = code.equalsIgnoreCase(loaded[0]) && !loaded[1].isEmpty();
        String msg = pk.messages();
        step(page, "Edit · Load the package", "Click Edit on " + code, "The package opens in the form with its Code and Description",
                "Code '" + loaded[0] + "' | Description '" + loaded[1] + "' | Validity " + loaded[2] + " - " + loaded[3] + " | services: " + pk.addedServiceCount()
                        + (msg.isEmpty() ? "" : " | message: " + msg),
                ok ? "PASS" : "FAIL");

        boolean back = pk.back();
        step(page, "Edit · Back", "Click Back", "Returns to the Package list without saving",
                back ? "Back on " + page.url() : "Still on " + page.url(), back ? "PASS" : "FAIL");
        if (!back) openScreen(pk);
    }

    // ===== Section: Status ==============================================
    private void sectionStatus(com.kpj.pages.ApplicationConfiguration_page.Billing_page.Package pk, String code) {
        if (pk.listCount() <= 0) pk.searchList();
        String st = pk.toggleStatus(code);
        String after = pk.listRecord(code);
        boolean inactive = st.toLowerCase().contains("status updated") && after != null && after.endsWith("inactive");
        step(page, "Status · Make inactive", "Untick the Status of " + code + " in the list",
                "\"Status updated successfully\" and the package shows inactive",
                (st.isEmpty() ? "No message" : st) + " | now: " + after, inactive ? "PASS" : "FAIL");
    }
}
