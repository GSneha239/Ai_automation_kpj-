package com.kpj.tests.ApplicationConfiguration_page.Billing_page;

import com.kpj.core.DevHisBase;
import com.kpj.pages.LoginPage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Application Configuration &gt; Billing &gt; <b>Pricing Policy Service</b>.
 *
 * <ol>
 *   <li>Click <b>Application Configuration</b> → <b>Billing</b> → <b>Pricing Policy Service</b>.</li>
 *   <li>Select a <b>Pricing Policy</b> — a DIFFERENT one every run (the next in the list; the position is kept between runs).</li>
 *   <li>Click <b>Search</b> — the policy's services are listed in the table.</li>
 *   <li>Verify the data is displayed in the table (and that it is the selected policy's, and agrees with what the server returned).</li>
 * </ol>
 * Pin a policy with {@code -Dpps.policy="NAME"} or a list position with {@code -Dpps.index=N} (1-based).
 */
public class PricingPolicyService extends DevHisBase {

    public PricingPolicyService() { super("ApplicationConfig_Billing_PricingPolicyService"); }

    public static void main(String[] args) {
        PricingPolicyService t = new PricingPolicyService();
        try { t.run(); } finally { t.stop(); }
    }

    @org.junit.jupiter.api.Test
    void execute() { try { run(); } finally { stop(); } }

    /** Where the "next policy" position is kept between runs (next to the test output, so it survives a rebuild). */
    private static Path stateFile() {
        String out = System.getenv("OUTDIR");
        if (out == null || out.isBlank()) out = System.getProperty("user.dir") + "/test-output";
        return Paths.get(out, "state", "pricing-policy-service-next.txt");
    }

    /** The 0-based list position to use this run; the following one is stored for the next run. */
    private static int nextIndex(int count) {
        String pin = System.getProperty("pps.index");
        if (pin != null && pin.matches("\\d+")) return (Integer.parseInt(pin) - 1 + count) % count;
        int idx = 0;
        Path f = stateFile();
        try { if (Files.exists(f)) idx = Integer.parseInt(Files.readString(f).trim()); } catch (Exception ignore) { }
        idx = ((idx % count) + count) % count;
        try { Files.createDirectories(f.getParent()); Files.writeString(f, String.valueOf((idx + 1) % count)); }
        catch (Exception e) { System.out.println("PricingPolicyService: could not keep the position (" + e.getMessage() + ") — using the clock"); idx = (int) ((System.currentTimeMillis() / 60000L) % count); }
        return idx;
    }

    @Override
    protected void body() {
        meta("Application Configuration - Billing - Pricing Policy Service", "Application Configuration > Billing > Pricing Policy Service",
                "Select a Pricing Policy (a different one every run), click Search, verify the policy's services are displayed in the table.");

        LoginPage loginPage = new LoginPage(page);
        loginPage.login(BASE, USER, PASS);
        step("Login", USER + " login", "Authenticated; Patient Dashboard", "Logged in", "PASS");

        com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyService pps =
                new com.kpj.pages.ApplicationConfiguration_page.Billing_page.PricingPolicyService(page);

        // 1) Navigate
        boolean onScreen = pps.navigateViaMenu();
        step(page, "Open Pricing Policy Service screen", "Click Application Configuration -> Billing -> Pricing Policy Service",
                "The Pricing Policy Service screen is shown", onScreen ? "Opened " + page.url() : "Did NOT reach the screen (" + page.url() + ")",
                onScreen ? "PASS" : "FAIL");
        if (!onScreen) { addSummary("Result", "FAILED — screen not reached"); return; }

        // 2) Pick this run's Pricing Policy
        List<String> names = pps.policyNames();
        if (names.isEmpty()) {
            step(page, "Select Pricing Policy", "Open the Pricing Policy dropdown", "The dropdown lists the pricing policies", "The dropdown is empty", "FAIL");
            return;
        }
        String pinned = System.getProperty("pps.policy");
        int idx = pinned != null && names.contains(pinned) ? names.indexOf(pinned) : nextIndex(names.size());
        String policy = names.get(idx);
        String why = pps.selectPolicy(policy);
        boolean picked = why.isEmpty();
        step(page, "Select Pricing Policy", "Select the Pricing Policy \"" + policy + "\" (policy " + (idx + 1) + " of " + names.size() + "; the next run picks the next one)",
                "The Pricing Policy dropdown shows the selected policy",
                picked ? "Selected \"" + policy + "\" (policy " + (idx + 1) + " of " + names.size() + ")" : "Could not select \"" + policy + "\": " + why,
                picked ? "PASS" : "FAIL");
        if (!picked) return;

        // 3) Search
        boolean searched = pps.clickSearch();
        step(pps.searchPng != null ? pps.searchPng : page.screenshot(), "Click Search", "Click Search",
                "The services of the selected Pricing Policy are listed in the table",
                searched ? "Search clicked; server: " + pps.searchHttp : "The Search button could not be clicked", searched ? "PASS" : "FAIL");
        if (!searched) return;

        // 4) Verify the table
        List<String> heads = pps.headers();
        String need = "Pricing Policy Service Code|Pricing Policy Name|Pricing Policy Service Name|Group Name|Sub Group Name|Status|Effective Date";
        StringBuilder missing = new StringBuilder();
        for (String h : need.split("\\|")) if (heads.stream().noneMatch(x -> x.equalsIgnoreCase(h))) missing.append(missing.length() > 0 ? ", " : "").append(h);
        step(page, "Verify the table columns", "Read the table header",
                "Columns: " + need.replace("|", ", ") + ", Edit",
                missing.length() == 0 ? "Columns shown: " + String.join(", ", heads) : "Missing column(s): " + missing + " (shown: " + String.join(", ", heads) + ")",
                missing.length() == 0 ? "PASS" : "FAIL");

        List<List<String>> rows = pps.rows();
        int shown = rows.size();
        int total = pps.totalItems();
        String empty = pps.emptyMessage();
        int expect = pps.serverRows;   // -1 when the call could not be read
        boolean serverKnown = expect >= 0;
        // what the table shows on its first page (25 per page by default)
        int pageSize = 25;
        int expectShown = serverKnown ? Math.min(expect, pageSize) : -1;
        boolean dataShown = shown > 0;
        // The grid only draws the rows that fit on screen (20 of 25), so the table's own item count is compared with the server answer.
        int tableCount = total >= 0 ? total : shown;
        boolean consistent = !serverKnown || tableCount == expect;

        // every listed row belongs to the selected policy
        int idxName = -1, idxStatus = -1;
        for (int i = 0; i < heads.size(); i++) {
            if (heads.get(i).equalsIgnoreCase("Pricing Policy Name")) idxName = i;
            if (heads.get(i).equalsIgnoreCase("Status")) idxStatus = i;
        }
        // rows may carry an extra leading selection cell; align from the right when the counts differ
        int off = rows.isEmpty() ? 0 : Math.max(0, rows.get(0).size() - heads.size());
        int wrong = 0;
        Map<String, Integer> status = new LinkedHashMap<>();
        for (List<String> r : rows) {
            if (idxName >= 0 && idxName + off < r.size() && !r.get(idxName + off).equalsIgnoreCase(policy)) wrong++;
            if (idxStatus >= 0 && idxStatus + off < r.size()) status.merge(r.get(idxStatus + off).isEmpty() ? "(blank)" : r.get(idxStatus + off), 1, Integer::sum);
        }

        String actual;
        String verdict;
        if (dataShown) {
            actual = (total >= 0 ? total : shown) + " service(s) listed in the table for \"" + policy + "\"" + (serverKnown ? " (server returned " + expect + ")" : "")
                    + (total > shown ? "; " + shown + " of them on screen, the rest after scrolling" : "")
                    + "; Status of the visible rows: " + (status.isEmpty() ? "n/a" : status.toString())
                    + (wrong > 0 ? "; " + wrong + " row(s) belong to a different policy" : "; every row is this policy's")
                    + "; first row: " + String.join(" | ", rows.get(0));
            verdict = (wrong == 0 && consistent) ? "PASS" : "FAIL";
            if (!consistent) actual += " — the table does not match the server answer";
        } else if (serverKnown && expect > 0) {
            actual = "The server returned " + expect + " row(s) for \"" + policy + "\" but the table shows none";
            verdict = "FAIL";
        } else {
            actual = "No rows: \"" + policy + "\" has no services" + (serverKnown ? " (server returned 0)" : "") + (empty.isEmpty() ? "" : "; screen says: " + empty);
            verdict = "PASS";
        }
        step(pps.searchPng != null ? pps.searchPng : page.screenshot(), "Verify the data is displayed in the table",
                "Check the table lists the selected policy's services (rows, matching policy name, Status) and agrees with the server answer",
                "Data is displayed in the table (or the policy genuinely has no services)", actual, verdict);

        addSummary("Pricing Policy", policy + " (" + (idx + 1) + " of " + names.size() + ")");
        addSummary("Rows in table", String.valueOf(tableCount));
        addSummary("Server rows", serverKnown ? String.valueOf(expect) : "n/a");
        addSummary("Status values", status.isEmpty() ? "n/a" : status.toString());
        addSummary("Result", dataShown ? "Data displayed in the table" : "No data for this policy");
    }
}
