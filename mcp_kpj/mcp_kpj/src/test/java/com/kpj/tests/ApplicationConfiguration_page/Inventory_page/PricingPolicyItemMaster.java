package com.kpj.tests.ApplicationConfiguration_page.Inventory_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application Configuration &gt; Inventory &gt; <b>Pricing Policy Item Master</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> -> <b>Inventory</b> -> <b>Pricing Policy Item Master</b>.</li>
 *   <li>Select a <b>Pricing Policy</b> - a DIFFERENT one every run (the next in the list; the position is kept between runs). Policies that have
 *       no items are skipped (up to 10 tried) so the table check has data to look at.</li>
 *   <li>Click <b>Search</b> - the policy's items are listed in the table.</li>
 *   <li>Verify the data is displayed in the table (the selected policy's rows, and the same count the server returned).</li>
 * </ol>
 * Read-only: nothing is saved. Pin a policy with {@code -Dppim.policy="NAME"} or a list position with {@code -Dppim.index=N} (1-based).
 */
public class PricingPolicyItemMaster extends DevHisBase {

    public PricingPolicyItemMaster() { super("ApplicationConfig_Inventory_PricingPolicyItemMaster"); }

    public static void main(String[] args) {
        PricingPolicyItemMaster t = new PricingPolicyItemMaster();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    private static Path stateFile() {
        String out = System.getenv("OUTDIR");
        if (out == null || out.isBlank()) out = System.getProperty("user.dir") + "/test-output";
        return Paths.get(out, "state", "pricing-policy-item-master-next.txt");
    }

    /** The 0-based list position to start from this run; the following one is stored for the next run. */
    private static int nextIndex(int count) {
        String pin = System.getProperty("ppim.index");
        if (pin != null && pin.matches("\\d+")) return (Integer.parseInt(pin) - 1 + count) % count;
        int idx = 0;
        Path f = stateFile();
        try { if (Files.exists(f)) idx = Integer.parseInt(Files.readString(f).trim()); } catch (Exception ignore) { }
        idx = ((idx % count) + count) % count;
        try { Files.createDirectories(f.getParent()); Files.writeString(f, String.valueOf((idx + 1) % count)); }
        catch (Exception e) { idx = (int) ((System.currentTimeMillis() / 60000L) % count); }
        return idx;
    }

    @Override
    protected void body() {
        meta("Application Configuration - Inventory - Pricing Policy Item Master", "Application Configuration > Inventory > Pricing Policy Item Master",
                "Select a Pricing Policy (a different one every run), click Search, verify the policy's items are displayed in the table.");

        new LoginPage(page).login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PricingPolicyItemMaster pp =
                new com.kpj.pages.ApplicationConfiguration_page.Inventory_page.PricingPolicyItemMaster(page);

        // 1) Navigate
        boolean onScreen = pp.navigateViaMenu();
        step(page, "Open Pricing Policy Item Master screen", "Click Application Configuration -> Inventory -> Pricing Policy Item Master",
                "The Pricing Policy Item Master screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED - screen not reached"); return; }

        // 2) Pricing Policy + 3) Search: walk on from this run's policy until one has items
        List<String> names = pp.policyNames();
        if (names.isEmpty()) {
            step(page, "Select Pricing Policy", "Open the Pricing Policy dropdown", "The dropdown lists the pricing policies", "The dropdown is empty", "FAIL");
            return;
        }
        String pinned = System.getProperty("ppim.policy");
        int idx = pinned != null && names.contains(pinned) ? names.indexOf(pinned) : nextIndex(names.size());
        String policy = "";
        int skippedEmpty = 0;
        boolean picked = false, searched = false;
        for (int tries = 0; tries < 10; tries++, idx = (idx + 1) % names.size()) {
            policy = names.get(idx);
            String why = pp.selectPolicy(policy);
            picked = why.isEmpty();
            if (!picked) { System.out.println("PricingPolicyItemMaster: could not select \"" + policy + "\": " + why); continue; }
            searched = pp.clickSearch();
            if (!searched) break;
            if (pp.serverRows != 0 || pinned != null) break;   // has items (or the user pinned it): stop here
            skippedEmpty++;
        }
        step(page, "Select Pricing Policy", "Select the Pricing Policy \"" + policy + "\" (policy " + (idx + 1) + " of " + names.size() + "; the next run starts from the next one)",
                "The Pricing Policy dropdown shows the selected policy",
                picked ? "Selected \"" + policy + "\" (policy " + (idx + 1) + " of " + names.size() + ")"
                        + (skippedEmpty > 0 ? "; " + skippedEmpty + " earlier policy(ies) had no items and were skipped" : "")
                       : "Could not select \"" + policy + "\"",
                picked ? "PASS" : "FAIL");
        if (!picked) return;

        step(pp.searchPng != null ? pp.searchPng : page.screenshot(), "Click Search", "Click Search",
                "The items of the selected Pricing Policy are listed in the table",
                searched ? "Search clicked; server: " + pp.searchHttp : "The Search button could not be clicked", searched ? "PASS" : "FAIL");
        if (!searched) return;

        // 4) Verify the table
        List<String> heads = pp.headers();
        String need = "Pricing Policy Service Code|Pricing Policy Name|Pricing Policy Service Name|Group Name|Sub Group Name|Status";
        StringBuilder missing = new StringBuilder();
        for (String h : need.split("\\|")) if (heads.stream().noneMatch(x -> x.equalsIgnoreCase(h))) missing.append(missing.length() > 0 ? ", " : "").append(h);
        step(page, "Verify the table columns", "Read the table header", "Columns: " + need.replace("|", ", ") + ", Edit",
                missing.length() == 0 ? "Columns shown: " + String.join(", ", heads) : "Missing column(s): " + missing + " (shown: " + String.join(", ", heads) + ")",
                missing.length() == 0 ? "PASS" : "FAIL");

        List<List<String>> rows = pp.rows();
        int shown = rows.size();
        int total = pp.totalItems();
        int expect = pp.serverRows;
        boolean serverKnown = expect >= 0;
        boolean dataShown = shown > 0;
        int tableCount = total >= 0 ? total : shown;
        boolean consistent = !serverKnown || tableCount == expect;

        int idxName = -1, idxStatus = -1;
        for (int i = 0; i < heads.size(); i++) {
            if (heads.get(i).equalsIgnoreCase("Pricing Policy Name")) idxName = i;
            if (heads.get(i).equalsIgnoreCase("Status")) idxStatus = i;
        }
        int off = rows.isEmpty() ? 0 : Math.max(0, rows.get(0).size() - heads.size());
        int wrong = 0;
        Map<String, Integer> status = new LinkedHashMap<>();
        for (List<String> r : rows) {
            if (idxName >= 0 && idxName + off < r.size() && !r.get(idxName + off).equalsIgnoreCase(policy)) wrong++;
            if (idxStatus >= 0 && idxStatus + off < r.size()) status.merge(r.get(idxStatus + off).isEmpty() ? "(blank)" : r.get(idxStatus + off), 1, Integer::sum);
        }

        String actual, verdict;
        if (dataShown) {
            actual = tableCount + " item(s) listed in the table for \"" + policy + "\"" + (serverKnown ? " (server returned " + expect + ")" : "")
                    + (total > shown ? "; " + shown + " of them on screen, the rest after scrolling" : "")
                    + "; Status of the visible rows: " + (status.isEmpty() ? "n/a" : status.toString())
                    + (wrong > 0 ? "; " + wrong + " row(s) belong to a different policy" : "; every row is this policy's")
                    + "; first row: " + String.join(" | ", rows.get(0));
            verdict = (wrong == 0 && consistent) ? "PASS" : "FAIL";
            if (!consistent) actual += " - the table does not match the server answer";
        } else if (serverKnown && expect > 0) {
            actual = "The server returned " + expect + " row(s) for \"" + policy + "\" but the table shows none";
            verdict = "FAIL";
        } else {
            actual = "No rows: \"" + policy + "\" has no items" + (serverKnown ? " (server returned 0)" : "")
                    + (pp.emptyMessage().isEmpty() ? "" : "; screen says: " + pp.emptyMessage());
            verdict = "PASS";
        }
        step(pp.searchPng != null ? pp.searchPng : page.screenshot(), "Verify the data is displayed in the table",
                "Check the table lists the selected policy's items (rows, matching policy name, Status) and agrees with the server answer",
                "Data is displayed in the table (or the policy genuinely has no items)", actual, verdict);

        addSummary("Pricing Policy", policy + " (" + (idx + 1) + " of " + names.size() + ")");
        addSummary("Rows in table", String.valueOf(tableCount));
        addSummary("Server rows", serverKnown ? String.valueOf(expect) : "n/a");
        addSummary("Status values", status.isEmpty() ? "n/a" : status.toString());
        addSummary("Result", dataShown ? "Data displayed in the table" : "No data for this policy");
    }
}
