package com.kpj.tests.ApplicationConfiguration_page.Location_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.List;
import java.util.Map;

// The page object is also named HospitalLocation — referenced by its fully-qualified name.

/**
 * Application Configuration &gt; Location &gt; <b>Hospital Location</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Location</b> → <b>Hospital Location</b>.</li>
 *   <li>Verify the Location column in the grid reads <b>KPJ DAMANSARA SPECIALIST HOSPITAL</b>.</li>
 * </ol>
 */
public class HospitalLocation extends DevHisBase {

    /** Every environment (devhis/dsh/klg/ks) is scoped to its own hospital location, so this is NOT one constant
     *  across environments — override with {@code -Ddevhis.expectedLocation=...} when running against dsh/klg/ks.
     *  devhis's own location (verified live 2026-08-24) is the default. */
    private static final String EXPECTED_LOCATION = System.getProperty("devhis.expectedLocation", "KPJ DAMANSARA SPECIALIST HOSPITAL");

    public HospitalLocation() { super("ApplicationConfig_Location_HospitalLocation"); }

    public static void main(String[] args) {
        HospitalLocation t = new HospitalLocation();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Hospital Location", "Application Configuration > Location > Hospital Location",
                "Open Hospital Location and verify the Location column reads " + EXPECTED_LOCATION + ".");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Location_page.HospitalLocation hl =
                new com.kpj.pages.ApplicationConfiguration_page.Location_page.HospitalLocation(page);

        boolean onScreen = hl.navigateViaMenu();
        step(page, "Open Hospital Location screen", "Application Configuration -> Location -> Hospital Location",
                "The Hospital Location screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        List<Map<String, Object>> rows = hl.readRows();
        boolean found = hl.hasLocation(EXPECTED_LOCATION);
        StringBuilder actual = new StringBuilder();
        actual.append(rows.size()).append(" row(s): ");
        for (Map<String, Object> row : rows) actual.append("[Code=").append(row.get("Code")).append(", Location=").append(row.get("Name")).append(", Status=").append(row.get("Status")).append("] ");
        step(page, "Verify the Location in the table", "Read the Location column of every grid row",
                "The Location reads \"" + EXPECTED_LOCATION + "\"", actual.toString().trim(), found ? "PASS" : "FAIL");

        addSummary("Expected Location", EXPECTED_LOCATION);
        addSummary("Row count", String.valueOf(rows.size()));
        addSummary("Result", found ? "Verified — Location = " + EXPECTED_LOCATION : "NOT verified — see step 3");
    }
}
