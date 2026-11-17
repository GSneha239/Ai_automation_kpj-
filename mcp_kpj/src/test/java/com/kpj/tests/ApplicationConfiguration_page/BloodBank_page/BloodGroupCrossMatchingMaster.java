package com.kpj.tests.ApplicationConfiguration_page.BloodBank_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.util.ArrayList;
import java.util.List;

/**
 * Application Configuration &gt; Blood Bank &gt; <b>Blood Group Cross Matching Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Blood Bank</b> → <b>Blood Group Cross Matching Master</b>.</li>
 *   <li>Verify the data is displayed in the table: the donor blood groups ("Doner : A Positive (51)" ...) with their Applicability tick,
 *       and, once a donor group is opened, the recipient blood groups under it.</li>
 * </ol>
 * Read-only: nothing is saved.
 */
public class BloodGroupCrossMatchingMaster extends DevHisBase {

    public BloodGroupCrossMatchingMaster() { super("ApplicationConfig_BloodBank_BloodGroupCrossMatchingMaster"); }

    public static void main(String[] args) {
        BloodGroupCrossMatchingMaster t = new BloodGroupCrossMatchingMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    @Override
    protected void body() {
        meta("Application Configuration - Blood Bank - Blood Group Cross Matching Master", "Application Configuration > Blood Bank > Blood Group Cross Matching Master",
                "Open the screen and verify the donor / recipient combinations are displayed in the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodGroupCrossMatchingMaster bg =
                new com.kpj.pages.ApplicationConfiguration_page.BloodBank_page.BloodGroupCrossMatchingMaster(page);

        // 1) Navigate
        boolean onScreen = bg.navigateViaMenu();
        step(page, "Open Blood Group Cross Matching Master screen", "Click Application Configuration -> Blood Bank -> Blood Group Cross Matching Master",
                "The Blood Group Cross Matching Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED - screen not reached"); return; }

        // 2) Table columns
        List<String> heads = bg.headers();
        String[] need = {"Donor", "Applicability", "Recipient"};
        StringBuilder missing = new StringBuilder();
        for (String h : need) if (heads.stream().noneMatch(x -> x.equalsIgnoreCase(h))) missing.append(missing.length() > 0 ? ", " : "").append(h);
        step(page, "Verify the table columns", "Read the table header", "Columns: Donor, Applicability, Recipient",
                missing.length() == 0 ? "Columns shown: " + String.join(", ", heads) : "Missing column(s): " + missing + " (shown: " + String.join(", ", heads) + ")",
                missing.length() == 0 ? "PASS" : "FAIL");

        // 3) Data displayed: the donor groups
        List<List<String>> rows = bg.rows();
        int total = bg.totalItems();
        List<String> groups = new ArrayList<>();
        for (List<String> r : rows) if (!r.isEmpty() && r.get(0).toLowerCase().startsWith("doner")) groups.add(r.get(0));
        boolean shown = !rows.isEmpty();
        boolean groupsShown = !groups.isEmpty();
        bg.shootTable();
        step(bg.tablePng != null ? bg.tablePng : page.screenshot(), "Verify the data is displayed in the table",
                "Check the table lists the donor blood groups with their Applicability", "Rows are displayed in the table",
                shown ? (total >= 0 ? total : rows.size()) + " row(s) in the table (" + rows.size() + " on screen); donor groups on screen: "
                        + String.join(" ; ", groups.subList(0, Math.min(8, groups.size()))) + (groups.size() > 8 ? " ..." : "")
                      : "The table is empty - no data is displayed",
                shown && groupsShown ? "PASS" : "FAIL");

        // 4) Open the first donor group: its recipient blood groups are listed
        boolean opened = shown && bg.expandFirstGroup();
        List<List<String>> after = bg.rows();
        List<String> recipients = new ArrayList<>();
        for (List<String> r : after) {
            String last = r.isEmpty() ? "" : r.get(r.size() - 1);
            if (!r.isEmpty() && !r.get(0).toLowerCase().startsWith("doner") && (!last.isEmpty() || r.get(0).length() > 0)) recipients.add(String.join(" / ", r));
        }
        bg.shootTable();
        step(bg.tablePng != null ? bg.tablePng : page.screenshot(), "Expand the donor groups and verify the recipient rows",
                "Click the expand control of the table", "The recipient blood groups under the donor groups are displayed",
                opened && !recipients.isEmpty() ? recipients.size() + " recipient row(s) shown after expanding (Applicability / Recipient): "
                        + String.join(" ; ", recipients.subList(0, Math.min(4, recipients.size())))
                      : "The donor group did not open / no recipient rows appeared (rows on screen: " + after.size() + ")",
                opened && !recipients.isEmpty() ? "PASS" : "FAIL");

        addSummary("Rows in table", shown ? String.valueOf(total >= 0 ? total : rows.size()) : "0");
        addSummary("Donor groups on screen", String.valueOf(groups.size()));
        addSummary("Recipient rows after opening a group", String.valueOf(recipients.size()));
        addSummary("Result", shown && groupsShown ? "Data displayed in the table" : "Table empty");
    }
}
